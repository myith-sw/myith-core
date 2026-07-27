package com.myith.core.common;

import com.myith.core.application.port.UserRepository;
import com.myith.core.domain.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.time.Instant;
import java.util.Set;

@Component
public class ActivityInterceptor implements HandlerInterceptor {

    private static final Set<String> EXCLUDED_PATHS = Set.of(
            "/api/heartbeat", "/api/health", "/api-docs", "/swagger-ui"
    );

    private final UserRepository userRepository;
    private final long throttleMinutes;

    public ActivityInterceptor(UserRepository userRepository,
                               @Value("${policy.activity.throttle-minutes}") long throttleMinutes) {
        this.userRepository = userRepository;
        this.throttleMinutes = throttleMinutes;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
                             Object handler) {
        String path = request.getRequestURI();
        if (isExcluded(path)) return true;

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof Long userId)) return true;

        userRepository.findById(userId).ifPresent(user -> {
            Instant now = Instant.now();
            if (!user.shouldThrottleActivity(now, throttleMinutes)) {
                user.touchActivity(now);
                userRepository.save(user);
            }
        });

        return true;
    }

    private boolean isExcluded(String path) {
        return EXCLUDED_PATHS.stream().anyMatch(path::startsWith);
    }
}
