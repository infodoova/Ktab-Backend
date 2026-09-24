package com.doova.ktab.features.storybook.storage;

import com.doova.ktab.features.storybook.repository.StorybookRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.mockito.Mockito.*;

class StorybookOrphanSweeperTest {

    @Test
    void deletesOnlyPrefixesWithoutABook() {
        StorybookAssetStore store = mock(StorybookAssetStore.class);
        StorybookRepository books = mock(StorybookRepository.class);
        when(store.listBookIds()).thenReturn(Set.of(1L, 2L, 3L));
        when(books.findAllById(Set.of(1L, 2L, 3L))).thenReturn(List.of(bookWithId(2L)));

        new StorybookOrphanSweeper(store, books).sweep();

        verify(store).deletePrefix("storybook/1/");
        verify(store).deletePrefix("storybook/3/");
        verify(store, never()).deletePrefix("storybook/2/");
    }

    private static com.doova.ktab.features.storybook.model.Storybook bookWithId(Long id) {
        var b = new com.doova.ktab.features.storybook.model.Storybook();
        b.setId(id);
        return b;
    }
}
