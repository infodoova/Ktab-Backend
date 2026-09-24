package com.doova.ktab.features.storybook.reader;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.storybook.enums.PageKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.enums.TextZone;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.reader.dto.StorybookDownloadResponse;
import com.doova.ktab.features.storybook.reader.dto.StorybookReaderManifest;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorybookReaderServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final StorybookPageRepository pages = mock(StorybookPageRepository.class);
    private final FileStorageService storageService = mock(FileStorageService.class);
    private StorybookReaderService service;
    private final User user = new User();

    @BeforeEach
    void setUp() {
        user.setId(1L);
        service = new StorybookReaderService(guard, pages, storageService);
    }

    @Test
    void returnsReaderManifestForReadyBook() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        book.setTitleAr("عنوان القصة");
        book.setPageCount(10);
        StoryInputs inputs = new StoryInputs("سامي", null, null, null, List.of(), null, null, null);
        book.setInputs(inputs);
        when(guard.requireOwned(10L, user)).thenReturn(book);

        StorybookPage p1 = new StorybookPage();
        p1.setPageIndex(1);
        p1.setKind(PageKind.STORY);
        p1.setTextAr("نص");
        p1.setTextZone(TextZone.BOTTOM);
        StorybookPageImage img = new StorybookPageImage();
        img.setImageKey("storybook/10/pages/1/g1.png");
        p1.setCurrentImage(img);

        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(p1));
        when(storageService.getFileUrl("storybook/10/pages/1/g1.png", UrlStrategy.SIGNED))
                .thenReturn("https://r2.ktab.app/presigned/p1.png");

        StorybookReaderManifest manifest = service.getReaderManifest(user, 10L);

        assertThat(manifest.bookId()).isEqualTo(10L);
        assertThat(manifest.status()).isEqualTo(StorybookStatus.READY);
        assertThat(manifest.titleAr()).isEqualTo("عنوان القصة");
        assertThat(manifest.childNameAr()).isEqualTo("سامي");
        assertThat(manifest.pages()).hasSize(1);
        assertThat(manifest.pages().get(0).imageKey()).isEqualTo("https://r2.ktab.app/presigned/p1.png");
    }

    @Test
    void rejectsReaderManifestIfNotReady() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.ILLUSTRATING);
        when(guard.requireOwned(10L, user)).thenReturn(book);

        assertThatThrownBy(() -> service.getReaderManifest(user, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void returnsDownloadUrlForReadyBook() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.READY);
        book.setPdfKey("storybook/10/book-r0.pdf");
        StoryInputs inputs = new StoryInputs("سامي", null, null, null, List.of(), null, null, null);
        book.setInputs(inputs);
        when(guard.requireOwned(10L, user)).thenReturn(book);

        when(storageService.getPreSignedDownloadUrl(eq("storybook/10/book-r0.pdf"), any(), any()))
                .thenReturn("https://r2.ktab.app/download/book.pdf");

        StorybookDownloadResponse response = service.getDownloadUrl(user, 10L);

        assertThat(response.downloadUrl()).isEqualTo("https://r2.ktab.app/download/book.pdf");
        assertThat(response.filename()).contains("سامي").contains("10.pdf");
        assertThat(response.expiresAt()).isNotNull();
    }

    @Test
    void rejectsDownloadUrlIfNotReady() {
        Storybook book = new Storybook();
        book.setId(10L);
        book.setStatus(StorybookStatus.RENDERING);
        when(guard.requireOwned(10L, user)).thenReturn(book);

        assertThatThrownBy(() -> service.getDownloadUrl(user, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
    }
}
