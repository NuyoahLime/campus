package com.campusguinness.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

public class RegisteredUserApiAccessFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
            FilterChain chain) throws ServletException, IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()
                || !(auth.getPrincipal() instanceof CampusGuinnessUserDetails user)
                || !"REGISTERED_USER".equals(user.getResolvedIdentity() == null
                        ? null : user.getResolvedIdentity().primaryRole())) {
            chain.doFilter(request, response);
            return;
        }

        if (isAllowed(request)) {
            chain.doFilter(request, response);
            return;
        }

        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write("""
                {"code":"REGISTERED_USER_ONBOARDING_REQUIRED","message":"Complete onboarding before accessing this resource."}
                """);
    }

    private boolean isAllowed(HttpServletRequest request) {
        String method = request.getMethod();
        String path = request.getRequestURI();
        if (HttpMethod.GET.matches(method) && "/api/v1/auth/me".equals(path)) {
            return true;
        }
        if (HttpMethod.POST.matches(method) && "/api/v1/auth/logout".equals(path)) {
            return true;
        }
        if (HttpMethod.GET.matches(method) && "/api/v1/schools".equals(path)) {
            return true;
        }
        return path.equals("/api/v1/onboarding")
                || path.startsWith("/api/v1/onboarding/");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }
}
