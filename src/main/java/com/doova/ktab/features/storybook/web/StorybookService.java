package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.LanguageVariety;
import com.doova.ktab.features.storybook.enums.LlmPurpose;
import com.doova.ktab.features.storybook.enums.TashkeelLevel;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.story.ModerationService;
import com.doova.ktab.features.storybook.character.PhotoIntakeService;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StorybookService {

    private static final Set<Integer> PAGE_COUNTS = Set.of(15, 16, 17, 18, 19, 20);

    /** Every character costs a sheet and a reference image on each page it appears in, so the cast is capped. */
    static final int MAX_CHARACTERS = 4;

    private final ChildProfileService children;
    private final ModerationService moderation;
    private final AiCallLedger ledger;
    private final StorybookDraftWriter writer;
    private final StorybookViewMapper mapper;
    private final StorybookAccessGuard guard;
    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookCharacterRepository characters;
    private final com.doova.ktab.features.storybook.config.StorybookProperties properties;
    private final PhotoIntakeService photoIntakeService;
    private final org.springframework.transaction.support.TransactionTemplate tx;

    /** Deliberately not @Transactional: moderation calls an LLM and must not hold a DB connection. */
    public StorybookDetail create(User owner, CreateStorybookRequest r) {
        return create(owner, r, null, null, null, r.photoConsent());
    }

    public StorybookDetail create(
            User owner,
            CreateStorybookRequest r,
            MultipartFile childPhoto,
            List<MultipartFile> characterPhotos,
            Boolean consent
    ) {
        return create(owner, r, childPhoto, null, characterPhotos, consent);
    }

    public StorybookDetail create(
            User owner,
            CreateStorybookRequest r,
            MultipartFile childPhoto,
            MultipartFile companionPhoto,
            List<MultipartFile> characterPhotos,
            Boolean consent
    ) {
        long recent = books.countByOwner_IdAndCreatedAtAfter(owner.getId(), java.time.LocalDateTime.now().minusDays(1));
        if (recent >= properties.getLimits().getDraftsPerUserPerDay()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }

        if (r.child() == null && r.childProfileId() == null) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }
        if (r.childProfileId() != null && r.child() == null) {
            children.requireOwned(owner, r.childProfileId());
        }

        if (!PAGE_COUNTS.contains(r.pageCount())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_INVALID_PAGE_COUNT);
        }
        List<com.doova.ktab.features.storybook.enums.Interest> interests =
                r.interests() == null ? List.of() : List.copyOf(new LinkedHashSet<>(r.interests()));
        if (interests.size() > 3) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_TOO_MANY_INTERESTS);
        }

        if (r.characters() != null && r.characters().size() > MAX_CHARACTERS) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_TOO_MANY_CHARACTERS);
        }

        Blueprint blueprint = Blueprint.custom();

        LanguageVariety variety = r.variety() == null ? LanguageVariety.MSA : r.variety();
        TashkeelLevel tashkeel;
        if (variety.isDialect()) {
            if (r.tashkeelLevel() != null && r.tashkeelLevel() != TashkeelLevel.NONE) {
                throw new BadRequestException(ApiMessageKey.STORYBOOK_DIALECT_REQUIRES_NO_TASHKEEL);
            }
            tashkeel = TashkeelLevel.NONE;
        } else {
            if (r.tashkeelLevel() == null) {
                throw new BadRequestException(ApiMessageKey.STORYBOOK_TASHKEEL_REQUIRED);
            }
            tashkeel = r.tashkeelLevel();
        }

        if (r.companion() != null && !r.companion().nameAr().matches(CreateChildProfileRequest.ARABIC_NAME)) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        String dedication = r.dedication() == null || r.dedication().isBlank() ? null : r.dedication().strip();
        ModerationService.ModerationOutcome outcome = moderation.moderate(dedication);
        if (outcome.llmCall() != null) {
            ledger.recordLlm(null, null, LlmPurpose.MODERATION, outcome.llmCall());
        }
        if (!outcome.allowed()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_DEDICATION_REJECTED);
        }

        ChildProfile child = r.child() != null
                ? children.createProfile(owner, r.child())
                : children.requireOwned(owner, r.childProfileId());

        StoryInputs inputs = new StoryInputs(child.getNameAr(), child.getGender(), child.getAgeBand(),
                child.getAppearance(), interests, r.companion(),
                r.setting(), r.timeOfDay(), r.place(), blueprint);
        Storybook book = writer.insertDraft(owner, child, inputs,
                new StorybookDraftWriter.ResolvedSettings(r, variety, tashkeel, dedication));
        photoIntakeService.attachPhotos(book.getId(), r, childPhoto, companionPhoto, characterPhotos, consent);
        return detail(owner, book.getId());
    }

    @Transactional(readOnly = true)
    public List<StorybookSummary> list(User owner) {
        List<Storybook> userBooks = books.findByOwner_IdOrderByCreatedAtDesc(owner.getId());
        if (userBooks.isEmpty()) {
            return List.of();
        }

        List<Long> bookIds = userBooks.stream().map(Storybook::getId).toList();
        Map<Long, String> coverUrlByBookId = pages.findCoversByStorybookIds(bookIds).stream()
                .filter(p -> p.getCurrentImage() != null && p.getCurrentImage().getImageKey() != null)
                .collect(Collectors.toMap(
                        p -> p.getStorybook().getId(),
                        mapper::resolveCoverImageUrl,
                        (existing, replacement) -> existing
                ));

        return userBooks.stream()
                .map(b -> mapper.toSummary(b, coverUrlByBookId.get(b.getId())))
                .toList();
    }

    @Transactional(readOnly = true)
    public StorybookDetail detail(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        return mapper.toDetail(book, pages.findByStorybook_IdOrderByPageIndexAsc(bookId),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElse(null));
    }

    /**
     * Deliberately not @Transactional: moderation calls an LLM and must not hold a DB connection (or an open
     * transaction) while it waits. Validation runs in a read-only transaction, moderation runs with none, and the
     * changes are written in a short transaction at the end. A rejection therefore never writes anything.
     */
    public StorybookDetail editStory(User owner, Long bookId, com.doova.ktab.features.storybook.web.dto.EditStoryRequest r) {
        EditPlan plan = tx.execute(status -> planEdit(owner, bookId, r));
        moderateAll(bookId, plan.textsToModerate());
        return tx.execute(status -> applyEdit(owner, bookId, plan));
    }

    private record PagePlan(int pageIndex, boolean setText, String text, String scene) {
    }

    private record EditPlan(String title, List<PagePlan> pages, List<String> textsToModerate) {
    }

    /** Validates the request against the current state and works out what to write; mutates nothing. */
    private EditPlan planEdit(User owner, Long bookId, com.doova.ktab.features.storybook.web.dto.EditStoryRequest r) {
        Storybook book = guard.requireOwned(bookId, owner);
        requireEditable(book);

        boolean hasTitle = r.titleAr() != null && !r.titleAr().isBlank();
        boolean hasPages = r.pages() != null && !r.pages().isEmpty();
        if (!hasTitle && !hasPages) {
            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
        }

        String childName = book.getInputs() != null ? book.getInputs().childNameAr() : null;
        // Only texts that actually changed are moderated, and they are moderated together (see moderateAll).
        List<String> textsToModerate = new java.util.ArrayList<>();

        String cleanTitle = null;
        if (hasTitle) {
            cleanTitle = r.titleAr().strip();
            if (cleanTitle.length() < 2 || cleanTitle.length() > 200) {
                throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
            }
            if (childName != null && !childName.isBlank()) {
                cleanTitle = com.doova.ktab.features.storybook.story.NameEnforcer.enforce(cleanTitle, childName);
            }
            if (!cleanTitle.equals(book.getTitleAr())) {
                textsToModerate.add(cleanTitle);
            }
        }

        List<PagePlan> pagePlans = new java.util.ArrayList<>();
        if (hasPages) {
            Map<Integer, com.doova.ktab.features.storybook.model.StorybookPage> pageMap =
                    pages.findByStorybook_IdOrderByPageIndexAsc(bookId).stream()
                            .collect(Collectors.toMap(p -> (int) p.getPageIndex(), p -> p));

            int maxWords = book.getInputs() != null && book.getInputs().ageBand() != null
                    ? book.getInputs().ageBand().maxWordsPerPage()
                    : 70;

            for (com.doova.ktab.features.storybook.web.dto.EditPageRequest pReq : r.pages()) {
                if (pReq.pageIndex() == null) {
                    throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                }
                com.doova.ktab.features.storybook.model.StorybookPage page = pageMap.get(pReq.pageIndex());
                if (page == null) {
                    throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                }

                boolean pageHasText = pReq.textAr() != null;
                boolean pageHasScene = pReq.sceneEn() != null;
                if (!pageHasText && !pageHasScene) {
                    throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                }

                String text = null;
                if (pageHasText) {
                    if (page.getKind() != com.doova.ktab.features.storybook.enums.PageKind.COVER && pReq.textAr().isBlank()) {
                        throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                    }
                    if (!pReq.textAr().isBlank()) {
                        text = pReq.textAr().strip();
                        if (text.length() > 1000) {
                            throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                        }
                        if (childName != null && !childName.isBlank()) {
                            text = com.doova.ktab.features.storybook.story.NameEnforcer.enforce(text, childName);
                        }
                        if (com.doova.ktab.features.storybook.story.ArabicText.wordCount(text) > maxWords) {
                            throw new BadRequestException(ApiMessageKey.STORYBOOK_TEXT_TOO_LONG);
                        }
                        if (!text.equals(page.getTextAr())) {
                            textsToModerate.add(text);
                        }
                    }
                }

                String scene = null;
                if (pageHasScene) {
                    if (pReq.sceneEn().isBlank() || pReq.sceneEn().length() > 2000) {
                        throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                    }
                    scene = pReq.sceneEn().strip();
                }
                pagePlans.add(new PagePlan(pReq.pageIndex(), pageHasText, text, scene));
            }
        }
        return new EditPlan(cleanTitle, pagePlans, textsToModerate);
    }

    /** Writes a moderated plan. The state is checked again because it may have changed while moderation ran. */
    private StorybookDetail applyEdit(User owner, Long bookId, EditPlan plan) {
        Storybook book = guard.requireOwned(bookId, owner);
        requireEditable(book);

        if (plan.title() != null) {
            book.setTitleAr(plan.title());
            books.save(book);
        }

        List<com.doova.ktab.features.storybook.model.StorybookPage> bookPages = pages.findByStorybook_IdOrderByPageIndexAsc(bookId);
        if (!plan.pages().isEmpty()) {
            Map<Integer, com.doova.ktab.features.storybook.model.StorybookPage> pageMap = bookPages.stream()
                    .collect(Collectors.toMap(p -> (int) p.getPageIndex(), p -> p));
            for (PagePlan pp : plan.pages()) {
                com.doova.ktab.features.storybook.model.StorybookPage page = pageMap.get(pp.pageIndex());
                if (page == null) {
                    throw new BadRequestException(ApiMessageKey.VALIDATION_FAILED);
                }
                if (pp.setText()) {
                    page.setTextAr(pp.text());
                }
                if (pp.scene() != null) {
                    page.setSceneEn(pp.scene());
                    if (page.getKind() == com.doova.ktab.features.storybook.enums.PageKind.COVER) {
                        book.setCoverSceneEn(pp.scene());
                        books.save(book);
                    }
                }
            }
            pages.saveAll(bookPages);
        }

        return mapper.toDetail(book, bookPages,
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElse(null));
    }

    private static void requireEditable(Storybook book) {
        if (book.getStatus() != com.doova.ktab.features.storybook.enums.StorybookStatus.STORY_READY || book.getStoryApprovedAt() != null) {
            throw new com.doova.ktab.features.storybook.exception.StorybookStateConflictException(ApiMessageKey.STORYBOOK_INVALID_STATE);
        }
    }

    /** Moderates all changed texts with a single LLM call and records its cost. */
    private void moderateAll(Long bookId, List<String> texts) {
        if (texts.isEmpty()) {
            return;
        }
        ModerationService.ModerationOutcome outcome = moderation.moderateBatch(texts);
        if (outcome.llmCall() != null) {
            ledger.recordLlm(bookId, null, LlmPurpose.MODERATION, outcome.llmCall());
        }
        if (!outcome.allowed()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_TEXT_REJECTED);
        }
    }
}
