package com.doova.ktab.features.storybook.notification;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.features.storybook.blueprint.Blueprint;
import com.doova.ktab.features.storybook.character.ChildAppearance;
import com.doova.ktab.features.storybook.enums.AgeBand;
import com.doova.ktab.features.storybook.enums.ChildGender;
import com.doova.ktab.features.storybook.character.ChildAppearance.EyeColor;
import com.doova.ktab.features.storybook.character.ChildAppearance.HairColor;
import com.doova.ktab.features.storybook.character.ChildAppearance.HairStyle;
import com.doova.ktab.features.storybook.character.ChildAppearance.SkinTone;
import com.doova.ktab.features.storybook.event.StorybookCharacterReadyEvent;
import com.doova.ktab.features.storybook.event.StorybookCompletedEvent;
import com.doova.ktab.features.storybook.event.StorybookCreatedEvent;
import com.doova.ktab.features.storybook.event.StorybookStoryReadyEvent;
import com.doova.ktab.features.storybook.model.ChildProfile;
import com.doova.ktab.features.storybook.model.StoryInputs;
import com.doova.ktab.features.storybook.model.Storybook;
import com.doova.ktab.features.storybook.repository.StorybookRepository;
import com.doova.ktab.model.user.User;
import com.doova.ktab.service.email.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class StorybookNotificationListenerTest {

    @Mock
    private EmailService emailService;

    @Mock
    private StorybookRepository storybookRepository;

    @InjectMocks
    private StorybookNotificationListener listener;

    private User testUser;
    private Storybook testBook;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(listener, "frontendUrl", "https://ktab-rho.vercel.app");

        testUser = new User();
        testUser.setFirstName("أحمد");
        testUser.setLastName("المنصوري");
        testUser.setEmail("parent@example.com");

        ChildAppearance appearance = new ChildAppearance(SkinTone.OLIVE, HairColor.BLACK, HairStyle.SHORT_CURLY, EyeColor.BROWN, false, false);
        ChildProfile childProfile = new ChildProfile();
        childProfile.setNameAr("سامي");

        StoryInputs inputs = new StoryInputs("سامي", ChildGender.BOY, AgeBand.AGE_6_8, appearance,
                List.of(), null, null, null, null, Blueprint.custom());

        testBook = new Storybook();
        testBook.setId(42L);
        testBook.setOwner(testUser);
        testBook.setChildProfile(childProfile);
        testBook.setInputs(inputs);
        testBook.setPageCount(16);
        testBook.setTitleAr("سامي والبوصلة العجيبة");
    }

    @Test
    @DisplayName("onStorybookCreated dispatches onboarding email with journey roadmap")
    void onStorybookCreated_validStorybook_dispatchesCreatedEmailWithCorrectVariables() {
        when(storybookRepository.findByIdWithOwner(42L)).thenReturn(Optional.of(testBook));

        listener.onStorybookCreated(new StorybookCreatedEvent(42L));

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.to()).containsExactly("parent@example.com");
        assertThat(req.templateName()).isEqualTo("storybook-created");
        assertThat(req.subject()).contains("سامي");
        assertThat(req.templateVariables().get("NAME")).isEqualTo("أحمد المنصوري");
        assertThat(req.templateVariables().get("CHILD_NAME")).isEqualTo("سامي");
        assertThat(req.templateVariables().get("BOOK_ID")).isEqualTo("42");
        assertThat(req.templateVariables().get("BOOK_URL")).isEqualTo("https://ktab-rho.vercel.app/storybook/books/42");
    }

    @Test
    @DisplayName("onStoryReady dispatches Human-in-the-Loop #1 story approval email")
    void onStoryReady_validStorybook_dispatchesStoryReadyEmailWithApprovalLink() {
        when(storybookRepository.findByIdWithOwner(42L)).thenReturn(Optional.of(testBook));

        listener.onStoryReady(new StorybookStoryReadyEvent(42L));

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.to()).containsExactly("parent@example.com");
        assertThat(req.templateName()).isEqualTo("storybook-story-ready");
        assertThat(req.subject()).contains("سامي والبوصلة العجيبة");
        assertThat(req.templateVariables().get("BOOK_TITLE")).isEqualTo("سامي والبوصلة العجيبة");
        assertThat(req.templateVariables().get("CHILD_NAME")).isEqualTo("سامي");
        assertThat(req.templateVariables().get("BOOK_URL")).isEqualTo("https://ktab-rho.vercel.app/storybook/books/42");
    }

    @Test
    @DisplayName("onCharacterReady dispatches Human-in-the-Loop #2 character sheet approval email")
    void onCharacterReady_validStorybook_dispatchesCharacterReadyEmailWithReviewLink() {
        when(storybookRepository.findByIdWithOwner(42L)).thenReturn(Optional.of(testBook));

        listener.onCharacterReady(new StorybookCharacterReadyEvent(42L));

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.to()).containsExactly("parent@example.com");
        assertThat(req.templateName()).isEqualTo("storybook-character-ready");
        assertThat(req.subject()).contains("سامي");
        assertThat(req.templateVariables().get("CHILD_NAME")).isEqualTo("سامي");
        assertThat(req.templateVariables().get("BOOK_URL")).isEqualTo("https://ktab-rho.vercel.app/storybook/books/42");
    }

    @Test
    @DisplayName("onStorybookCompleted dispatches completion email with reader and PDF download links")
    void onStorybookCompleted_validStorybook_dispatchesCompletionEmailWithReaderAndDownloadLinks() {
        when(storybookRepository.findByIdWithOwner(42L)).thenReturn(Optional.of(testBook));

        listener.onStorybookCompleted(new StorybookCompletedEvent(42L));

        ArgumentCaptor<EmailRequest> captor = ArgumentCaptor.forClass(EmailRequest.class);
        verify(emailService).sendSync(captor.capture());

        EmailRequest req = captor.getValue();
        assertThat(req.to()).containsExactly("parent@example.com");
        assertThat(req.templateName()).isEqualTo("storybook-completed");
        assertThat(req.subject()).contains("سامي والبوصلة العجيبة");
        assertThat(req.templateVariables().get("BOOK_TITLE")).isEqualTo("سامي والبوصلة العجيبة");
        assertThat(req.templateVariables().get("READER_URL")).isEqualTo("https://ktab-rho.vercel.app/storybook/books/42/reader");
        assertThat(req.templateVariables().get("DOWNLOAD_URL")).isEqualTo("https://ktab-rho.vercel.app/storybook/books/42/download");
    }

    @Test
    @DisplayName("onStorybookCreated skips dispatch gracefully when owner email is blank")
    void onStorybookCreated_missingRecipientEmail_skipsDispatchGracefully() {
        testUser.setEmail("  ");
        when(storybookRepository.findByIdWithOwner(42L)).thenReturn(Optional.of(testBook));

        listener.onStorybookCreated(new StorybookCreatedEvent(42L));

        verify(emailService, never()).sendSync(any());
    }

    @Test
    @DisplayName("onStorybookCreated skips dispatch gracefully when book is not found")
    void onStorybookCreated_bookNotFound_skipsDispatchGracefully() {
        when(storybookRepository.findByIdWithOwner(999L)).thenReturn(Optional.empty());

        listener.onStorybookCreated(new StorybookCreatedEvent(999L));

        verify(emailService, never()).sendSync(any());
    }
}
