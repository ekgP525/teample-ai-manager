package com.teample.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class SupabaseAuthenticationFilterTest {

    @Test
    void setsCurrentUserAttributesForDashboardRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/dashboard/my");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.CURRENT_USER_ID_ATTRIBUTE)).isEqualTo("minjae");
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTHENTICATED_USER_ATTRIBUTE)).isEqualTo(user);
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.CURRENT_USER_EMAIL_ATTRIBUTE)).isEqualTo("minjae@example.com");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void allowsAdminToSelectCurrentUserWithHeaders() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/dashboard/my");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        request.addHeader(SupabaseAuthenticationFilter.ADMIN_ID_HEADER, "admin");
        request.addHeader(SupabaseAuthenticationFilter.ADMIN_PASSWORD_HEADER, "1234");
        request.addHeader(SupabaseAuthenticationFilter.CURRENT_USER_ID_HEADER, "member-a");
        when(adminTestAuthService.matches("admin", "1234")).thenReturn(true);
        when(adminTestAuthService.adminId()).thenReturn("admin");

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.CURRENT_USER_ID_ATTRIBUTE)).isEqualTo("member-a");
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("admin-test:admin");
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE)).isEqualTo(true);
        verifyNoInteractions(authService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void allowsAdminToSelectCurrentUserWithBasicAuth() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/dashboard/team");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        String basic = Base64.getEncoder().encodeToString("admin:1234".getBytes(StandardCharsets.UTF_8));

        request.addHeader("Authorization", "Basic " + basic);
        request.addHeader(SupabaseAuthenticationFilter.CURRENT_USER_ID_HEADER, "member-b");
        when(adminTestAuthService.matches("admin", "1234")).thenReturn(true);
        when(adminTestAuthService.adminId()).thenReturn("admin");

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.CURRENT_USER_ID_ATTRIBUTE)).isEqualTo("member-b");
        assertThat(request.getAttribute(SupabaseAuthenticationFilter.ADMIN_TEST_USER_ATTRIBUTE)).isEqualTo(true);
        verifyNoInteractions(authService);
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void rejectsInvalidAdminCredentials() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/projects/my");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        request.addHeader(SupabaseAuthenticationFilter.ADMIN_ID_HEADER, "admin");
        request.addHeader(SupabaseAuthenticationFilter.ADMIN_PASSWORD_HEADER, "wrong");
        when(adminTestAuthService.matches("admin", "wrong")).thenReturn(false);

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid admin credentials.");
        verifyNoInteractions(authService);
        verify(filterChain, never()).doFilter(any(), any());
    }

    @Test
    void protectsProjectCreationRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void protectsProjectMemberRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/members");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void protectsProjectListRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void protectsProjectTrashRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/trash");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void returnsUnauthorizedWhenAuthenticationFails() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/dashboard/projects/my");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);

        when(authService.authenticate(request)).thenThrow(new AuthRequiredException("Invalid Supabase access token."));

        filter.doFilter(request, response, filterChain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).contains("Invalid Supabase access token.");
        verify(filterChain, never()).doFilter(any(), any());
    }
    @Test
    void protectsProjectDetailRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void protectsProjectMinutesRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/minutes");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void protectsProjectTodoRequests() throws Exception {
        SupabaseAuthService authService = mock(SupabaseAuthService.class);
        AdminTestAuthService adminTestAuthService = mock(AdminTestAuthService.class);
        SupabaseAuthenticationFilter filter = new SupabaseAuthenticationFilter(authService, adminTestAuthService);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/projects/project-1/todos");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain filterChain = mock(FilterChain.class);
        AuthenticatedUser user = new AuthenticatedUser("auth-user-1", "minjae", "minjae@example.com");

        when(authService.authenticate(request)).thenReturn(user);

        filter.doFilter(request, response, filterChain);

        assertThat(request.getAttribute(SupabaseAuthenticationFilter.AUTH_USER_ID_ATTRIBUTE)).isEqualTo("auth-user-1");
        verify(filterChain).doFilter(request, response);
    }
}