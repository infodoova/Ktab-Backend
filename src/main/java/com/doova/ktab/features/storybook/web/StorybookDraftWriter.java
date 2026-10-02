package com.doova.ktab.features.storybook.web;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.model.CharacterAttributes;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.features.storybook.web.dto.CreateStorybookRequest;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class StorybookDraftWriter {

    private final StorybookRepository books;
    private final StorybookCharacterRepository characters;
    private final ApplicationEventPublisher events;

    /** @param settings the validated request, with {@code tashkeelLevel} and {@code variety} already resolved */
    @Transactional
    public Storybook insertDraft(User owner, ChildProfile child, StoryInputs inputs, ResolvedSettings settings) {
        Storybook book = new Storybook();
        book.setOwner(owner);
        book.setChildProfile(child);
        book.setInputs(inputs);
        book.setBlueprintKey(inputs.blueprint().key());
        book.setBlueprintVersion(inputs.blueprint().version());
        book.setStyle(settings.request().style());
        book.setVariety(settings.variety());
        book.setTashkeelLevel(settings.tashkeelLevel());
        book.setPageCount(settings.request().pageCount());
        book.setDedication(settings.dedication());
        book.setStatus(StorybookStatus.DRAFT);
        book.setTheme(settings.request().theme());
        book.setStoryTone(settings.request().storyTone());
        book.setLesson(settings.request().lesson());
        book.setStoryIdea(settings.request().storyIdea());
        book.setThingsToAvoid(settings.request().thingsToAvoid());
        if (settings.request().orientation() != null) {
            book.setOrientation(settings.request().orientation());
        }
        books.save(book);

        if (settings.request().characters() != null && !settings.request().characters().isEmpty()) {
            boolean hasChild = false;
            boolean hasCompanion = false;
            java.util.Set<String> usedIds = new java.util.HashSet<>();
            for (com.doova.ktab.features.storybook.web.dto.CharacterInput ci : settings.request().characters()) {
                StorybookCharacter sc = new StorybookCharacter();
                sc.setStorybook(book);
                CharacterKind kind;
                if (!hasChild && !"COMPANION".equalsIgnoreCase(ci.type()) && !"COMPANION".equalsIgnoreCase(ci.role())) {
                    kind = CharacterKind.CHILD;
                    hasChild = true;
                } else if (!hasCompanion) {
                    kind = CharacterKind.COMPANION;
                    hasCompanion = true;
                } else {
                    kind = CharacterKind.SUPPORTING; // a grandparent, a friend: drawn from its own sheet
                }
                sc.setKind(kind);
                sc.setCharacterId(uniqueId(ci, kind, usedIds));
                sc.setCharacterType(ci.type() != null ? ci.type() : (kind == CharacterKind.CHILD ? "HUMAN" : "ANIMAL"));
                sc.setRole(ci.role() != null ? ci.role() : (kind == CharacterKind.CHILD ? "MAIN" : "COMPANION"));
                sc.setRelationship(ci.relationship());
                sc.setClothing(ci.clothes());
                sc.setPersonality(ci.personality());
                sc.setAdvancedDetails(details(ci));
                if (kind == CharacterKind.CHILD) {
                    sc.setAttributes(ci.appearance() != null ? CharacterAttributes.ofChild(ci.appearance()) : CharacterAttributes.ofChild(inputs.appearance()));
                } else if (kind == CharacterKind.COMPANION) {
                    sc.setAttributes(inputs.companion() != null ? CharacterAttributes.ofCompanion(inputs.companion()) : CharacterAttributes.ofChild(inputs.appearance()));
                } else {
                    sc.setAttributes(ci.appearance() != null ? CharacterAttributes.ofChild(ci.appearance()) : new CharacterAttributes(null, null));
                }
                characters.save(sc);
            }
        } else {
            StorybookCharacter childCharacter = new StorybookCharacter();
            childCharacter.setStorybook(book);
            childCharacter.setKind(CharacterKind.CHILD);
            childCharacter.setCharacterId("child");
            childCharacter.setAttributes(CharacterAttributes.ofChild(inputs.appearance()));
            characters.save(childCharacter);

            if (inputs.companion() != null) {
                StorybookCharacter companion = new StorybookCharacter();
                companion.setStorybook(book);
                companion.setKind(CharacterKind.COMPANION);
                companion.setCharacterId("companion");
                companion.setAttributes(CharacterAttributes.ofCompanion(inputs.companion()));
                characters.save(companion);
            }
        }

        events.publishEvent(new StorybookCreatedEvent(book.getId()));
        return book;
    }

    private static String uniqueId(com.doova.ktab.features.storybook.web.dto.CharacterInput ci, CharacterKind kind, java.util.Set<String> used) {
        String base = ci.id() != null ? ci.id()
                : ci.name() != null ? ci.name().toLowerCase()
                : kind == CharacterKind.CHILD ? "child" : kind == CharacterKind.COMPANION ? "companion" : "supporting";
        String id = base;
        for (int n = 2; !used.add(id); n++) {
            id = base + "-" + n;
        }
        return id;
    }

    /** The parts of a character the parent described that have no column of their own; the sheet and the story use them. */
    private static java.util.Map<String, Object> details(com.doova.ktab.features.storybook.web.dto.CharacterInput ci) {
        java.util.Map<String, Object> d = new java.util.LinkedHashMap<>();
        putIfPresent(d, "name", ci.name());
        putIfPresent(d, "age", ci.age());
        putIfPresent(d, "gender", ci.gender() == null ? null : ci.gender().name());
        putIfPresent(d, "strength", ci.strength());
        putIfPresent(d, "weakness", ci.weakness());
        putIfPresent(d, "favoriteActivity", ci.favoriteActivity());
        putIfPresent(d, "signatureItem", ci.signatureItem());
        putIfPresent(d, "speakingStyle", ci.speakingStyle());
        return d.isEmpty() ? null : d;
    }

    private static void putIfPresent(java.util.Map<String, Object> d, String key, Object value) {
        if (value != null && !(value instanceof String str && str.isBlank())) {
            d.put(key, value);
        }
    }

    public record ResolvedSettings(CreateStorybookRequest request,
                                   com.doova.ktab.features.storybook.enums.LanguageVariety variety,
                                   com.doova.ktab.features.storybook.enums.TashkeelLevel tashkeelLevel,
                                   String dedication) {
    }
}
