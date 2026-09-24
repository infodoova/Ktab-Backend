package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.repository.ChildProfileRepository;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.ChildProfileResponse;
import com.doova.ktab.features.storybook.web.dto.CreateChildProfileRequest;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChildProfileServiceTest {

    @Mock ChildProfileRepository repository;
    @InjectMocks ChildProfileService service;

    private User owner;

    @BeforeEach
    void setUp() {
        owner = new User();
        owner.setId(7L);
    }

    @Test
    void createStoresTheNameExactlyAsTyped() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        ChildProfileResponse r = service.create(owner, new CreateChildProfileRequest(
                "  مُحَمَّد ", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE));
        assertThat(r.nameAr()).isEqualTo("مُحَمَّد"); // trimmed, tashkeel kept
    }

    @Test
    void otherUsersProfileIsNotFound() {
        when(repository.findByIdAndOwner_Id(99L, 7L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.requireOwned(owner, 99L)).isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesOnlyAnOwnedProfile() {
        ChildProfile p = new ChildProfile();
        when(repository.findByIdAndOwner_Id(5L, 7L)).thenReturn(Optional.of(p));
        service.delete(owner, 5L);
        verify(repository).delete(p);
    }
}
