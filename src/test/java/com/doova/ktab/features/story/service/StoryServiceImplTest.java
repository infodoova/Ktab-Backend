package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.StoryResponse;
import com.doova.ktab.features.story.enums.StoryLens;
import com.doova.ktab.features.story.enums.StoryVisualStyle;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.features.story.model.StoryConstitution;
import com.doova.ktab.features.story.repository.SessionRepository;
import com.doova.ktab.features.story.repository.StoryRepository;
import com.doova.ktab.features.story.service.impl.StoryServiceImpl;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.file.AttachmentService;
import com.doova.ktab.service.file.FileStorageService;
import com.doova.ktab.utils.validator.ImageValidator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StoryServiceImplTest {

    @Mock
    private StoryRepository storyRepository;

    @Mock
    private SessionRepository sessionRepository;

    @Mock
    private AttachmentService attachmentService;

    @Mock
    private FileStorageService fileStorageService;

    @Mock
    private ImageValidator imageValidator;

    @InjectMocks
    private StoryServiceImpl storyService;

    private User testAuthor;
    private Story testStory;

    @BeforeEach
    void setUp() {
        testAuthor = User.builder()
                .email("author@example.com")
                .firstName("Najib")
                .lastName("Mahfouz")
                .role("AUTHOR")
                .build();
        testAuthor.setId(11L);

        StoryConstitution constitution = new StoryConstitution(
                "Modern era", "Cairo", "Freedom", "Philosophical",
                "Existentialism", "Man vs Destiny", "Violence", "Moderate"
        );

        testStory = new Story(
                testAuthor,
                "Cairo Trilogy",
                "Drama",
                10,
                StoryLens.PSYCHOLOGICAL,
                constitution,
                StoryVisualStyle.CINEMATIC_STORYBOOK,
                "Detailed shadows"
        );
        testStory.setId(100L);
    }

    @Test
    @DisplayName("getAllStoriesPaged_validRequest_returnsMappedStoryResponsePageWithAuthor")
    void getAllStoriesPaged_validRequest_returnsMappedStoryResponsePageWithAuthor() {
        Pageable pageable = PageRequest.of(0, 10);
        Page<Story> storyPage = new PageImpl<>(List.of(testStory), pageable, 1);
        when(storyRepository.findAllWithAuthor(pageable)).thenReturn(storyPage);
        when(attachmentService.getAttachment(100L, "Story", "COVER_IMAGE")).thenReturn(Optional.empty());

        Page<StoryResponse> result = storyService.getAllStoriesPaged(pageable);

        assertThat(result).isNotNull();
        assertThat(result.getTotalElements()).isEqualTo(1);
        StoryResponse response = result.getContent().get(0);
        assertThat(response.id()).isEqualTo(100L);
        assertThat(response.title()).isEqualTo("Cairo Trilogy");
        assertThat(response.authorName()).isEqualTo("Najib Mahfouz");

        verify(storyRepository).findAllWithAuthor(pageable);
    }

    @Test
    @DisplayName("getStoryById_existingStory_returnsStoryResponseWithAuthor")
    void getStoryById_existingStory_returnsStoryResponseWithAuthor() {
        when(storyRepository.findWithAuthorById(100L)).thenReturn(Optional.of(testStory));
        when(attachmentService.getAttachment(100L, "Story", "COVER_IMAGE")).thenReturn(Optional.empty());

        StoryResponse result = storyService.getStoryById(100L);

        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(100L);
        assertThat(result.title()).isEqualTo("Cairo Trilogy");
        assertThat(result.authorName()).isEqualTo("Najib Mahfouz");

        verify(storyRepository).findWithAuthorById(100L);
    }
}
