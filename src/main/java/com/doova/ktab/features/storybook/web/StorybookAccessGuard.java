package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StorybookAccessGuard {

    private final StorybookRepository repository;

    public Storybook requireOwned(Long bookId, User user) {
        return repository.findByIdAndOwner_Id(bookId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.STORYBOOK_NOT_FOUND));
    }
}
