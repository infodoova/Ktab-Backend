package com.doova.ktab.features.storybook.render;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReaderService {

    private final StorybookAccessGuard guard;
    private final RenderPersistence persistence;
    private final FileStorageService storage;

    @Transactional(readOnly = true)
    public ReaderManifest manifest(User owner, Long bookId) {
        requireReady(guard.requireOwned(bookId, owner));
        RenderContext ctx = persistence.context(bookId);
        BookRenderModel model = RenderModelFactory.build(ctx.titleAr(), ctx.childNameAr(), ctx.dedication(), ctx.level(), ctx.pages());
        List<ReaderManifest.Page> pages = model.pages().stream().map(p -> new ReaderManifest.Page(p.order(), p.kind(),
                p.textAr(), p.textZone(), p.imageFile() == null ? null : imageUrl(ctx, p.imageFile()))).toList();
        return new ReaderManifest(bookId, "rtl", model.titleAr(), pages);
    }

    @Transactional(readOnly = true)
    public String downloadUrl(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        requireReady(book);
        return storage.getPreSignedDownloadUrl(book.getPdfKey(), Duration.ofMinutes(10), "storybook-" + bookId + ".pdf");
    }

    private static void requireReady(Storybook book) {
        if (book.getStatus() != StorybookStatus.READY || book.getPdfKey() == null) {
            throw new StorybookStateConflictException(ApiMessageKey.STORYBOOK_NOT_READY);
        }
    }

    private String imageUrl(RenderContext ctx, String imageFile) {
        int pageIndex = Integer.parseInt(imageFile.substring("img/p".length(), imageFile.length() - ".jpg".length()));
        String key = ctx.imageKeysByPageIndex().get(pageIndex);
        return key == null ? null : storage.getFileUrl(key, UrlStrategy.SIGNED);
    }
}
