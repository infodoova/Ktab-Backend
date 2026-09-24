package com.doova.ktab.features.storybook.web;

import com.doova.ktab.exception.ResourceNotFoundException;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorybookAccessGuardTest {

    private final StorybookRepository repository = mock(StorybookRepository.class);
    private final StorybookAccessGuard guard = new StorybookAccessGuard(repository);

    private static User user(long id) {
        User u = new User();
        u.setId(id);
        return u;
    }

    @Test
    void ownerGetsTheBook() {
        Storybook book = new Storybook();
        when(repository.findByIdAndOwner_Id(10L, 1L)).thenReturn(Optional.of(book));
        assertThat(guard.requireOwned(10L, user(1))).isSameAs(book);
    }

    @Test
    void someoneElsesBookLooksLikeItDoesNotExist() {
        when(repository.findByIdAndOwner_Id(10L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> guard.requireOwned(10L, user(2)))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("STORYBOOK_NOT_FOUND");
    }
}
