package com.doova.ktab.validation;

import com.doova.ktab.annotation.ValidPassword;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.util.regex.Pattern;

/**
 * Constraint validator implementation for {@link ValidPassword}.
 */
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile(ValidationConstants.PASSWORD_REGEX);

    private boolean required;

    @Override
    public void initialize(ValidPassword constraintAnnotation) {
        this.required = constraintAnnotation.required();
    }

    @Override
    public boolean isValid(String password, ConstraintValidatorContext context) {
        if (password == null || password.isBlank()) {
            return !required;
        }

        return PASSWORD_PATTERN.matcher(password).matches();
    }
}
