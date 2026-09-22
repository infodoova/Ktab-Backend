package com.doova.ktab.validation;

import com.doova.ktab.dto.library.AssignBookRequest;
import com.doova.ktab.dto.review.ReviewRequestDto;
import com.doova.ktab.dto.user.UpdatePublisherRequest;
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
        boolean hasPasswordInvalidAr = violations.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص"));

        assertTrue(hasFirstNameAr, "Should have Arabic first name error");
        assertTrue(hasLastNameAr, "Should have Arabic last name error");
        assertTrue(hasEmailAr, "Should have Arabic email format error");
        assertTrue(hasPasswordInvalidAr, "Should have Arabic unified password validation error");
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

    @Test
    @DisplayName("Valid password passes unified validation")
    void testValidPassword_passes() {
        UserRegisterRequest req = new UserRegisterRequest("John", null, "Doe", "john@example.com", "SecureP@ss123", "READER");
        Set<ConstraintViolation<UserRegisterRequest>> violations = validator.validate(req);
        assertTrue(violations.isEmpty(), "Valid request should have no violations");
    }

    @Test
    @DisplayName("Password missing complexity or length fails with unified Arabic message")
    void testInvalidPassword_failsWithUnifiedMessage() {
        // Missing special char
        UserRegisterRequest req1 = new UserRegisterRequest("John", null, "Doe", "john@example.com", "Password123", "READER");
        Set<ConstraintViolation<UserRegisterRequest>> violations1 = validator.validate(req1);
        assertTrue(violations1.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص")));

        // Length < 8
        UserRegisterRequest req2 = new UserRegisterRequest("John", null, "Doe", "john@example.com", "P@s1", "READER");
        Set<ConstraintViolation<UserRegisterRequest>> violations2 = validator.validate(req2);
        assertTrue(violations2.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص")));

        // Missing digit
        UserRegisterRequest req3 = new UserRegisterRequest("John", null, "Doe", "john@example.com", "Password!@", "READER");
        Set<ConstraintViolation<UserRegisterRequest>> violations3 = validator.validate(req3);
        assertTrue(violations3.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص")));

        // Missing uppercase
        UserRegisterRequest req4 = new UserRegisterRequest("John", null, "Doe", "john@example.com", "password123!", "READER");
        Set<ConstraintViolation<UserRegisterRequest>> violations4 = validator.validate(req4);
        assertTrue(violations4.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص")));
    }

    @Test
    @DisplayName("Optional password when null or blank passes validation")
    void testOptionalPassword_nullOrBlank_passes() {
        UpdatePublisherRequest reqNull = new UpdatePublisherRequest("pub@example.com", "Pub", null, "Publisher", null);
        Set<ConstraintViolation<UpdatePublisherRequest>> violationsNull = validator.validate(reqNull);
        assertTrue(violationsNull.isEmpty(), "Null optional password should produce no violations");

        UpdatePublisherRequest reqBlank = new UpdatePublisherRequest("pub@example.com", "Pub", null, "Publisher", "");
        Set<ConstraintViolation<UpdatePublisherRequest>> violationsBlank = validator.validate(reqBlank);
        assertTrue(violationsBlank.isEmpty(), "Blank optional password should produce no violations");
    }

    @Test
    @DisplayName("Optional password when provided but invalid fails validation")
    void testOptionalPassword_providedInvalid_fails() {
        UpdatePublisherRequest reqInvalid = new UpdatePublisherRequest("pub@example.com", "Pub", null, "Publisher", "weak");
        Set<ConstraintViolation<UpdatePublisherRequest>> violations = validator.validate(reqInvalid);
        assertFalse(violations.isEmpty(), "Invalid optional password should produce violations");
        assertTrue(violations.stream().anyMatch(v -> v.getMessage().contains("يجب أن تتكون كلمة المرور من 8 أحرف على الأقل، وتحتوي على حرف كبير وحرف صغير ورقم ورمز خاص")));
    }
}
