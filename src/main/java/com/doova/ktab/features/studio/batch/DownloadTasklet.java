package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.dto.StudioChapterSummary;
import com.doova.ktab.features.studio.client.dto.StudioSnapshotSummary;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.item.ExecutionContext;
import org.springframework.batch.repeat.RepeatStatus;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.*;

/**
 * Phase 4 {@code downloadStep}: for each converted chapter, finds its latest snapshot and
 * streams the MP3 audio directly from ElevenLabs to R2 via S3 multipart upload without
 * buffering to local disk. sha256 is computed incrementally over the stream.
 *
 * <p>Key guarantees (docs/ocr_engine_v3.md, Phase 4.3):
 * <ul>
 *   <li>8 MB part buffer — balances memory against multipart overhead.</li>
 *   <li>Multipart upload is aborted in a {@code finally} block so orphaned parts do not accrue.</li>
 *   <li>sha256 and size are stored in the execution context for {@link FinalizeTasklet}.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
public class DownloadTasklet implements Tasklet {

    private static final int PART_SIZE_BYTES = 8 * 1024 * 1024; // 8 MB
    private static final String AUDIO_KEY_FMT = "audio/%d/chapters/ch-%04d.mp3";

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final S3Client s3Client;
    private final String bucketName;
    private final StudioProperties props;
    private final MeterRegistry meterRegistry;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (props.getAudiobook().isDryRun()) {
            log.info("studio.download.dryRun bookId={} — skipping", bookId);
            return RepeatStatus.FINISHED;
        }

        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));
        String extId = project.getExternalProjectId();

        List<StudioChapter> chapters =
                chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());

        // Store download results in execution context for FinalizeTasklet
        ExecutionContext ctx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();

        for (StudioChapter chapter : chapters) {
            if (chapter.getDeletedAt() != null) continue;

            List<StudioSnapshotSummary> snapshots =
                    client.listSnapshots(extId, chapter.getExternalChapterId());

            StudioSnapshotSummary snapshot = snapshots.stream()
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "No snapshot for chapter " + chapter.getExternalChapterId()));

            int sortOrder = chapter.getOrderIndex() + 1; // 1-based
            String r2Key = String.format(AUDIO_KEY_FMT, bookId, sortOrder);

            DownloadResult result = streamToR2(extId, chapter.getExternalChapterId(),
                    snapshot.snapshotId(), r2Key);

            // Store per-chapter metadata for FinalizeTasklet
            ctx.put("chapter." + chapter.getId() + ".r2Key", r2Key);
            ctx.put("chapter." + chapter.getId() + ".sha256", result.sha256);
            ctx.put("chapter." + chapter.getId() + ".sizeBytes", result.sizeBytes);
            ctx.put("chapter." + chapter.getId() + ".durationMs", result.durationMs);
            ctx.put("chapter." + chapter.getId() + ".sortOrder", sortOrder);

            meterRegistry.counter("studio.chars_converted").increment(result.sizeBytes);
            log.info("studio.download.done chapterId={} r2Key={} sizeBytes={}",
                    chapter.getId(), r2Key, result.sizeBytes);
        }

        return RepeatStatus.FINISHED;
    }

    private DownloadResult streamToR2(
            String extProjectId,
            String extChapterId,
            String snapshotId,
            String r2Key
    ) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        long sizeBytes = 0;

        String uploadId = null;
        List<CompletedPart> completedParts = new ArrayList<>();

        try (InputStream raw = client.streamAudio(extProjectId, extChapterId, snapshotId);
             DigestInputStream dis = new DigestInputStream(raw, digest)) {

            CreateMultipartUploadResponse createResp = s3Client.createMultipartUpload(
                    CreateMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(r2Key)
                            .contentType("audio/mpeg")
                            .build());
            uploadId = createResp.uploadId();

            byte[] buffer = new byte[PART_SIZE_BYTES];
            int partNumber = 1;
            int bytesRead;
            int partLen = 0;

            while ((bytesRead = dis.read(buffer, partLen, buffer.length - partLen)) != -1) {
                partLen += bytesRead;
                sizeBytes += bytesRead;

                if (partLen == PART_SIZE_BYTES) {
                    completedParts.add(uploadPart(uploadId, r2Key, partNumber++, buffer, partLen));
                    partLen = 0;
                }
            }

            // Upload final (possibly smaller) part
            if (partLen > 0) {
                completedParts.add(uploadPart(uploadId, r2Key, partNumber, buffer, partLen));
            }

            s3Client.completeMultipartUpload(CompleteMultipartUploadRequest.builder()
                    .bucket(bucketName)
                    .key(r2Key)
                    .uploadId(uploadId)
                    .multipartUpload(CompletedMultipartUpload.builder()
                            .parts(completedParts)
                            .build())
                    .build());

            uploadId = null; // mark as completed so finally block skips abort

        } finally {
            if (uploadId != null) {
                // Abort orphaned upload — prevents R2 storage from accumulating incomplete parts
                try {
                    s3Client.abortMultipartUpload(AbortMultipartUploadRequest.builder()
                            .bucket(bucketName)
                            .key(r2Key)
                            .uploadId(uploadId)
                            .build());
                } catch (Exception ex) {
                    log.warn("studio.download.abortFailed r2Key={}: {}", r2Key, ex.getMessage());
                }
            }
        }

        String sha256 = HexFormat.of().formatHex(digest.digest());
        // Duration estimation: MP3 at 128 kbps ≈ 16 000 bytes/s; placeholder until metadata is parsed
        long durationMs = (sizeBytes * 1000L) / 16_000L;

        return new DownloadResult(sha256, sizeBytes, durationMs);
    }

    private CompletedPart uploadPart(
            String uploadId, String key, int partNumber, byte[] data, int length
    ) {
        UploadPartResponse resp = s3Client.uploadPart(
                UploadPartRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .uploadId(uploadId)
                        .partNumber(partNumber)
                        .contentLength((long) length)
                        .build(),
                RequestBody.fromBytes(Arrays.copyOf(data, length)));
        return CompletedPart.builder().partNumber(partNumber).eTag(resp.eTag()).build();
    }

    private record DownloadResult(String sha256, long sizeBytes, long durationMs) {}
}

