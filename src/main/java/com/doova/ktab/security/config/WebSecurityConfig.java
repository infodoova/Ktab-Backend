package com.doova.ktab.security.config;

import com.doova.ktab.security.exception.SecurityExceptionHandler;
import com.doova.ktab.security.filter.JwtFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import java.util.List;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
@RequiredArgsConstructor
public class WebSecurityConfig {

    private final JwtFilter jwtFilter;
    private final SecurityExceptionHandler securityExceptionHandler;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationProvider authProvider) throws Exception {

        http
                // Disable CSRF for API
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

                // Stateless session (JWT)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // ⚠️ CRITICAL FIX — Do NOT re-run Security on ASYNC dispatch and ERROR dispatch
                .authorizeHttpRequests(auth -> auth.dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll().dispatcherTypeMatchers(DispatcherType.ERROR).permitAll())

                // SSE-safe error handling
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, exc) -> {
                    if (!res.isCommitted()) {
                        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                        res.setContentType("application/json; charset=UTF-8");
                        res.getWriter().write("""
                                {"success":false,"message":"غير مصرح بالوصول"}
                                """);
                    }
                }).accessDeniedHandler((req, res, exc) -> {
                    if (!res.isCommitted()) {
                        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
                        res.setContentType("application/json; charset=UTF-8");
                        res.getWriter().write("""
                                {"success":false,"message":"تم رفض الوصول"}
                                """);
                    }
                }))

                // Authorize routes
                .authorizeHttpRequests(auth -> auth

                        // Public endpoints
                        .requestMatchers("/api/v1/auth/**", "/swagger-ui.html", "/swagger-ui/**", "/v3/api-docs/**", "/api-docs/**", "/webjars/**", "/error", "/favicon.ico"      // <-- browser default request, MUST be allowed
                        ).permitAll()

                        // SSE streaming endpoint — must be authenticated BEFORE the stream starts
                        .requestMatchers("/api/v1/conclusion/stream").authenticated()

                        // Non-stream endpoint
                        .requestMatchers("/api/v1/conclusion/generate").authenticated()

                        // Everything else
                        .anyRequest().authenticated())

                // JWT authentication filter
                .authenticationProvider(authProvider).addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)

                // Disable unused auth methods
                .httpBasic(AbstractHttpConfigurer::disable).formLogin(AbstractHttpConfigurer::disable).logout(AbstractHttpConfigurer::disable);

        return http.build();
    }


    @Bean
    @Deprecated
    public AuthenticationProvider authenticationProvider(UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }
}
