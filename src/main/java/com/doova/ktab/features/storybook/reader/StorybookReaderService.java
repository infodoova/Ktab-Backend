package com.doova.ktab.features.storybook.reader;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.reader.dto.ReaderPageDetail;
import com.doova.ktab.features.storybook.reader.dto.StorybookDownloadResponse;
import com.doova.ktab.features.storybook.reader.dto.StorybookReaderManifest;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorybookReaderService {

    private final StorybookAccessGuard guard;
    private final StorybookPageRepository pages;
    private final FileStorageService storageService;

    private static final Duration DOWNLOAD_VALIDITY = Duration.ofMinutes(15);

    @Transactional(readOnly = true)
    public StorybookReaderManifest getReaderManifest(User user, Long bookId) {
        Storybook book = guard.requireOwned(bookId, user);
        if (book.getStatus() != StorybookStatus.READY) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }

        List<StorybookPage> pageList = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        List<ReaderPageDetail> details = new ArrayList<>();
        for (StorybookPage page : pageList) {
            String imageKey = page.getCurrentImage() != null ? page.getCurrentImage().getImageKey() : null;
            String imageUrl = imageKey != null ? storageService.getFileUrl(imageKey, UrlStrategy.SIGNED) : null;
            details.add(new ReaderPageDetail((int) page.getPageIndex(), page.getKind(), page.getTextAr(),
                    page.getTextZone(), imageUrl != null ? imageUrl : imageKey));
        }

        String childName = book.getInputs() != null ? book.getInputs().childNameAr() : "";
        return new StorybookReaderManifest(book.getId(), book.getStatus(), book.getTitleAr(),
                childName, book.getPageCount(), details);
    }

    @Transactional(readOnly = true)
    public StorybookDownloadResponse getDownloadUrl(User user, Long bookId) {
        Storybook book = guard.requireOwned(bookId, user);
        if (book.getStatus() != StorybookStatus.READY || book.getPdfKey() == null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }

        String childName = book.getInputs() != null ? book.getInputs().childNameAr() : "story";
        String filename = "ktab-" + childName + "-" + bookId + ".pdf";
        Instant expiresAt = Instant.now().plus(DOWNLOAD_VALIDITY);
        String downloadUrl = storageService.getPreSignedDownloadUrl(book.getPdfKey(), DOWNLOAD_VALIDITY, filename);

        return new StorybookDownloadResponse(downloadUrl, filename, expiresAt);
    }
}
