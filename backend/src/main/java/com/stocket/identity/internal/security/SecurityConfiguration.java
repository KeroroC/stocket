package com.stocket.identity.internal.security;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.HeaderWriterFilter;

import com.stocket.identity.internal.config.IdentityProperties;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(IdentityProperties.class)
class SecurityConfiguration {

    private final SessionAuthenticationFilter sessionAuthenticationFilter;
    private final PasswordChangeRequiredFilter passwordChangeRequiredFilter;
    private final ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint;
    private final ProblemAccessDeniedHandler problemAccessDeniedHandler;

    SecurityConfiguration(SessionAuthenticationFilter sessionAuthenticationFilter,
                          PasswordChangeRequiredFilter passwordChangeRequiredFilter,
                          ProblemAuthenticationEntryPoint problemAuthenticationEntryPoint,
                          ProblemAccessDeniedHandler problemAccessDeniedHandler) {
        this.sessionAuthenticationFilter = sessionAuthenticationFilter;
        this.passwordChangeRequiredFilter = passwordChangeRequiredFilter;
        this.problemAuthenticationEntryPoint = problemAuthenticationEntryPoint;
        this.problemAccessDeniedHandler = problemAccessDeniedHandler;
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        return CookieCsrfTokenRepository.withHttpOnlyFalse();
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository) throws Exception {
        http
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .securityContext(context -> context
                        .securityContextRepository(new RequestAttributeSecurityContextRepository()))
                .requestCache(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .headers(headers -> headers
                        // Avoid commit-time header writes racing with async response streaming.
                        .addObjectPostProcessor(new ObjectPostProcessor<HeaderWriterFilter>() {
                            @Override
                            public HeaderWriterFilter postProcess(HeaderWriterFilter filter) {
                                filter.setShouldWriteHeadersEagerly(true);
                                return filter;
                            }
                        }))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(problemAuthenticationEntryPoint)
                        .accessDeniedHandler(problemAccessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET,
                                "/api/v1/system",
                                "/api/v1/setup/status",
                                "/api/v1/auth/csrf",
                                "/api/v1/invites/*/status",
                                "/livez",
                                "/readyz").permitAll()
                        .requestMatchers(HttpMethod.POST,
                                "/api/v1/setup/initialize",
                                "/api/v1/auth/login",
                                "/api/v1/auth/logout",
                                "/api/v1/invites/*/accept").permitAll()
                        .requestMatchers("/api/v1/admin/**").hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(sessionAuthenticationFilter, AnonymousAuthenticationFilter.class)
                .addFilterAfter(passwordChangeRequiredFilter, SessionAuthenticationFilter.class);

        return http.build();
    }
}
