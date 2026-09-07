package com.doova.ktab.security.config;

import com.doova.ktab.dto.ApiResponse;
import com.doova.ktab.enums.ApiMessageKey;
import com.doova.ktab.security.exception.SecurityExceptionHandler;
import com.doova.ktab.security.filter.JwtFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import com.doova.ktab.utils.response.ResponseUtils;

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecurityExceptionHandler securityExceptionHandler;
    private final org.springframework.context.MessageSource messageSource;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authProvider
    ) throws Exception {

        http
                // Disable CSRF for APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Global CORS
                .cors(cors -> cors.configurationSource(request -> {
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOrigins(List.of("*"));
                    config.setAllowedMethods(List.of("*"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setAllowCredentials(false);
                    return config;
                }))

                // Stateless JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // IMPORTANT: allow ASYNC + ERROR dispatchers (SSE-safe)
                .authorizeHttpRequests(auth -> auth
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        ).permitAll()
                )

                // Centralized exception handling
                .exceptionHandling(ex -> ex

                        // 401 — Not authenticated
                        .authenticationEntryPoint((req, res, exc) -> {
                            if (!res.isCommitted()) {
                                ApiResponse<Void> body = ApiResponse.error(
                                        ApiMessageKey.SECURITY_UNAUTHORIZED.getMessage(messageSource),
                                        HttpStatus.UNAUTHORIZED
                                );

                                ResponseUtils.send(body, res, HttpStatus.UNAUTHORIZED);
                            }
                        })

                        // 403 — Authenticated but forbidden
                        .accessDeniedHandler((req, res, exc) -> {
                            if (!res.isCommitted()) {
                                ApiResponse<Void> body = ApiResponse.error(
                                        ApiMessageKey.SECURITY_ACCESS_DENIED.getMessage(messageSource),
                                        HttpStatus.FORBIDDEN
                                );

                                ResponseUtils.send(body, res, HttpStatus.FORBIDDEN);
                            }
                        })
                )

                // Route authorization
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/reader/covers",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/webjars/**",
                                "/error",
                                "/favicon.ico",
                                "/ws/**",
                                "api/ocr/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // AI endpoints
                        .requestMatchers("/api/v1/conclusion/stream").authenticated()
                        .requestMatchers("/api/v1/conclusion/generate").authenticated()

                        // Everything else
                        .anyRequest().authenticated()
                )

                // JWT filter
                .authenticationProvider(authProvider)
                .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Disable unused auth mechanisms
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // ---------------------------------------------------------------------
    // AUTH PROVIDER
    // ---------------------------------------------------------------------

    @Bean
    @Deprecated
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService,
            PasswordEncoder passwordEncoder
    ) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
