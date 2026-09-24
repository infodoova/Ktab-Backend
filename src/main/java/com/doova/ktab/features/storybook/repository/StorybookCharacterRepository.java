package com.doova.ktab.features.storybook.repository;

import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface StorybookCharacterRepository extends JpaRepository<StorybookCharacter, Long> {
    Optional<StorybookCharacter> findByStorybook_IdAndKind(Long storybookId, CharacterKind kind);
    List<StorybookCharacter> findByStorybook_Id(Long storybookId);
}
