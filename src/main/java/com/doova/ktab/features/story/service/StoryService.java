package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.CreateStoryRequest;
import com.doova.ktab.features.story.dto.StoryResponse;
import com.doova.ktab.features.story.dto.UpdateStoryRequest;
import com.doova.ktab.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface StoryService {

    StoryResponse createStory(CreateStoryRequest request, MultipartFile coverImage, User author);

    String getCoverImageUrl(Long storyId);

    StoryResponse getStoryById(Long id);

    Page<StoryResponse> getAllStoriesPaged(Pageable pageable);

    Page<StoryResponse> getStoriesByAuthor(Long authorId, Pageable pageable);

    StoryResponse updateStory(Long storyId, UpdateStoryRequest request, MultipartFile coverImage, User author);

    void deleteStory(Long storyId, User author);
}
