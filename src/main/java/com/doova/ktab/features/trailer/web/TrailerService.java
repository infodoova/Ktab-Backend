package com.doova.ktab.features.trailer.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.trailer.agent.TrailerAgentGateway;
import com.doova.ktab.features.trailer.config.TrailerProperties;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.features.trailer.pipeline.TrailerNotifier;
import com.doova.ktab.features.trailer.repository.BookTrailerRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class TrailerService {

    private final TrailerAccess access;
    private final BookTrailerRepository trailers;
    private final TrailerAgentGateway gateway;
    private final FileStorageService storage;
    private final TrailerProperties properties;
    private final TrailerNotifier notifier;

    @Transactional
    public TrailerView create(User user, Long bookId) {
        access.requireBook(user, bookId);
        if (trailers.existsByBookIdAndStatusIn(bookId, TrailerStatus.ACTIVE)) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
        if (!access.isAdmin(user) && trailers.countByBookIdAndCreatedAtAfterAndStatusNotIn(bookId,
                LocalDateTime.now().minusDays(30), EnumSet.of(TrailerStatus.FAILED, TrailerStatus.CANCELLED))
                >= properties.getPerBookPer30Days()) {
            throw new BadRequestException(ApiMessageKey.TRAILER_LIMIT_REACHED);
        }
        BookTrailer t = new BookTrailer();
        t.setBookId(bookId);
        t.setRequestedById(user.getId());
        t.setStatus(TrailerStatus.QUEUED);
        try {
            return TrailerView.of(trailers.save(t), true);
        } catch (DataIntegrityViolationException raced) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
    }

    @Transactional(readOnly = true)
    public List<TrailerView> list(User user, Long bookId) {
        access.requireBook(user, bookId);
        return trailers.findByBookIdOrderByIdDesc(bookId).stream().map(TrailerView::of).toList();
    }

    @Transactional(readOnly = true)
    public TrailerView get(User user, Long id) {
        return TrailerView.of(owned(user, id));
    }

    /** Presigned links: captioned MP4, clean MP4 and SRT (whichever exist). NEEDS_REVIEW is downloadable by admins. */
    @Transactional(readOnly = true)
    public Map<String, String> downloadUrls(User user, Long id) {
        BookTrailer t = owned(user, id);
        boolean allowed = t.getStatus() == TrailerStatus.READY
                || (t.getStatus() == TrailerStatus.NEEDS_REVIEW && access.isAdmin(user));
        if (!allowed || t.getVideoKey() == null) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        Map<String, String> urls = new LinkedHashMap<>();
        urls.put("video", presign(t.getVideoKey(), "trailer-" + t.getBookId() + ".mp4"));
        if (t.getCleanVideoKey() != null) {
            urls.put("videoClean", presign(t.getCleanVideoKey(), "trailer-" + t.getBookId() + "-clean.mp4"));
        }
        if (t.getCaptionsKey() != null) {
            urls.put("captions", presign(t.getCaptionsKey(), "trailer-" + t.getBookId() + "-ar.srt"));
        }
        return urls;
    }

    @Transactional
    public void cancel(User user, Long id) {
        BookTrailer t = owned(user, id);
        if (!t.getStatus().isActive()) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        if (t.getSessionId() != null) {
            gateway.interrupt(t.getSessionId());
        }
        t.setStatus(TrailerStatus.CANCELLED);
        t.setFinishedAt(Instant.now());
    }

    @Transactional
    public TrailerView review(User admin, Long id, boolean approve) {
        if (!access.isAdmin(admin)) {
            throw new AccessDeniedException("Only admins review trailers");
        }
        BookTrailer t = trailers.findById(id).orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        if (t.getStatus() != TrailerStatus.NEEDS_REVIEW) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        t.setStatus(approve ? TrailerStatus.READY : TrailerStatus.FAILED);
        trailers.save(t);
        notifier.notifyCompletion(t);
        return TrailerView.of(t);
    }

    /** Admin: re-queue a FAILED or NEEDS_REVIEW trailer. Preserves bookFileId to avoid re-uploading the PDF. */
    @Transactional
    public TrailerView retry(User admin, Long id) {
        if (!access.isAdmin(admin)) {
            throw new AccessDeniedException("Only admins may retry trailers");
        }
        BookTrailer t = trailers.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        if (t.getStatus() != TrailerStatus.FAILED && t.getStatus() != TrailerStatus.NEEDS_REVIEW) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_NOT_READY);
        }
        if (trailers.existsByBookIdAndStatusIn(t.getBookId(), TrailerStatus.ACTIVE)) {
            throw new TrailerConflictException(ApiMessageKey.TRAILER_ALREADY_RUNNING);
        }
        t.setStatus(TrailerStatus.QUEUED);
        t.setSessionId(null);
        t.setAgentVersion(null);
        t.setHiggsfieldGenerations(0);
        t.setHiggsfieldCalls(0);
        t.setOutcomeResult(null);
        t.setOutcomeExplanation(null);
        t.setError(null);
        t.setStartedAt(null);
        t.setFinishedAt(null);
        t.setNextCheckAt(java.time.Instant.now());
        trailers.save(t);
        return TrailerView.of(t);
    }

    /** Webhook: check this session on the next tick instead of waiting for the regular interval (D6). */
    @Transactional
    public void nudge(String sessionId) {
        trailers.findBySessionId(sessionId).ifPresent(t -> t.setNextCheckAt(Instant.now()));
    }

    private String presign(String key, String filename) {
        return storage.getPreSignedDownloadUrl(key, Duration.ofMinutes(10), filename);
    }

    private BookTrailer owned(User user, Long id) {
        BookTrailer t = trailers.findById(id).orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.TRAILER_NOT_FOUND));
        access.requireBook(user, t.getBookId());
        return t;
    }
}
