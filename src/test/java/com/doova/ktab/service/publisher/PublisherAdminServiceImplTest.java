package com.doova.ktab.service.publisher;

import com.doova.ktab.dto.user.AssignPublisherRequest;
import com.doova.ktab.enums.status.Status;
import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.enums.user.UserRole;
import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.publisher.impl.PublisherAdminServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PublisherAdminServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private PublisherAdminServiceImpl publisherAdminService;

    private AssignPublisherRequest request;

    @BeforeEach
    void setUp() {
        request = new AssignPublisherRequest(
                "newpublisher@ktab.com",
                "Ahmed",
                "Ali",
                "Mansoor",
                "SecureP@ss123"
        );
    }

    @Test
    @DisplayName("assignPublisher_newUser_createsAndAssignsPublisherRole")
    void assignPublisher_newUser_createsAndAssignsPublisherRole() {
        when(userRepository.findByEmail("newpublisher@ktab.com")).thenReturn(Optional.empty());
        when(passwordEncoder.encode(any())).thenReturn("hashedPassword");

        publisherAdminService.assignPublisher(request);

        verify(userRepository).save(argThat(user ->
                "newpublisher@ktab.com".equals(user.getEmail()) &&
                UserRole.PUBLISHER.getCode().equals(user.getRole())
        ));
    }

    @Test
    @DisplayName("assignPublisher_existingUser_promotesToPublisherRole")
    void assignPublisher_existingUser_promotesToPublisherRole() {
        User existing = new User();
        existing.setId(70L);
        existing.setEmail("newpublisher@ktab.com");
        existing.setRole(UserRole.READER.getCode());

        when(userRepository.findByEmail("newpublisher@ktab.com")).thenReturn(Optional.of(existing));

        publisherAdminService.assignPublisher(request);

        assertThat(existing.getRole()).isEqualTo(UserRole.PUBLISHER.getCode());
        verify(userRepository).save(existing);
    }

    @Test
    @DisplayName("removePublisher_existingPublisher_deletesUser")
    void removePublisher_existingPublisher_deletesUser() {
        User publisher = new User();
        publisher.setId(70L);
        publisher.setEmail("pub@ktab.com");
        publisher.setRole(UserRole.PUBLISHER.getCode());

        when(userRepository.findById(70L)).thenReturn(Optional.of(publisher));

        publisherAdminService.removePublisher(70L);

        verify(userRepository).delete(publisher);
    }

    @Test
    @DisplayName("removePublisher_nonPublisherUser_doesNotDelete")
    void removePublisher_nonPublisherUser_doesNotDelete() {
        User author = new User();
        author.setId(70L);
        author.setEmail("author@ktab.com");
        author.setRole(UserRole.AUTHOR.getCode());

        when(userRepository.findById(70L)).thenReturn(Optional.of(author));

        publisherAdminService.removePublisher(70L);

        verify(userRepository, never()).delete(any());
    }

    @Test
    @DisplayName("removePublisher_userNotFound_throwsResourceNotFoundException")
    void removePublisher_userNotFound_throwsResourceNotFoundException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> publisherAdminService.removePublisher(999L))
                .isInstanceOf(ResourceNotFoundException.class)
                .satisfies(e -> assertThat(((ResourceNotFoundException) e).getMessageKey()).isEqualTo(ApiMessageKey.USER_NOT_FOUND));
    }

    @Test
    @DisplayName("getAllPublishers_returnsPaginatedPublishers")
    void getAllPublishers_returnsPaginatedPublishers() {
        User pub = new User();
        pub.setId(50L);
        pub.setEmail("pub@ktab.com");
        pub.setFirstName("Pub");
        pub.setLastName("Lisher");
        pub.setRole(UserRole.PUBLISHER.getCode());
        pub.setActive(Status.ACTIVE.getCode());

        org.springframework.data.domain.Page<User> page =
                new org.springframework.data.domain.PageImpl<>(java.util.List.of(pub));

        when(userRepository.findByRole(eq(UserRole.PUBLISHER.getCode()), any(org.springframework.data.domain.Pageable.class))).thenReturn(page);

        com.doova.ktab.utils.pagination.PageResponse<com.doova.ktab.dto.user.UserResponseDto> response =
                publisherAdminService.getAllPublishers(0, 10, null);

        assertThat(response).isNotNull();
        assertThat(response.getContent()).hasSize(1);
        assertThat(response.getContent().get(0).id()).isEqualTo(50L);
        assertThat(response.getContent().get(0).email()).isEqualTo("pub@ktab.com");
    }
}
