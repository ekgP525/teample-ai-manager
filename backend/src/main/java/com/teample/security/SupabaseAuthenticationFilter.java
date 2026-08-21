package com.teample.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
@RequiredArgsConstructor
public class SupabaseAuthenticationFilter extends OncePerRequestFilter {

    public static final String CURRENT_USER_ID_ATTRIBUTE = "currentUserId";
    public static final String AUTHENTICATED_USER_ATTRIBUTE = "authenticatedUser";
    public static final String AUTH_USER_ID_ATTRIBUTE = "authUserId";
    public static final String CURRENT_USER_EMAIL_ATTRIBUTE = "currentUserEmail";
    public static final String ADMIN_TEST_USER_ATTRIBUTE = "adminTestUser";
    public static final String ADMIN_ID_HEADER = "X-Admin-Id";
    public static final String ADMIN_PASSWORD_HEADER = "X-Admin-Password";
    public static final String CURRENT_USER_ID_HEADER = "X-Current-User-Id";

    private static final List<Pattern> PROTECTED_PATHS = List.of(
            Pattern.compile("^/api/auth/me$"),
            Pattern.compile("^/api/dashboard(?:/.*)?$"),
            Pattern.compile("^/api/projects/[^/]+/dashboard(?:/.*)?$"),
            Pattern.compile("^/api/todo-assignments/[^/]+$"),
            Pattern.compile("^/api/todos/[^/]+/progress$")
    );
    private static final Pattern PROJECT_MEMBERS_PATH = Pattern.compile("^/api/projects/[^/]+/members$");
    private static final List<Pattern> PROJECT_RESOURCE_PATHS = List.of(
            Pattern.compile("^/api/projects/[^/]+$"),
            Pattern.compile("^/api/projects/[^/]+/(restore|permanent)$"),
            Pattern.compile("^/api/projects/[^/]+/minutes(?:/.*)?$"),
            Pattern.compile("^/api/projects/[^/]+/todos(?:/.*)?$")
    );

    private final SupabaseAuthService authService;
    private final AdminTestAuthService adminTestAuthService;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        return !requiresAuthentication(request);
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        try {
            AuthenticatedUser adminUser = resolveAdminTestUser(request);
            if (adminUser != null) {
                setCurrentUserAttributes(request, adminUser);
                request.setAttribute(ADMIN_TEST_USER_ATTRIBUTE, true);
                filterChain.doFilter(request, response);
                return;
            }

            AuthenticatedUser user = authService.authenticate(request);
            setCurrentUserAttributes(request, user);
            filterChain.doFilter(request, response);
        } catch (AuthRequiredException e) {
            writeUnauthorized(response, e.getMessage());
        }
    }

    private boolean requiresAuthentication(HttpServletRequest request) {
        String requestPath = normalizeRequestPath(request);
        String method = request.getMethod();
        if (("GET".equalsIgnoreCase(method) || "POST".equalsIgnoreCase(method))
                && "/api/projects".equals(requestPath)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && "/api/projects/trash".equals(requestPath)) {
            return true;
        }
        if ("GET".equalsIgnoreCase(method) && PROJECT_MEMBERS_PATH.matcher(requestPath).matches()) {
            return true;
        }
        if (PROJECT_RESOURCE_PATHS.stream().anyMatch(pattern -> pattern.matcher(requestPath).matches())) {
            return true;
        }
        return PROTECTED_PATHS.stream()
                .anyMatch(pattern -> pattern.matcher(requestPath).matches());
    }

    private AuthenticatedUser resolveAdminTestUser(HttpServletRequest request) {
        AdminCredentials credentials = resolveAdminCredentials(request);
        if (credentials == null) {
            return null;
        }
        if (!adminTestAuthService.matches(credentials.id(), credentials.password())) {
            throw new AuthRequiredException("Invalid admin credentials.");
        }

        String selectedUserId = request.getHeader(CURRENT_USER_ID_HEADER);
        String memberKey = selectedUserId == null || selectedUserId.isBlank()
                ? adminTestAuthService.adminId()
                : URLDecoder.decode(selectedUserId.trim(), StandardCharsets.UTF_8);
        if (memberKey.isBlank()) {
            throw new AuthRequiredException("Admin selected user is empty.");
        }
        return new AuthenticatedUser("admin-test:" + adminTestAuthService.adminId(), memberKey, null);
    }

    private AdminCredentials resolveAdminCredentials(HttpServletRequest request) {
        String headerId = request.getHeader(ADMIN_ID_HEADER);
        String headerPassword = request.getHeader(ADMIN_PASSWORD_HEADER);
        if (headerId != null || headerPassword != null) {
            return new AdminCredentials(headerId, headerPassword);
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Basic ")) {
            return null;
        }

        try {
            String encodedCredentials = authorization.substring("Basic ".length()).trim();
            String decodedCredentials = new String(Base64.getDecoder().decode(encodedCredentials), StandardCharsets.UTF_8);
            int separator = decodedCredentials.indexOf(':');
            if (separator < 0) {
                throw new AuthRequiredException("Invalid admin basic credentials.");
            }
            return new AdminCredentials(
                    decodedCredentials.substring(0, separator),
                    decodedCredentials.substring(separator + 1)
            );
        } catch (IllegalArgumentException e) {
            throw new AuthRequiredException("Invalid admin basic credentials.");
        }
    }

    private void setCurrentUserAttributes(HttpServletRequest request, AuthenticatedUser user) {
        request.setAttribute(CURRENT_USER_ID_ATTRIBUTE, user.memberKey());
        request.setAttribute(AUTHENTICATED_USER_ATTRIBUTE, user);
        request.setAttribute(AUTH_USER_ID_ATTRIBUTE, user.authUserId());
        request.setAttribute(CURRENT_USER_EMAIL_ATTRIBUTE, user.email());
    }

    private String normalizeRequestPath(HttpServletRequest request) {
        String requestUri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (contextPath != null && !contextPath.isBlank() && requestUri.startsWith(contextPath)) {
            return requestUri.substring(contextPath.length());
        }
        return requestUri;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"message\":\"" + escapeJson(message) + "\"}");
    }

    private String escapeJson(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record AdminCredentials(String id, String password) {
    }
}
