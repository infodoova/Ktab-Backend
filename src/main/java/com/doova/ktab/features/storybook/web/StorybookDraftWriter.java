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
            for (com.doova.ktab.features.storybook.web.dto.CharacterInput ci : settings.request().characters()) {
                StorybookCharacter sc = new StorybookCharacter();
                sc.setStorybook(book);
                CharacterKind kind;
                if (!hasChild && !"COMPANION".equalsIgnoreCase(ci.type()) && !"COMPANION".equalsIgnoreCase(ci.role())) {
                    kind = CharacterKind.CHILD;
                    hasChild = true;
                } else {
                    kind = CharacterKind.COMPANION;
                }
                sc.setKind(kind);
                sc.setCharacterId(ci.id() != null ? ci.id() : (ci.name() != null ? ci.name().toLowerCase() : (kind == CharacterKind.CHILD ? "child" : "companion")));
                sc.setCharacterType(ci.type() != null ? ci.type() : (kind == CharacterKind.CHILD ? "HUMAN" : "ANIMAL"));
                sc.setRole(ci.role() != null ? ci.role() : (kind == CharacterKind.CHILD ? "MAIN" : "COMPANION"));
                sc.setRelationship(ci.relationship());
                sc.setClothing(ci.clothes());
                sc.setPersonality(ci.personality());
                if (kind == CharacterKind.CHILD) {
                    sc.setAttributes(ci.appearance() != null ? CharacterAttributes.ofChild(ci.appearance()) : CharacterAttributes.ofChild(inputs.appearance()));
                } else {
                    sc.setAttributes(inputs.companion() != null ? CharacterAttributes.ofCompanion(inputs.companion()) : CharacterAttributes.ofChild(inputs.appearance()));
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

    public record ResolvedSettings(CreateStorybookRequest request,
                                   com.doova.ktab.features.storybook.enums.LanguageVariety variety,
                                   com.doova.ktab.features.storybook.enums.TashkeelLevel tashkeelLevel,
                                   String dedication) {
    }
}
