package com.doova.ktab.security.aop;

import com.doova.ktab.annotation.UserMatchesOrAdmin;

import com.doova.ktab.model.user.User;
import com.doova.ktab.utils.Utils;
import com.doova.ktab.utils.response.ResponseUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.aspectj.lang.*;
import org.aspectj.lang.annotation.*;
import org.aspectj.lang.reflect.MethodSignature;

import org.springframework.stereotype.Component;

import org.springframework.expression.*;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

import java.util.Objects;
import java.util.Optional;


@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
public class UserMatchesOrAdminAspect {

    private final ExpressionParser parser = new SpelExpressionParser();

    @Around("@annotation(rule)")
    public Object validate(ProceedingJoinPoint pjp, UserMatchesOrAdmin rule) throws Throwable {

        Optional<User> currentUser = Utils.getCurrentLoggedInUser();

        // admin bypass
        if (Utils.hasRole("ADMIN")) {
            return pjp.proceed();
        }

        // extract the userId from path or body
        Long providedId = extractId(pjp, rule);

        if (providedId == null) {
            throw new IllegalStateException("@UserMatchesOrAdmin requires a path or body expression");
        }

        // compare them
        if (!Objects.equals(currentUser.map(User::getId).orElse(null), providedId)) {
            return ResponseUtils.forbidden("You cannot access another user's data.");
        }

        return pjp.proceed();
    }


    private Long extractId(ProceedingJoinPoint pjp, UserMatchesOrAdmin rule) {

        if (!rule.path().isEmpty()) {
            return resolveSpEL(pjp, rule.path());
        }

        if (!rule.body().isEmpty()) {
            return resolveSpEL(pjp, rule.body());
        }

        return null;
    }


    private Long resolveSpEL(ProceedingJoinPoint pjp, String expression) {

        EvaluationContext ctx = new StandardEvaluationContext();

        String[] paramNames = ((MethodSignature) pjp.getSignature()).getParameterNames();

        Object[] args = pjp.getArgs();

        for (int i = 0; i < paramNames.length; i++) {
            ctx.setVariable(paramNames[i], args[i]);
        }

        return parser.parseExpression(expression).getValue(ctx, Long.class);
    }
}