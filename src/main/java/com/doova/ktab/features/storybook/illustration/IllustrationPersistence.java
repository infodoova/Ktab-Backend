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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class IllustrationPersistence {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final StorybookPageRepository pages;
    private final StorybookPageImageRepository images;
    private final JobEnqueuer enqueuer;
    private final StorybookStateMachine stateMachine;
    private final StorybookProperties properties;
    private final org.springframework.context.ApplicationEventPublisher events;

    @Transactional(readOnly = true)
    public SheetContext sheetContext(Long bookId) {
        Storybook book = books.findById(bookId).orElseThrow();
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        Optional<StorybookCharacter> companion = characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION);
        return new SheetContext(bookId, book.getStatus(), book.getStoryApprovedAt() != null,
                book.getInputs().gender(), book.getInputs().ageBand(), book.getInputs().appearance(),
                book.getInputs().companion(), book.getStyle(), child.getSheetVersion(), child.getSheetKey(),
                child.getPhotoKey(), child.getPhotoConsentAt() != null,
                companion.map(c -> c.getSheetKey() != null).orElse(false), child.getClothing(), supportingSheets(bookId),
                companion.map(StorybookCharacter::getPhotoKey).orElse(null));
    }

    private java.util.List<SheetContext.SupportingSheet> supportingSheets(Long bookId) {
        java.util.List<StorybookCharacter> rows = characters.findByStorybook_IdAndKindOrderByIdAsc(bookId, CharacterKind.SUPPORTING);
        java.util.List<com.doova.ktab.features.storybook.story.SupportingCast> cast = com.doova.ktab.features.storybook.story.SupportingCast.of(rows);
        java.util.List<SheetContext.SupportingSheet> out = new java.util.ArrayList<>();
        for (int i = 0; i < rows.size(); i++) {
            out.add(new SheetContext.SupportingSheet(rows.get(i).getCharacterId(), cast.get(i).ref(), cast.get(i).describeEn(),
                    rows.get(i).getClothing(), rows.get(i).getSheetKey(), rows.get(i).getPhotoKey()));
        }
        return out;
    }

    @Transactional
    public void saveSheets(Long bookId, int version, String childKey, String companionKey, boolean photoUsed) {
        saveSheets(bookId, version, childKey, companionKey, photoUsed, java.util.Map.of());
    }

    /** {@code supportingKeys} maps a supporting character's id to the sheet just drawn for it. */
    @Transactional
    public void saveSheets(Long bookId, int version, String childKey, String companionKey, boolean photoUsed,
                           java.util.Map<String, String> supportingKeys) {
        StorybookCharacter child = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElseThrow();
        child.setSheetKey(childKey);
        child.setMasterSheetKey(childKey);
        child.setSheetVersion(version);
        child.setSheetStatus(CharacterSheetStatus.GENERATED);
        if (companionKey != null) {
            characters.findByStorybook_IdAndKind(bookId, CharacterKind.COMPANION).ifPresent(c -> {
                c.setSheetKey(companionKey);
                c.setMasterSheetKey(companionKey);
                c.setSheetVersion(1);
                c.setSheetStatus(CharacterSheetStatus.GENERATED);
            });
        }
        if (supportingKeys != null && !supportingKeys.isEmpty()) {
            for (StorybookCharacter c : characters.findByStorybook_IdAndKindOrderByIdAsc(bookId, CharacterKind.SUPPORTING)) {
                String key = supportingKeys.get(c.getCharacterId());
                if (key != null) {
                    c.setSheetKey(key);
                    c.setMasterSheetKey(key);
                    c.setSheetVersion(1);
                    c.setSheetStatus(CharacterSheetStatus.GENERATED);
                }
            }
        }
        if (photoUsed) {
            enqueuer.enqueue(bookId, JobStep.PURGE_PHOTO, -1, version);
        }
        Storybook book = books.findById(bookId).orElseThrow();
        if (book.getStatus() == StorybookStatus.STORY_READY) {
            stateMachine.transition(book, StorybookStatus.CHARACTER_READY);
        }
        events.publishEvent(new com.doova.ktab.features.storybook.event.StorybookCharacterReadyEvent(bookId));
    }

    @Transactional(readOnly = true)
    public String photoKey(Long bookId) {
        return characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getPhotoKey).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<String> allPhotoKeys(Long bookId) {
        return characters.findByStorybook_Id(bookId).stream()
                .map(StorybookCharacter::getPhotoKey)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    @Transactional
    public void markPhotoPurged(Long bookId) {
        characters.findByStorybook_Id(bookId).forEach(c -> {
            if (c.getPhotoKey() != null) {
                c.setPhotoKey(null);
                c.setPhotoPurgedAt(Instant.now());
            }
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
        String anchorKey = null;
        if (page.getKind() != com.doova.ktab.features.storybook.enums.PageKind.COVER) {
            anchorKey = pages.findByStorybook_IdAndPageIndex(bookId, 0)
                    .map(com.doova.ktab.features.storybook.model.StorybookPage::getCurrentImage)
                    .map(com.doova.ktab.features.storybook.model.StorybookPageImage::getImageKey).orElse(null);
        }
        String clothing = characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).map(StorybookCharacter::getClothing).orElse(null);
        boolean hijab = book.getInputs() != null && book.getInputs().appearance() != null
                && book.getInputs().appearance().hijab();
        boolean glasses = book.getInputs() != null && book.getInputs().appearance() != null
                && book.getInputs().appearance().glasses();
        String appearanceEn = book.getInputs() != null && book.getInputs().appearance() != null
                ? book.getInputs().appearance().describeEn() : null;
        var companion = book.getInputs() != null ? book.getInputs().companion() : null;
        var setting = book.getInputs() != null ? book.getInputs().setting() : null;
        var timeOfDay = book.getInputs() != null ? book.getInputs().timeOfDay() : null;
        var place = book.getInputs() != null ? book.getInputs().place() : null;
        return new PageContext(bookId, book.getStatus(), page.getId(), page.getPageIndex(), page.getKind(),
                page.getSceneEn(), page.getTextZone(), page.getCharacters(), page.getGeneration(),
                page.getRoundStartGeneration(), images.findByPage_IdAndGeneration(page.getId(), generation).isPresent(),
                childSheet, companionSheet, book.getStyle(), hijab,
                anchorKey, glasses, appearanceEn, clothing,
                companion == null ? null : companion.describeEn(), StyleBible.notesOf(book.getStyleBible()),
                supportingSheets(bookId).stream()
                        .map(x -> new PageContext.SupportingLook(x.ref(), x.describeEn(), x.clothing(), x.sheetKey())).toList(),
                setting, timeOfDay, place);
    }

    @Transactional
    public void ensureQaEnqueued(Long bookId, int pageIndex, int generation) {
        enqueuer.enqueue(bookId, JobStep.QA_PAGE, pageIndex, generation);
    }

    @Transactional
    public void savePageImage(Long bookId, Long pageId, int pageIndex, int generation, String key, String model,
                              java.math.BigDecimal cost) {
        savePageImage(bookId, pageId, pageIndex, generation, key, null, model, cost);
    }

    /** {@code webKey} is the downscaled JPEG copy readers load; null when none was made. */
    @Transactional
    public void savePageImage(Long bookId, Long pageId, int pageIndex, int generation, String key, String webKey,
                              String model, java.math.BigDecimal cost) {
        com.doova.ktab.features.storybook.model.StorybookPage page = pages.findById(pageId).orElseThrow();
        if (images.findByPage_IdAndGeneration(pageId, generation).isEmpty()) {
            com.doova.ktab.features.storybook.model.StorybookPageImage image = new com.doova.ktab.features.storybook.model.StorybookPageImage();
            image.setPage(page);
            image.setGeneration(generation);
            image.setImageKey(key);
            image.setWebImageKey(webKey);
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
        } else if (attemptInRound < properties.getImage().getMaxGenerations() && !repeatsTheSameFailure(page, generation, verdict)) {
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.QA_FAILED);
            page.setGeneration(generation + 1);
            enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, page.getPageIndex(), generation + 1);
            com.doova.ktab.features.storybook.metrics.StorybookMetrics.qaVerdict("retry");
        } else {
            image.setStatus(com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED);
            page.setCurrentImage(image);
            com.doova.ktab.features.storybook.metrics.StorybookMetrics.qaVerdict("flagged");
        }
        if (page.getKind() == com.doova.ktab.features.storybook.enums.PageKind.COVER
                && image.getStatus() != com.doova.ktab.features.storybook.enums.PageImageStatus.QA_FAILED) {
            releaseStoryPages(bookId); // the cover is settled (passed, or flagged for a human): it now anchors every other page
        }
        advance(book);
    }

    /**
     * True when this attempt and the ones just before it all failed the same checks. A redraw with the same prompt and
     * the same references will most likely fail the same way (an outfit the sheet disagrees with, say), so the page is
     * flagged for a person instead of spending the rest of its attempts. The earlier verdicts are the ones already stored.
     */
    private boolean repeatsTheSameFailure(com.doova.ktab.features.storybook.model.StorybookPage page, int generation, VisualQaResponse verdict) {
        int limit = properties.getImage().getRepeatFailureLimit();
        if (limit < 2) {
            return false;
        }
        int sameInARow = 1;
        for (int g = generation - 1; g >= page.getRoundStartGeneration() && sameInARow < limit; g--) {
            VisualQaResponse earlier = images.findByPage_IdAndGeneration(page.getId(), g)
                    .map(com.doova.ktab.features.storybook.model.StorybookPageImage::getQaResult).orElse(null);
            if (earlier == null || !failedChecks(earlier).equals(failedChecks(verdict))) {
                break;
            }
            sameInARow++;
        }
        return sameInARow >= limit;
    }

    private static java.util.Set<String> failedChecks(VisualQaResponse v) {
        java.util.Set<String> failed = new java.util.TreeSet<>();
        if (!v.identityMatch()) failed.add("identity");
        if (!v.sceneMatch()) failed.add("scene");
        if (v.strayText()) failed.add("text");
        if (!v.anatomyOk()) failed.add("anatomy");
        if (!v.safeForChildren()) failed.add("safety");
        return failed;
    }

    /** Idempotent: a job that already exists for a page and generation is not created twice. */
    private void releaseStoryPages(Long bookId) {
        for (com.doova.ktab.features.storybook.model.StorybookPage p : pages.findByStorybook_IdOrderByPageIndexAsc(bookId)) {
            if (p.getKind() == com.doova.ktab.features.storybook.enums.PageKind.STORY) {
                enqueuer.enqueue(bookId, JobStep.ILLUSTRATE_PAGE, p.getPageIndex(), Math.max(1, p.getGeneration()));
            }
        }
    }

    /**
     * Repairs a book whose drawing has stalled: a page with no image and no live drawing job, or an image waiting for a
     * check no job is going to run, gets that job (re)started. Story pages are left alone until the cover has settled,
     * because they are released by it. Then the book is advanced in case every page was already done. Returns how many
     * jobs it started.
     */
    @Transactional
    public int reconcile(Long bookId, java.time.Instant succeededBefore) {
        Storybook book = books.findByIdForUpdate(bookId).orElseThrow();
        if (book.getStatus() != StorybookStatus.ILLUSTRATING) {
            return 0;
        }
        java.util.List<com.doova.ktab.features.storybook.model.StorybookPage> all = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        boolean coverSettled = all.stream()
                .filter(p -> p.getKind() == com.doova.ktab.features.storybook.enums.PageKind.COVER)
                .allMatch(p -> images.findByPage_IdAndGeneration(p.getId(), p.getGeneration())
                        .map(i -> DONE_OK.contains(i.getStatus())
                                || i.getStatus() == com.doova.ktab.features.storybook.enums.PageImageStatus.FLAGGED)
                        .orElse(false));
        int started = 0;
        for (com.doova.ktab.features.storybook.model.StorybookPage p : all) {
            boolean story = p.getKind() != com.doova.ktab.features.storybook.enums.PageKind.COVER;
            if (p.getGeneration() < 1 || (story && !coverSettled)) {
                continue;
            }
            var image = images.findByPage_IdAndGeneration(p.getId(), p.getGeneration());
            JobStep missing = null;
            if (image.isEmpty()) {
                missing = JobStep.ILLUSTRATE_PAGE;
            } else if (image.get().getStatus() == com.doova.ktab.features.storybook.enums.PageImageStatus.GENERATED) {
                missing = JobStep.QA_PAGE;
            }
            if (missing != null && enqueuer.ensure(bookId, missing, p.getPageIndex(), p.getGeneration(), succeededBefore)) {
                log.warn("storybook {} page {}: no {} job was going to run for generation {}; started one",
                        bookId, p.getPageIndex(), missing, p.getGeneration());
                started++;
            }
        }
        advance(book);
        return started;
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
