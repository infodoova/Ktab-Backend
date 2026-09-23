package com.doova.ktab.service.publisher.impl;

import com.doova.ktab.dto.user.AssignPublisherRequest;
import com.doova.ktab.dto.user.UpdatePublisherRequest;
import com.doova.ktab.dto.user.UserResponseDto;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.BadRequestException;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.publisher.PublisherAdminService;
import com.doova.ktab.utils.pagination.PageResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PublisherAdminServiceImpl implements PublisherAdminService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<UserResponseDto> getAllPublishers(int page, int size, String search) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<User> pageResult;
        if (search != null && !search.isBlank()) {
            pageResult = userRepository.searchByRole(UserRole.PUBLISHER.getCode(), search.trim(), pageable);
        } else {
            pageResult = userRepository.findByRole(UserRole.PUBLISHER.getCode(), pageable);
        }
        return PageResponse.fromPage(pageResult.map(UserResponseDto::from));
    }

    @Override
    @Transactional
    public void assignPublisher(AssignPublisherRequest req) {
        if (userRepository.existsByEmail(req.email())) {
            log.warn("Cannot create publisher: email {} is already in use", req.email());
            throw new BadRequestException(ApiMessageKey.AUTH_EMAIL_ALREADY_USED);
        }

        User newUser = User.builder()
                .email(req.email())
                .firstName(req.firstName())
                .middleName(req.middleName())
                .lastName(req.lastName())
                .role(UserRole.PUBLISHER.getCode())
                .active(Status.ACTIVE.getCode())
                .build();

        newUser.setPasswordAndDigest(req.password(), passwordEncoder);
        userRepository.save(newUser);
        log.info("Assigned user {} as PUBLISHER", newUser.getEmail());
    }

    @Override
    @Transactional
    public UserResponseDto updatePublisher(Long userId, UpdatePublisherRequest req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));

        if (!UserRole.PUBLISHER.getCode().equals(user.getRole())) {
            throw new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND);
        }

        user.setEmail(req.email());
        user.setFirstName(req.firstName());
        user.setMiddleName(req.middleName());
        user.setLastName(req.lastName());

        if (req.password() != null && !req.password().isBlank()) {
            user.setPasswordAndDigest(req.password(), passwordEncoder);
        }

        userRepository.save(user);
        log.info("Updated publisher id={} email={}", userId, user.getEmail());
        return UserResponseDto.from(user);
    }

    @Override
    @Transactional
    public void removePublisher(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException(ApiMessageKey.USER_NOT_FOUND));

        if (UserRole.PUBLISHER.getCode().equals(user.getRole())) {
            userRepository.delete(user);
            log.info("Deleted publisher {}", user.getEmail());
        }
    }
}
