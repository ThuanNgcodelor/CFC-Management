package com.booking.system.health.config;

import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;

@Component
@Order(org.springframework.core.Ordered.HIGHEST_PRECEDENCE + 20)
public class HealthSelfPrivacyFilter extends OncePerRequestFilter {
    @Override protected boolean shouldNotFilter(HttpServletRequest request) {
        String path=request.getRequestURI();return !path.startsWith("/api/v1/health-self")&&!path.equals("/health-lookup");
    }
    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain) throws ServletException,IOException {
        response.setHeader("Cache-Control","no-store, private");response.setHeader("Pragma","no-cache");
        response.setHeader("Referrer-Policy","no-referrer");response.setHeader("X-Robots-Tag","noindex, nofollow, noarchive");
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("X-Frame-Options","DENY");
        chain.doFilter(request,response);
    }
}
