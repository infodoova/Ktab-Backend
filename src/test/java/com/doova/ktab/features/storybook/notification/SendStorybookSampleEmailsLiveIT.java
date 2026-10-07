package com.doova.ktab.features.storybook.notification;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.dto.mail.EmailResult;
import com.doova.ktab.service.email.EmailService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

@Disabled("Manual live verification test - run on demand")
@SpringBootTest
class SendStorybookSampleEmailsLiveIT {

    @Autowired
    private EmailService emailService;

    @Test
    @DisplayName("Send live sample storybook emails to alhassan.khalilnew@gmail.com")
    void sendAllStorybookSampleEmails() {
        String recipient = "alhassan.khalilnew@gmail.com";
        String recipientName = "الحسن خليل";
        String childName = "ليلى";
        String bookTitle = "خَرِيطَةُ النُّجُومِ وَمَرْصَدُ عَمَّانَ";
        String bookId = "35";
        String baseUrl = "https://ktab-rho.vercel.app/storybook/books/" + bookId;

        // 1. Storybook Created (Onboarding & Roadmap)
        EmailRequest req1 = EmailRequest.builder()
                .to(recipient)
                .subject("بدأت رحلة كتاب " + childName + ": ماذا سيحدث الآن؟")
                .template("storybook-created")
                .variable("NAME", recipientName)
                .variable("CHILD_NAME", childName)
                .variable("BOOK_ID", bookId)
                .variable("PAGE_COUNT", "16")
                .variable("BOOK_URL", baseUrl)
                .variable("SUPPORT_EMAIL", "support@ktab.app")
                .build();
        EmailResult res1 = emailService.sendSync(req1);
        System.out.println(">>> Email 1 (Created) -> Success: " + res1.success() + ", MessageId: " + res1.messageId() + ", Error: " + res1.errorMessage());

        // 2. Story Script Ready (Human-in-the-Loop #1)
        EmailRequest req2 = EmailRequest.builder()
                .to(recipient)
                .subject("قصة «" + bookTitle + "» جاهزة لمراجعتك واعتمادك")
                .template("storybook-story-ready")
                .variable("NAME", recipientName)
                .variable("CHILD_NAME", childName)
                .variable("BOOK_TITLE", bookTitle)
                .variable("BOOK_URL", baseUrl)
                .variable("SUPPORT_EMAIL", "support@ktab.app")
                .build();
        EmailResult res2 = emailService.sendSync(req2);
        System.out.println(">>> Email 2 (Story Ready - HITL #1) -> Success: " + res2.success() + ", MessageId: " + res2.messageId() + ", Error: " + res2.errorMessage());

        // 3. Character Look Sheet Ready (Human-in-the-Loop #2)
        EmailRequest req3 = EmailRequest.builder()
                .to(recipient)
                .subject("لوحة رسم شخصية " + childName + " جاهزة للاعتماد")
                .template("storybook-character-ready")
                .variable("NAME", recipientName)
                .variable("CHILD_NAME", childName)
                .variable("BOOK_TITLE", bookTitle)
                .variable("BOOK_URL", baseUrl)
                .variable("SUPPORT_EMAIL", "support@ktab.app")
                .build();
        EmailResult res3 = emailService.sendSync(req3);
        System.out.println(">>> Email 3 (Character Look Ready - HITL #2) -> Success: " + res3.success() + ", MessageId: " + res3.messageId() + ", Error: " + res3.errorMessage());

        // 4. Storybook Completed (Delivery)
        EmailRequest req4 = EmailRequest.builder()
                .to(recipient)
                .subject("تهانينا! كتاب «" + bookTitle + "» مكتمل وجاهز للقراءة الآن")
                .template("storybook-completed")
                .variable("NAME", recipientName)
                .variable("CHILD_NAME", childName)
                .variable("BOOK_TITLE", bookTitle)
                .variable("READER_URL", baseUrl + "/reader")
                .variable("DOWNLOAD_URL", baseUrl + "/download")
                .variable("BOOK_URL", baseUrl)
                .variable("SUPPORT_EMAIL", "support@ktab.app")
                .build();
        EmailResult res4 = emailService.sendSync(req4);
        System.out.println(">>> Email 4 (Completed) -> Success: " + res4.success() + ", MessageId: " + res4.messageId() + ", Error: " + res4.errorMessage());

        assertThat(res1.success()).isTrue();
        assertThat(res2.success()).isTrue();
        assertThat(res3.success()).isTrue();
        assertThat(res4.success()).isTrue();
    }
}
