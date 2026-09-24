package com.doova.ktab.features.storybook.web;

import com.doova.ktab.enums.book.UrlStrategy;
import com.doova.ktab.features.storybook.config.StorybookProperties;
import com.doova.ktab.features.storybook.enums.*;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.model.StorybookPage;
import com.doova.ktab.features.storybook.model.StorybookPageImage;
import com.doova.ktab.features.storybook.support.StoryFixtures;
import com.doova.ktab.features.storybook.web.dto.StorybookDetail;
import com.doova.ktab.service.file.FileStorageService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class StorybookViewMapperTest {

    private final FileStorageService storage = mock(FileStorageService.class);
    private final StorybookViewMapper mapper = new StorybookViewMapper(storage, new StorybookProperties());

    @Test
    void appliesTheTashkeelLevelAndSignsImageUrls() {
        Storybook book = new Storybook();
        book.setId(1L);
        book.setStatus(StorybookStatus.STORY_READY);
        book.setVariety(LanguageVariety.MSA);
        book.setTashkeelLevel(TashkeelLevel.NONE);
        book.setPageCount(10);
        book.setInputs(new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, StoryFixtures.APPEARANCE,
                List.of(), null, null, StoryFixtures.CATALOG.get("first-day-of-school")));
        book.setLookRegenerations(1);

        StorybookPage page = new StorybookPage();
        page.setPageIndex(1);
        page.setKind(PageKind.STORY);
        page.setTextAr("ذَهَبَ سامي.");
        page.setTextZone(TextZone.BOTTOM);
        StorybookPageImage image = new StorybookPageImage();
        image.setImageKey("k1");
        page.setCurrentImage(image);
        when(storage.getFileUrl("k1", UrlStrategy.SIGNED)).thenReturn("https://signed/k1");

        StorybookDetail detail = mapper.toDetail(book, List.of(page), null);

        assertThat(detail.pages()).singleElement().satisfies(p -> {
            assertThat(p.textAr()).isEqualTo("ذهب سامي.");
            assertThat(p.imageUrl()).isEqualTo("https://signed/k1");
        });
        assertThat(detail.lookRegenerationsLeft()).isEqualTo(1);
        assertThat(detail.pageRegenerationsLeft()).isEqualTo(3);
        assertThat(detail.characterSheetUrl()).isNull();
    }
}
