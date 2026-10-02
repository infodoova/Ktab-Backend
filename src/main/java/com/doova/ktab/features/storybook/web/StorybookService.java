package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.blueprint.BlueprintCatalog;
import com.doova.ktab.features.storybook.cost.AiCallLedger;
import com.doova.ktab.features.storybook.enums.AgeBand;
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
import com.doova.ktab.features.storybook.web.dto.BlueprintSummary;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.features.storybook.web.dto.StorybookSummary;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class StorybookService {

    private static final Set<Integer> PAGE_COUNTS = Set.of(10, 12, 15, 18, 20);

    /** Every character costs a sheet and a reference image on each page it appears in, so the cast is capped. */
    static final int MAX_CHARACTERS = 4;

    private final ChildProfileService children;
    private final BlueprintCatalog blueprints;
    private final ModerationService moderation;
    private final AiCallLedger ledger;
    private final StorybookDraftWriter writer;
    private final StorybookViewMapper mapper;
    private final StorybookAccessGuard guard;
    private final StorybookRepository books;
    private final StorybookPageRepository pages;
    private final StorybookCharacterRepository characters;
    private final com.doova.ktab.features.storybook.config.StorybookProperties properties;

    /** Deliberately not @Transactional: moderation calls an LLM and must not hold a DB connection. */
    public StorybookDetail create(User owner, CreateStorybookRequest r) {
        long recent = books.countByOwner_IdAndCreatedAtAfter(owner.getId(), java.time.LocalDateTime.now().minusDays(1));
        if (recent >= properties.getLimits().getDraftsPerUserPerDay()) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_LIMIT_REACHED);
        }

        ChildProfile child = children.requireOwned(owner, r.childProfileId());

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

        Blueprint blueprint;
        if (r.blueprintKey() == null || r.blueprintKey().isBlank()) {
            blueprint = Blueprint.custom();
        } else {
            try {
                blueprint = blueprints.get(r.blueprintKey());
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(ApiMessageKey.STORYBOOK_BLUEPRINT_NOT_ALLOWED);
            }
        }
        if (!blueprint.ageBands().contains(child.getAgeBand())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_BLUEPRINT_NOT_ALLOWED);
        }
        if (r.setting() != null && !blueprint.allowedSettings().contains(r.setting())) {
            throw new BadRequestException(ApiMessageKey.STORYBOOK_SETTING_NOT_ALLOWED);
        }

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

        StoryInputs inputs = new StoryInputs(child.getNameAr(), child.getGender(), child.getAgeBand(),
                child.getAppearance(), interests, r.companion(),
                r.setting(), r.timeOfDay(), r.place(), blueprint);
        Storybook book = writer.insertDraft(owner, child, inputs,
                new StorybookDraftWriter.ResolvedSettings(r, variety, tashkeel, dedication));
        return detail(owner, book.getId());
    }

    @Transactional(readOnly = true)
    public List<StorybookSummary> list(User owner) {
        return books.findByOwner_IdOrderByCreatedAtDesc(owner.getId()).stream()
                .map(b -> new StorybookSummary(b.getId(), b.getTitleAr(), b.getInputs().childNameAr(),
                        b.getStatus(), b.getPageCount(), b.getCreatedAt()))
                .toList();
    }

    @Transactional(readOnly = true)
    public StorybookDetail detail(User owner, Long bookId) {
        Storybook book = guard.requireOwned(bookId, owner);
        return mapper.toDetail(book, pages.findByStorybook_IdOrderByPageIndexAsc(bookId),
                characters.findByStorybook_IdAndKind(bookId, CharacterKind.CHILD).orElse(null));
    }

    public List<BlueprintSummary> blueprints(AgeBand band) {
        return blueprints.forAgeBand(band).stream().map(BlueprintSummary::from).toList();
    }
}
