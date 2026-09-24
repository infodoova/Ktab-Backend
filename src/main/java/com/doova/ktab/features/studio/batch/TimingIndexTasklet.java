package com.doova.ktab.features.studio.batch;

import com.doova.ktab.features.studio.client.ElevenLabsStudioClient;
import com.doova.ktab.features.studio.client.dto.CharacterTiming;
import com.doova.ktab.features.studio.client.dto.StudioCharacterAlignment;
import com.doova.ktab.features.studio.client.dto.StudioSnapshotSummary;
import com.doova.ktab.features.studio.config.StudioProperties;
import com.doova.ktab.features.studio.model.StudioChapter;
import com.doova.ktab.features.studio.model.StudioProject;
import com.doova.ktab.features.studio.repository.StudioChapterRepository;
import com.doova.ktab.features.studio.repository.StudioProjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;
import org.springframework.batch.item.ExecutionContext;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.zip.GZIPOutputStream;

/**
 * Phase 4 {@code timingIndexStep}: fetches character-level alignment data for each chapter
 * snapshot and writes a compact columnar timing JSON to R2 for use by the reader app.
 *
 * <p>The timing index format (docs/ocr_engine_v3.md, Phase 3.4):
 * <pre>{@code
 * { "v": 1, "chapterId": "...", "chars": "...", "startMs": [0, 80, ...], "endMs": [80, 140, ...] }
 * }</pre>
 *
 * <p>Stored columnar and gzipped at {@code audio/{bookId}/timings/ch-{sortOrder:04d}.json.gz}.
 * Parallel {@code int[]} arrays instead of an array of objects cuts payload size ~5x;
 * gzip compression another ~4x.
 */
@Slf4j
@RequiredArgsConstructor
public class TimingIndexTasklet implements Tasklet {

    private static final String TIMING_KEY_FMT = "audio/%d/timings/ch-%04d.json.gz";

    private final Long bookId;
    private final ElevenLabsStudioClient client;
    private final StudioProjectRepository projectRepository;
    private final StudioChapterRepository chapterRepository;
    private final S3Client s3Client;
    private final String bucketName;
    private final ObjectMapper objectMapper;
    private final StudioProperties props;

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) throws Exception {
        if (props.getAudiobook().isDryRun()) {
            log.info("studio.timing.dryRun bookId={} — skipping", bookId);
            return RepeatStatus.FINISHED;
        }

        StudioProject project = projectRepository.findLiveByBookId(bookId)
                .orElseThrow(() -> new IllegalStateException(
                        "No live StudioProject found for bookId=" + bookId));
        String extId = project.getExternalProjectId();

        List<StudioChapter> chapters =
                chapterRepository.findByProject_IdOrderByOrderIndexAsc(project.getId());

        ExecutionContext ctx = chunkContext.getStepContext().getStepExecution()
                .getJobExecution().getExecutionContext();

        for (StudioChapter chapter : chapters) {
            if (chapter.getDeletedAt() != null) continue;

            List<StudioSnapshotSummary> snapshots =
                    client.listSnapshots(extId, chapter.getExternalChapterId());
            if (snapshots.isEmpty()) {
                log.warn("studio.timing.noSnapshot chapterId={}", chapter.getId());
                continue;
            }

            String snapshotId = snapshots.get(0).snapshotId();
            StudioCharacterAlignment alignment =
                    client.getAlignment(extId, chapter.getExternalChapterId(), snapshotId);

            int sortOrder = chapter.getOrderIndex() + 1;
            String r2Key = String.format(TIMING_KEY_FMT, bookId, sortOrder);

            // Construct columnar format
            StringBuilder charsBuilder = new StringBuilder();
            List<String> charsList = alignment.characters() != null ? alignment.characters() : List.of();
            List<Double> startSecs = alignment.startTimesSeconds() != null ? alignment.startTimesSeconds() : List.of();
            List<Double> endSecs = alignment.endTimesSeconds() != null ? alignment.endTimesSeconds() : List.of();

            int count = Math.min(charsList.size(), Math.min(startSecs.size(), endSecs.size()));
            int[] startMs = new int[count];
            int[] endMs = new int[count];

            for (int i = 0; i < count; i++) {
                charsBuilder.append(charsList.get(i) != null ? charsList.get(i) : "");
                startMs[i] = (int) Math.round(startSecs.get(i) * 1000.0);
                endMs[i] = (int) Math.round(endSecs.get(i) * 1000.0);
            }

            ColumnarTimingPayload payload = new ColumnarTimingPayload(
                    1,
                    chapter.getExternalChapterId(),
                    charsBuilder.toString(),
                    startMs,
                    endMs
            );

            byte[] jsonBytes = objectMapper.writeValueAsBytes(payload);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (GZIPOutputStream gzos = new GZIPOutputStream(baos)) {
                gzos.write(jsonBytes);
            }
            byte[] gzippedBytes = baos.toByteArray();

            s3Client.putObject(
                    PutObjectRequest.builder()
                            .bucket(bucketName)
                            .key(r2Key)
                            .contentType("application/gzip")
                            .contentEncoding("gzip")
                            .contentLength((long) gzippedBytes.length)
                            .build(),
                    RequestBody.fromBytes(gzippedBytes));

            ctx.put("chapter." + chapter.getId() + ".timingsKey", r2Key);

            log.info("studio.timing.done chapterId={} r2Key={} chars={} gzippedBytes={}",
                    chapter.getId(), r2Key, count, gzippedBytes.length);
        }

        return RepeatStatus.FINISHED;
    }

    /** Columnar gzipped timing index payload (Phase 3.4). */
    public record ColumnarTimingPayload(
            int v,
            String chapterId,
            String chars,
            int[] startMs,
            int[] endMs
    ) {}
}

