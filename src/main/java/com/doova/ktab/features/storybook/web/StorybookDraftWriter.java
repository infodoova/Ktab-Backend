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
        books.save(book);

        StorybookCharacter childCharacter = new StorybookCharacter();
        childCharacter.setStorybook(book);
        childCharacter.setKind(CharacterKind.CHILD);
        childCharacter.setAttributes(CharacterAttributes.ofChild(inputs.appearance()));
        characters.save(childCharacter);

        if (inputs.companion() != null) {
            StorybookCharacter companion = new StorybookCharacter();
            companion.setStorybook(book);
            companion.setKind(CharacterKind.COMPANION);
            companion.setAttributes(CharacterAttributes.ofCompanion(inputs.companion()));
            characters.save(companion);
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
