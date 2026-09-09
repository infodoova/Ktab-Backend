package com.doova.ktab.validation;

import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.dto.review.ReviewRequestDto;
import com.doova.ktab.dto.user.UserRegisterRequest;
import com.doova.ktab.features.story.dto.ChooseRequest;
import com.doova.ktab.features.story.dto.CreateStoryRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.util.Locale;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class ValidationLocalizationTest {

    private Validator validator;

    @BeforeEach
    void setUp() {
        ReloadableResourceBundleMessageSource messageSource = new ReloadableResourceBundleMessageSource();
        messageSource.setBasename("classpath:messages");
        messageSource.setDefaultEncoding("UTF-8");

        LocalValidatorFactoryBean factory = new LocalValidatorFactoryBean();
        factory.setValidationMessageSource(messageSource);
        factory.afterPropertiesSet();
        this.validator = factory;
    }

    @Test
    @DisplayName("UserRegisterRequest with blank fields resolves Arabic validation messages")
    void testUserRegisterRequestArabicMessages() {
        UserRegisterRequest req = new UserRegisterRequest("", "", "", "invalid-email", "123", "INVALID_ROLE");
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());

        boolean hasFirstNameAr = violations.stream().anyMatch(v -> v.getMessage().contains("الاسم الأول مطلوب"));
        boolean hasLastNameAr = violations.stream().anyMatch(v -> v.getMessage().contains("اسم العائلة مطلوب"));
        boolean hasEmailAr = violations.stream().anyMatch(v -> v.getMessage().contains("صيغة البريد الإلكتروني غير صحيحة"));
        boolean hasPasswordMinAr = violations.stream().anyMatch(v -> v.getMessage().contains("يجب ألا تقل كلمة المرور عن 6 أحرف"));

        assertTrue(hasFirstNameAr, "Should have Arabic first name error");
        assertTrue(hasLastNameAr, "Should have Arabic last name error");
        assertTrue(hasEmailAr, "Should have Arabic email format error");
        assertTrue(hasPasswordMinAr, "Should have Arabic password size error");
    }

    @Test
    @DisplayName("ChooseRequest with invalid choiceId pattern resolves Arabic message")
    void testChooseRequestPatternArabicMessage() {
        ChooseRequest req = new ChooseRequest("Z");
        Set<ConstraintViolation<ChooseRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        boolean hasPatternAr = violations.stream().anyMatch(v -> v.getMessage().contains("الخيار يجب أن يكون A أو B أو C أو D"));
        assertTrue(hasPatternAr, "Should contain Arabic pattern violation message");
    }

    @Test
    @DisplayName("CreateStoryRequest with blank title resolves Arabic title required message")
    void testCreateStoryRequestArabicTitleMessage() {
        CreateStoryRequest req = new CreateStoryRequest("", "Fiction", 5, null, "Cinematic", "Notes", null);
        Set<ConstraintViolation<CreateStoryRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        boolean hasTitleAr = violations.stream().anyMatch(v -> v.getMessage().contains("عنوان القصة مطلوب"));
        boolean hasLensAr = violations.stream().anyMatch(v -> v.getMessage().contains("منظور القصة مطلوب"));

        assertTrue(hasTitleAr, "Should contain Arabic title required message");
        assertTrue(hasLensAr, "Should contain Arabic lens required message");
    }

    @Test
    @DisplayName("ReviewRequestDto with null rating resolves Arabic rating required message")
    void testReviewRequestDtoArabicMessage() {
        ReviewRequestDto req = new ReviewRequestDto(null, "Good book");
        Set<ConstraintViolation<ReviewRequestDto>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        boolean hasRatingAr = violations.stream().anyMatch(v -> v.getMessage().contains("التقييم مطلوب"));
        assertTrue(hasRatingAr, "Should contain Arabic rating required message");
    }

    @Test
    @DisplayName("AssignBookRequest with null bookId resolves Arabic book ID message")
    void testAssignBookRequestArabicMessage() {
        AssignBookRequest req = new AssignBookRequest(null);
        Set<ConstraintViolation<AssignBookRequest>> violations = validator.validate(req);

        assertFalse(violations.isEmpty());
        boolean hasBookIdAr = violations.stream().anyMatch(v -> v.getMessage().contains("معرف الكتاب مطلوب"));
        assertTrue(hasBookIdAr, "Should contain Arabic book ID required message");
    }
}
