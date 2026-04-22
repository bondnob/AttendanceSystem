package com.attendance.auth.security;

import com.attendance.exception.BizException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider jwtTokenProvider;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String authorization = request.getHeader("Authorization");
        if (authorization == null || authorization.isBlank()) {
            throw new BizException("未提供有效的 Authorization token");
        }

        String token = authorization.startsWith("Bearer ")
                ? authorization.substring(7)
                : authorization.trim();
        CurrentUser currentUser = jwtTokenProvider.parseToken(token);
        UserContext.set(currentUser);
        log.info("JWT authentication success, userId={}, roleCode={}, uri={}", currentUser.getUserId(), currentUser.getRoleCode(), request.getRequestURI());
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
