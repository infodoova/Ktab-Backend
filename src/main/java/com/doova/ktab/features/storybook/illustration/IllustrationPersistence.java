package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageImageRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class IllustrationPersistence {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final StorybookPageRepository pages;
    private final StorybookPageImageRepository images;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;

    @Transactional(readOnly = true)
    public SheetContext sheetContext(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        Optional<StorybookCharacter> companion = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION);
        return new SheetContext(bookId, book.getStatus(), book.getStoryApprovedAt() != null,
                book.getInputs().gender(), book.getInputs().ageBand(), book.getInputs().appearance(),
                book.getInputs().companion(), book.getStyle(), child.getSheetVersion(), child.getSheetKey(),
                child.getPhotoKey(), child.getPhotoConsentAt() != null,
                companion.map(c -> c.getSheetKey() != null).orElse(false));
    }

    @Transactional
    public void saveSheets(Long bookId, int version, String childKey, String companionKey, boolean photoUsed) {
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        child.setSheetKey(childKey);
        child.setSheetVersion(version);
        child.setSheetStatus(CharacterSheetStatus.GENERATED);
        if (companionKey != null) {
            characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).ifPresent(c -> {
                c.setSheetKey(companionKey);
                c.setSheetVersion(1);
                c.setSheetStatus(CharacterSheetStatus.GENERATED);
            });
        }
        if (photoUsed) {
            enqueuer.enqueue(bookId, JobStep.PURGE_PHOTO, -1, version);
        }
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() == StorybookStatus.STORY_READY) {
            stateMachine.transition(book, StorybookStatus.CHARACTER_READY);
        }
    }

    @Transactional(readOnly = true)
    public String photoKey(Long bookId) {
        return characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getPhotoKey).orElse(null);
    }

    @Transactional
    public void markPhotoPurged(Long bookId) {
        characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).ifPresent(c -> {
            c.setPhotoKey(null);
            c.setPhotoPurgedAt(Instant.now());
        });
    }

    @Transactional(readOnly = true)
    public PageContext pageContext(Long bookId, int pageIndex, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        com.doova.ktab.features.storybook.model.StorybookPage page =
                pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        String childSheet = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD)
                .map(StorybookCharacter::getSheetKey).orElse(null);
        String companionSheet = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION)
                .map(StorybookCharacter::getSheetKey).orElse(null);
        return new PageContext(bookId, book.getStatus(), page.getId(), page.getPageIndex(), page.getKind(),
                page.getSceneEn(), page.getTextZone(), page.getCharacters(), page.getGeneration(),
                page.getRoundStartGeneration(), images.findByPage_IdAndGeneration(page.getId(), generation).isPresent(),
                childSheet, companionSheet, book.getStyle(), book.getInputs().appearance().hijab());
    }

    @Transactional
    public void ensureQaEnqueued(Long bookId, int pageIndex, int generation) {
        enqueuer.enqueue(bookId, JobStep.QA_PAGE, pageIndex, generation);
    }

    @Transactional
    public void savePageImage(Long bookId, Long pageId, int pageIndex, int generation, String key, String model,
                              java.math.BigDecimal cost) {
        com.doova.ktab.features.storybook.model.StorybookPage page = pages.findById(pageId).orElseThrow();
        if (images.findByPage_IdAndGeneration(pageId, generation).isEmpty()) {
            com.doova.ktab.features.storybook.model.StorybookPageImage image = new com.doova.ktab.features.storybook.model.StorybookPageImage();
            image.setPage(page);
            image.setGeneration(generation);
            image.setImageKey(key);
            image.setModel(model);
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.GENERATED);
            image.setCostUsd(cost);
            images.save(image);
        }
        enqueuer.enqueue(bookId, JobStep.QA_PAGE, pageIndex, generation);
    }

    private static final java.util.Set<com.doova.ktab.features.storybook.enums.PageImageStatus> DONE_OK =
            java.util.EnumSet.of(com.doova.ktab.features.storybook.enums.PageImageStatus.QA_PASSED,
                    com.doova.ktab.features.storybook.enums.PageImageStatus.ACCEPTED_BY_ADMIN);

    @Transactional(readOnly = true)
    public QaContext qaContext(Long bookId, int pageIndex, int generation) {
        Storybook book = books.findById(bookId).orElseThrow();
        com.doova.ktab.features.storybook.model.StorybookPage page =
                pages.findByStorybook_IdAndPageIndex(bookId, pageIndex).orElseThrow();
        var image = images.findByPage_IdAndGeneration(page.getId(), generation);
        return new QaContext(bookId, book.getStatus(), page.getId(),
                image.map(com.doova.ktab.features.storybook.model.StorybookPageImage::getId).orElse(null),
                image.map(com.doova.ktab.features.storybook.model.StorybookPageImage::getStatus).orElse(null),
                image.map(com.doova.ktab.features.storybook.model.StorybookPageImage::getImageKey).orElse(null),
                page.getSceneEn(), page.getCharacters(),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getSheetKey).orElse(null),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).map(StorybookCharacter::getSheetKey).orElse(null),
                book.getStyle());
    }

    @Transactional
    public void recordQa(Long bookId, Long pageId, Long imageId, int generation, VisualQaResponse verdict) {
        Storybook book = books.findByIdForUpdate(bookId).orElseThrow(); // serializes advancement per book
        com.doova.ktab.features.storybook.model.StorybookPage page = pages.findById(pageId).orElseThrow();
        com.doova.ktab.features.storybook.model.StorybookPageImage image = images.findById(imageId).orElseThrow();
        image.setQaResult(verdict);
        int attemptInRound = generation - page.getRoundStartGeneration() + 1;
        if (verdict.passed()) {
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.QA_PASSED);
            page.setCurrentImage(image);
            com.doova.ktab.features.storybook.metrics.StorybookMetrics.qaVerdict("pass");
        } else if (attemptInRound < properties.getImage().getMaxGenerations()) {
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.QA_FAILED);
            page.setGeneration(generation + 1);
            enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), generation + 1);
            com.doova.ktab.features.storybook.metrics.StorybookMetrics.qaVerdict("retry");
        } else {
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED);
            page.setCurrentImage(image);
            com.doova.ktab.features.storybook.metrics.StorybookMetrics.qaVerdict("flagged");
        }
        advance(book);
    }

    @Transactional
    public void advanceIfDone(Long bookId) {
        advance(books.findByIdForUpdate(bookId).orElseThrow());
    }

    private void advance(Storybook book) {
        if (book.getStatus() != StorybookStatus.ILLUSTRATING && book.getStatus() != StorybookStatus.QA) {
            return;
        }
        boolean allOk = true;
        boolean anyFlagged = false;
        for (com.doova.ktab.features.storybook.model.StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(book.getId())) {
            com.doova.ktab.features.storybook.enums.PageImageStatus status = images.findByPage_IdAndGeneration(p.getId(), p.getGeneration())
                    .map(com.doova.ktab.features.storybook.model.StorybookPageImage::getStatus).orElse(null);
            if (status == null || status == com.doova.ktab.features.storybook.enums.PageImageStatus.GENERATED
                    || status == com.doova.ktab.features.storybook.enums.PageImageStatus.QA_FAILED) {
                return; // still in progress
            }
            if (!DONE_OK.contains(status)) {
                allOk = false;
                anyFlagged |= (status == com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED);
            }
        }
        if (allOk) {
            stateMachine.transition(book, StorybookStatus.RENDERING);
            enqueuer.enqueue(book.getId(), JobStep.RENDER_PDF, -1, book.getPageRegenerations());
        } else if (anyFlagged && book.getStatus() == StorybookStatus.ILLUSTRATING) {
            stateMachine.transition(book, StorybookStatus.QA);
        }
    }
}
