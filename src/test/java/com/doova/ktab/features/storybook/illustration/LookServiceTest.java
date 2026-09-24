package com.doova.ktab.features.storybook.illustration;

import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.CharacterKind;
import com.doova.ktab.features.storybook.enums.CharacterSheetStatus;
import com.doova.ktab.features.storybook.enums.JobStep;
import com.doova.ktab.features.storybook.enums.StorybookStatus;
import com.doova.ktab.features.storybook.exception.StorybookStateConflictException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookCharacter;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.orchestrator.JobEnqueuer;
import com.doova.ktab.features.storybook.orchestrator.StorybookStateMachine;
import com.doova.ktab.features.storybook.repository.StorybookCharacterRepository;
import com.doova.ktab.features.storybook.repository.StorybookPageRepository;
import com.doova.ktab.features.storybook.web.StorybookAccessGuard;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LookServiceTest {

    @Mock
    private StorybookAccessGuard guard;
    @Mock
    private StorybookCharacterRepository characters;
    @Mock
    private StorybookPageRepository pages;
    @Mock
    private JobEnqueuer enqueuer;

    private StorybookStateMachine stateMachine;
    private StorybookProperties properties;
    private LookService lookService;
    private User owner;

    @BeforeEach
    void setUp() {
        stateMachine = new StorybookStateMachine();
        properties = new StorybookProperties();
        lookService = new LookService(guard, characters, pages, enqueuer, stateMachine, properties);
        owner = new User();
        owner.setId(1L);
    }

    private Storybook readyBook() {
        Storybook b = new Storybook();
        b.setId(10L);
        b.setStatus(StorybookStatus.CHARACTER_READY);
        b.setLookRegenerations(0);
        return b;
    }

    private StorybookCharacter readyChild(Storybook b) {
        StorybookCharacter c = new StorybookCharacter();
        c.setStorybook(b);
        c.setKind(CharacterKind.CHILD);
        c.setSheetStatus(CharacterSheetStatus.GENERATED);
        c.setSheetVersion(1);
        return c;
    }

    @Test
    void regenerate_notCharacterReady_throwsConflict() {
        Storybook b = new Storybook();
        b.setId(10L);
        b.setStatus(StorybookStatus.DRAFT);
        when(guard.requireOwned(10L, owner)).thenReturn(b);
        StorybookCharacter c = readyChild(b);
        when(characters.findByStorybook_IdAndKind(10L, CharacterKind.CHILD)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> lookService.regenerate(owner, 10L))
                .isInstanceOf(StorybookStateConflictException.class);
    }

    @Test
    void regenerate_reachesLimit_throwsBadRequest() {
        Storybook b = readyBook();
        b.setLookRegenerations(2);
        when(guard.requireOwned(10L, owner)).thenReturn(b);
        StorybookCharacter c = readyChild(b);
        c.setSheetVersion(3); // version matches lookRegenerations + 1
        when(characters.findByStorybook_IdAndKind(10L, CharacterKind.CHILD)).thenReturn(Optional.of(c));

        assertThatThrownBy(() -> lookService.regenerate(owner, 10L))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void regenerate_success_incrementsCountAndEnqueuesSheet() {
        Storybook b = readyBook();
        when(guard.requireOwned(10L, owner)).thenReturn(b);
        StorybookCharacter c = readyChild(b);
        when(characters.findByStorybook_IdAndKind(10L, CharacterKind.CHILD)).thenReturn(Optional.of(c));

        lookService.regenerate(owner, 10L);

        assertThat(b.getLookRegenerations()).isEqualTo(1);
        verify(enqueuer).enqueue(10L, JobStep.CHARACTER_SHEET, -1, 2);
    }

    @Test
    void approve_success_transitionsToIllustratingAndEnqueuesPages() {
        Storybook b = readyBook();
        when(guard.requireOwned(10L, owner)).thenReturn(b);
        StorybookCharacter c = readyChild(b);
        when(characters.findByStorybook_IdAndKind(10L, CharacterKind.CHILD)).thenReturn(Optional.of(c));

        StorybookPage p0 = new StorybookPage();
        p0.setPageIndex(0);
        StorybookPage p1 = new StorybookPage();
        p1.setPageIndex(1);
        when(pages.findByStorybook_IdOrderByPageIndexAsc(10L)).thenReturn(List.of(p0, p1));

        lookService.approve(owner, 10L);

        assertThat(b.getStatus()).isEqualTo(StorybookStatus.ILLUSTRATING);
        assertThat(b.getLookApprovedAt()).isNotNull();
        assertThat(c.getSheetStatus()).isEqualTo(CharacterSheetStatus.APPROVED);
        assertThat(p0.getGeneration()).isEqualTo(1);
        assertThat(p0.getRoundStartGeneration()).isEqualTo(1);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 0, 1);
        verify(enqueuer).enqueue(10L, JobStep.ILLUSTRATE_PAGE, 1, 1);
    }
}
