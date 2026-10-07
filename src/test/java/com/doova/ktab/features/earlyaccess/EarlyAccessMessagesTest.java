package com.doova.ktab.features.earlyaccess;

import com.doova.ktab.enums.message.ApiMessageKey;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessOrganizationRequest;
import com.doova.ktab.features.earlyaccess.web.dto.EarlyAccessSignupRequest;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

/** Every message the early-access signup and the public cover endpoints can show exists in messages.properties. */
class EarlyAccessMessagesTest {

    private static final List<String> RESPONSE_KEYS = List.of(
            "EARLY_ACCESS_REGISTERED", "EARLY_ACCESS_ALREADY_REGISTERED",
            "BOOK_COVERS_FETCHED", "BOOK_TOP_REVIEWED_COVERS_FETCHED", "BOOK_COVER_IMAGES_FETCHED");

    private static ResourceBundleMessageSource messages() {
        ResourceBundleMessageSource source = new ResourceBundleMessageSource();
        source.setBasename("messages");
        source.setDefaultEncoding("UTF-8");
        source.setUseCodeAsDefaultMessage(false);
        return source;
    }

    @Test
    void everyResponseMessageHasArabicText() {
        ResourceBundleMessageSource source = messages();
        for (String name : RESPONSE_KEYS) {
            ApiMessageKey key = ApiMessageKey.valueOf(name);
            assertThat(source.getMessage(key.getKey(), null, Locale.ENGLISH)).as(name).isNotBlank();
        }
    }

    @Test
    void theTwoPublicCoverEndpointsAndTheCatalogHaveTheirOwnMessages() {
        assertThat(Arrays.stream(ApiMessageKey.values()).map(Enum::name))
                .contains("BOOK_COVERS_FETCHED", "BOOK_TOP_REVIEWED_COVERS_FETCHED", "BOOK_COVER_IMAGES_FETCHED");
    }

    @Test
    void everyValidationMessageTheSignupRequestUsesExists() throws IOException {
        ResourceBundleMessageSource source = messages();
        Pattern ref = Pattern.compile("\\{(validation\\.[a-z.]+)\\}");
        for (String file : List.of("EarlyAccessSignupRequest.java", "EarlyAccessOrganizationRequest.java")) {
            String text = Files.readString(Path.of("src/main/java/com/doova/ktab/features/earlyaccess/web/dto", file));
            Matcher m = ref.matcher(text);
            int found = 0;
            while (m.find()) {
                found++;
                assertThat(source.getMessage(m.group(1), null, Locale.ENGLISH)).as(file + " " + m.group(1)).isNotBlank();
            }
            assertThat(found).as(file + " references message keys, not hard-coded text").isGreaterThan(4);
        }
    }

    @Test
    void aFailedValidationAnswersInArabicFromTheMessageFile() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages());
        validator.afterPropertiesSet();

        EarlyAccessSignupRequest bad = new EarlyAccessSignupRequest("not-an-email", " ", com.doova.ktab.enums.user.UserRole.ADMIN,
                null, "call me", null, "a plan; drop");

        List<String> shown = validator.validate(bad).stream().map(v -> v.getMessage()).toList();

        assertThat(shown).isNotEmpty().allSatisfy(message -> assertThat(message).doesNotContain("{").doesNotContain("must be"));
        assertThat(shown).anyMatch(message -> message.contains("البريد"));
        assertThat(shown).anyMatch(message -> message.contains("رقم الهاتف"));
    }

    @Test
    void anOrganizationFailureIsAlsoShownInArabic() {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.setValidationMessageSource(messages());
        validator.afterPropertiesSet();

        EarlyAccessOrganizationRequest bad = new EarlyAccessOrganizationRequest(" ", null, null, null, null, "library.org", "x", "no");

        assertThat(validator.validate(bad).stream().map(v -> v.getMessage()))
                .isNotEmpty().allSatisfy(message -> assertThat(message).doesNotContain("{"));
    }
}
