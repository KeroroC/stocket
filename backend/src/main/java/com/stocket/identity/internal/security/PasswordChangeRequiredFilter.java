package com.stocket.identity.internal.security;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    private static final Set<String> EXEMPT_PATHS = Set.of(
            "/api/v1/account",
            "/api/v1/account/password",
            "/api/v1/auth/logout",
            "/api/v1/auth/csrf",
            "/api/v1/system"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication != null
                && authentication.isAuthenticated()
                && authentication.getPrincipal() instanceof IdentityPrincipal principal
                && principal.mustChangePassword()
                && !isExemptPath(request.getRequestURI())) {

            ProblemDetail problem = ProblemDetail.forStatus(HttpStatus.FORBIDDEN);
            problem.setTitle("Password change required");
            problem.setProperty("code", "PASSWORD_CHANGE_REQUIRED");
            problem.setProperty("retryable", false);

            ProblemAccessDeniedHandler.writeProblemDetail(response, problem);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean isExemptPath(String requestUri) {
        return EXEMPT_PATHS.stream().anyMatch(requestUri::equals);
    }
}
