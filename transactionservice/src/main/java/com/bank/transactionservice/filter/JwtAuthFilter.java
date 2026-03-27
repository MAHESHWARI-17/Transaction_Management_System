package com.bank.transactionservice.filter;

import com.bank.transactionservice.service.JwtService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        // Skip OPTIONS preflight
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader("Authorization");

        // No token — let Spring Security decide (public endpoints will pass, protected will 401)
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            if (jwtService.isTokenValid(token)) {

                String customerId = jwtService.extractCustomerId(token);

                // ✅ THIS IS THE CRITICAL LINE THAT WAS MISSING
                // Set the authenticated user into Spring Security's context
                // customerId becomes the value injected by @AuthenticationPrincipal
                UsernamePasswordAuthenticationToken authentication =
                        new UsernamePasswordAuthenticationToken(
                                customerId,  // principal → @AuthenticationPrincipal String customerId
                                null,        // credentials (not needed after auth)
                                List.of()    // authorities (empty — no role-based restrictions)
                        );

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request)
                );

                // Put authentication into SecurityContext so Spring knows this request is authenticated
                SecurityContextHolder.getContext().setAuthentication(authentication);

                log.info("Authenticated customer: {}", customerId);

            } else {
                log.warn("Invalid JWT token");
                SecurityContextHolder.clearContext();
            }

        } catch (Exception e) {
            log.error("JWT filter error: {}", e.getMessage());
            SecurityContextHolder.clearContext();
        }

        filterChain.doFilter(request, response);
    }
}