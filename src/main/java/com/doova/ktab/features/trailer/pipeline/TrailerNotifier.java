package com.doova.ktab.features.trailer.pipeline;

import com.doova.ktab.dto.mail.EmailRequest;
import com.doova.ktab.features.trailer.enums.TrailerStatus;
import com.doova.ktab.features.trailer.model.BookTrailer;
import com.doova.ktab.model.book.Book;
import com.doova.ktab.model.user.User;
import com.doova.ktab.repository.book.BookRepository;
import com.doova.ktab.repository.user.UserRepository;
import com.doova.ktab.service.email.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Dispatches completion and status notification emails to the user who requested the trailer.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class TrailerNotifier {

    private final EmailService emailService;
    private final UserRepository users;
    private final BookRepository books;

    @Value("${ktab.app.frontend-url:https://ktab-rho.vercel.app}")
    private String frontendUrl;

    public void notifyCompletion(BookTrailer trailer) {
        if (trailer == null || trailer.getRequestedById() == null) {
            log.debug("Skipping trailer notification: trailer or requestedById is null");
            return;
        }

        Optional<User> userOpt = users.findById(trailer.getRequestedById());
        if (userOpt.isEmpty()) {
            log.info("No user found for requestedById={}, skipping email", trailer.getRequestedById());
            return;
        }

        User user = userOpt.get();
        if (user.getEmail() == null || user.getEmail().isBlank()) {
            log.info("User {} has no email, skipping trailer notification", user.getId());
            return;
        }

        String recipientEmail = user.getEmail();
        String recipientName = user.getFirstName() != null && !user.getFirstName().isBlank()
                ? user.getFirstName()
                : "عزيزنا الكاتب";

        String bookTitle = books.findById(trailer.getBookId())
                .map(Book::getTitle)
                .filter(t -> !t.isBlank())
                .orElse("كتابك");

        String statusStr = trailer.getStatus() != null ? trailer.getStatus().name() : "READY";
        String subject;
        String trailerUrl;

        switch (trailer.getStatus()) {
            case READY -> {
                subject = "إعلانك الترويجي السينمائي جاهز! - " + bookTitle;
                trailerUrl = frontendUrl + "/books/" + trailer.getBookId() + "/trailer";
            }
            case NEEDS_REVIEW -> {
                subject = "إعلانك الترويجي جاهز للمراجعة التحريرية - " + bookTitle;
                trailerUrl = frontendUrl + "/studio/books/" + trailer.getBookId();
            }
            case FAILED -> {
                subject = "تحديث بخصوص إعلان كتابك «" + bookTitle + "»";
                trailerUrl = frontendUrl + "/books/" + trailer.getBookId();
            }
            default -> {
                return;
            }
        }

        String textBody = buildTextFallback(recipientName, bookTitle, trailer.getStatus(), trailer.getError(), trailerUrl);

        try {
            EmailRequest.Builder reqBuilder = EmailRequest.builder()
                    .to(recipientEmail)
                    .subject(subject)
                    .template("trailer-ready")
                    .variable("NAME", recipientName)
                    .variable("BOOK_TITLE", bookTitle)
                    .variable("STATUS", statusStr)
                    .variable("TRAILER_URL", trailerUrl)
                    .variable("SUBJECT", subject)
                    .textBody(textBody);

            if (trailer.getError() != null && !trailer.getError().isBlank()) {
                reqBuilder.variable("ERROR_NOTE", trailer.getError());
            }

            emailService.send(reqBuilder.build());
            log.info("Queued trailer {} notification email to {} for trailerId={}",
                    trailer.getStatus(), recipientEmail, trailer.getId());
        } catch (Exception e) {
            log.warn("Failed to dispatch trailer notification email for trailerId={}: {}",
                    trailer.getId(), e.getMessage(), e);
        }
    }

    private String buildTextFallback(String name, String bookTitle, TrailerStatus status, String error, String url) {
        StringBuilder sb = new StringBuilder();
        sb.append("مرحبًا ").append(name).append("،\n\n");
        if (status == TrailerStatus.READY) {
            sb.append("يسعدنا إعلامك بأن الإعلان الترويجي السينمائي لكتابك «").append(bookTitle)
                    .append("» قد اكتمل إنتاجه بنجاح واجتاز فحص الجودة.\n\n")
                    .append("المواصفات:\n")
                    .append("- المدة: 30 ثانية\n")
                    .append("- الجودة: 1080p عالية الدقة\n")
                    .append("- الصوت: تعليق صوتي باللغة العربية + موسيقى تصويرية\n\n")
                    .append("يمكنك مشاهدة الإعلان وتنزيله من هنا: ").append(url).append("\n\n");
        } else if (status == TrailerStatus.NEEDS_REVIEW) {
            sb.append("يسرنا إعلامك بأن الإعلان الترويجي لكتابك «").append(bookTitle)
                    .append("» متاح الآن للمراجعة التحريرية في الاستوديو.\n\n")
                    .append("رابط المراجعة: ").append(url).append("\n\n");
        } else {
            sb.append("نود إحاطتك بمواجهة مشكلة تقنية أثناء إنشاء الإعلان الترويجي لكتابك «").append(bookTitle).append("».\n\n");
            if (error != null && !error.isBlank()) {
                sb.append("التفاصيل: ").append(error).append("\n\n");
            }
            sb.append("يمكنك عرض تفاصيل كتابك من هنا: ").append(url).append("\n\n");
        }
        sb.append("مع أطيب التحيات،\nفريق عمل منصة كِتاب");
        return sb.toString();
    }
}
