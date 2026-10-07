package com.doova.ktab.features.storybook.render;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ReaderServiceTest {

    private final StorybookAccessGuard guard = mock(StorybookAccessGuard.class);
    private final RenderPersistence persistence = mock(RenderPersistence.class);
    private final FileStorageService storage = mock(FileStorageService.class);
    private final ReaderService service = new ReaderService(guard, persistence, storage);
    private final User owner = new User();
    private final Storybook book = new Storybook();

    @BeforeEach
    void setUp() {
        book.setId(9L);
        book.setStatus(StorybookStatus.READY);
        book.setPdfKey("storybook/9/book-r0.pdf");
        when(guard.requireOwned(9L, owner)).thenReturn(book);
        when(persistence.context(9L)).thenReturn(new RenderContext(9L, StorybookStatus.READY, "يومي", "سامي", null,
                TashkeelLevel.NONE,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.BOTTOM)),
                Map.of(0, "k0", 1, "k1")));
        when(storage.getFileUrl(anyString(), eq(UrlStrategy.SIGNED))).thenAnswer(inv -> "https://signed/" + inv.getArgument(0));
    }

    @Test
    void manifestMatchesThePdfLayout() {
        ReaderManifest m = service.manifest(owner, 9L);
        assertThat(m.dir()).isEqualTo("rtl");
        assertThat(m.pages()).extracting(ReaderManifest.Page::kind).containsExactly(
                RenderPage.Kind.COVER, RenderPage.Kind.DEDICATION, RenderPage.Kind.STORY, RenderPage.Kind.BACK);
        assertThat(m.pages().get(2).textAr()).isEqualTo("ذهب سامي.");
        assertThat(m.pages().get(2).imageUrl()).isEqualTo("https://signed/k1");
        assertThat(m.pages().get(1).imageUrl()).isNull();
    }

    @Test
    void theReaderLoadsTheSmallCopyWhereThereIsOneAndTheOriginalWhereThereIsNot() {
        when(persistence.context(9L)).thenReturn(new RenderContext(9L, StorybookStatus.READY, "يومي", "سامي", null,
                TashkeelLevel.NONE,
                List.of(new RenderModelFactory.PageSource(0, PageKind.COVER, null, TextZone.TOP),
                        new RenderModelFactory.PageSource(1, PageKind.STORY, "ذَهَبَ سامي.", TextZone.BOTTOM)),
                Map.of(0, "k0", 1, "k1"), Map.of(1, "k1.web")));

        ReaderManifest m = service.manifest(owner, 9L);

        assertThat(m.pages().get(0).imageUrl()).isEqualTo("https://signed/k0");
        assertThat(m.pages().get(2).imageUrl()).isEqualTo("https://signed/k1.web");
    }

    @Test
    void notReadyIsAConflict() {
        book.setStatus(StorybookStatus.ILLUSTRATING);
        assertThatThrownBy(() -> service.manifest(owner, 9L)).isInstanceOf(StorybookStateConflictException.class);
        assertThatThrownBy(() -> service.downloadUrl(owner, 9L)).isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void downloadIsShortLived() {
        when(storage.getPreSignedDownloadUrl("storybook/9/book-r0.pdf", Duration.ofMinutes(10), "storybook-9.pdf"))
                .thenReturn("https://download");
        assertThat(service.downloadUrl(owner, 9L)).isEqualTo("https://download");
    }

    @Test
    void otherUsersBookIsNotFound() {
        User stranger = new User();
        when(guard.requireOwned(9L, stranger)).thenThrow(new ResourceNotFoundException(
                com.doova.ktab.enums.message.ApiMessageKey.STORYBOOK_NOT_FOUND));
        assertThatThrownBy(() -> service.downloadUrl(stranger, 9L)).isInstanceOf(ResourceNotFoundException.class);
        verifyNoInteractions(storage);
    }
}
