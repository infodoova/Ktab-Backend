package com.doova.ktab.features.story.service;

import com.doova.ktab.features.story.dto.CreateStoryRequest;
import com.doova.ktab.features.story.dto.UpdateStoryRequest;
import com.doova.ktab.features.story.model.Story;
import com.doova.ktab.model.user.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;

public interface StoryService {

    Story createStory(CreateStoryRequest request, MultipartFile coverImage, User author);

    String getCoverImageUrl(Long storyId);

    Story getStoryById(Long id);

    Page<Story> getAllStoriesPaged(Pageable pageable);

    Page<Story> getStoriesByAuthor(Long authorId, Pageable pageable);

    Story updateStory(Long storyId, UpdateStoryRequest request, MultipartFile coverImage, User author);

    void deleteStory(Long storyId, User author);
}
