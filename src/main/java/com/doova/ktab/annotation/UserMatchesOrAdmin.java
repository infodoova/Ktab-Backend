package com.doova.ktab.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface UserMatchesOrAdmin {
    String path() default "";   // ID from @PathVariable

    String body() default "";   // ID from DTO or request body
}
