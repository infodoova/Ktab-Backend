package com.doova.ktab.service.publisher;

import com.doova.ktab.dto.user.AssignPublisherRequest;
import com.doova.ktab.dto.user.UpdatePublisherRequest;
import com.doova.ktab.dto.user.UserResponseDto;
import com.doova.ktab.utils.pagination.PageResponse;

public interface PublisherAdminService {

    PageResponse<UserResponseDto> getAllPublishers(int page, int size, String search);

    void assignPublisher(AssignPublisherRequest req);

    UserResponseDto updatePublisher(Long userId, UpdatePublisherRequest req);

    void removePublisher(Long userId);
}

