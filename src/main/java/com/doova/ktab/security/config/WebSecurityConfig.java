package com.doova.ktab.security.config;

import com.doova.ktab.security.exception.SecurityExceptionHandler;
import com.doova.ktab.security.filter.CorrelationIdFilter;
import com.doova.ktab.security.filter.JwtFilter;
import com.doova.ktab.security.filter.RateLimitingFilter;
import jakarta.servlet.DispatcherType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.annotation.web.configurers.HeadersConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;

import java.util.List;

@Slf4j
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final CorrelationIdFilter correlationIdFilter;
    private final RateLimitingFilter rateLimitingFilter;
    private final JwtFilter jwtFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    @Value("${app.cors.allowed-origins:http://localhost:3000,http://localhost:4200,http://localhost:5173,http://192.168.1.16:5173,http://192.168.*:*,https://ktab-rho.vercel.app,https://kristan-prickliest-ezekiel.ngrok-free.dev,https://melisa-balsamiferous-aubrie.ngrok-free.dev,https://*.ngrok-free.dev,https://*.ngrok.app}")
    private String allowedOrigins;

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            AuthenticationProvider authProvider
    ) throws Exception {

        List<String> sanitizedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(o -> o.replaceFirst("^(https?://[^/]+).*", "$1"))
                .distinct()
                .toList();

        log.info("Initialized CORS allowed origins: {}", sanitizedOrigins);

        http
                // Disable CSRF for REST APIs
                .csrf(AbstractHttpConfigurer::disable)

                // Global CORS
                .cors(cors -> cors.configurationSource(request -> {
                    String reqOrigin = request.getHeader("Origin");
                    log.info("CORS check for Origin: {}, Method: {}, Path: {}", reqOrigin, request.getMethod(), request.getRequestURI());
                    CorsConfiguration config = new CorsConfiguration();
                    config.setAllowedOriginPatterns(sanitizedOrigins);
                    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                    config.setAllowedHeaders(List.of("*"));
                    config.setExposedHeaders(List.of(
                            "X-Correlation-Id",
                            "X-RateLimit-Limit",
                            "X-RateLimit-Remaining",
                            "X-RateLimit-Reset",
                            "Retry-After",
                            "Content-Disposition"
                    ));
                    config.setAllowCredentials(true);
                    return config;
                }))

                // OWASP Recommended Security Headers
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp
                                .policyDirectives("default-src 'self'; script-src 'self' 'unsafe-inline' 'unsafe-eval'; style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com data:; img-src 'self' data: https:; connect-src 'self' ws: wss:; frame-ancestors 'none';")
                        )
                        .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                        .contentTypeOptions(Customizer.withDefaults())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31536000)
                        )
                        .referrerPolicy(referrer -> referrer
                                .policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)
                        )
                        .addHeaderWriter(new org.springframework.security.web.header.writers.StaticHeadersWriter(
                                "Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()"
                        ))
                )

                // Stateless JWT
                .sessionManagement(session ->
                        session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
                )

                // Centralized exception handling
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(securityExceptionHandler)
                        .accessDeniedHandler(securityExceptionHandler)
                )

                // Route authorization (single unified block)
                .authorizeHttpRequests(auth -> auth
                        // Always permit preflight CORS OPTIONS requests
                        .requestMatchers(org.springframework.http.HttpMethod.OPTIONS, "/**").permitAll()

                        // SSE & Error dispatchers
                        .dispatcherTypeMatchers(
                                DispatcherType.ASYNC,
                                DispatcherType.ERROR
                        ).permitAll()

                        // Public endpoints
                        .requestMatchers(
                                "/api/v1/auth/**",
                                "/api/v1/reader/covers",
                                "/api/v1/reader/books/covers",
                                "/swagger-ui.html",
                                "/swagger-ui/**",
                                "/v3/api-docs/**",
                                "/api-docs/**",
                                "/webjars/**",
                                "/error",
                                "/favicon.ico",
                                "/ws/**",
                                "/Ktab-0.0.1-SNAPSHOT/ws/**",
                                "/api/v1/reader/organizations/**",
                                "/api/v1/internal/ocr/**",
                                "/actuator/health",
                                "/actuator/info"
                        ).permitAll()

                        // All other endpoints require authentication
                        .anyRequest().authenticated()
                )

                // Filter Chain Ordering:
                // CorrelationIdFilter -> RateLimitingFilter -> JwtFilter -> UsernamePasswordAuthenticationFilter
                .authenticationProvider(authProvider)
                .addFilterBefore(correlationIdFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(rateLimitingFilter, CorrelationIdFilter.class)
                .addFilterAfter(jwtFilter, RateLimitingFilter.class)

                // Disable unused form/basic login
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable);

        return http.build();
    }

    // ---------------------------------------------------------------------
    // AUTH PROVIDER
    // ---------------------------------------------------------------------

    @Bean
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
