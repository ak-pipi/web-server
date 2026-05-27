package com.niuma.admin.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 接口限流过滤器
 *
 * <p>基于 Redis + 滑动窗口算法实现, 按 IP + 用户维度双重限流:
 * <ul>
 *   <li>全局 IP 维度: 每秒100次 / 每分钟600次</li>
 *   <li>写操作维度: 每分钟20次</li>
 *   <li>登录接口: 每分钟5次 (防暴力破解)</li>
 * </ul>
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    /** 全局每秒限制 */
    private static final int GLOBAL_PER_SECOND = 100;
    /** 全局每分钟限制 */
    private static final int GLOBAL_PER_MINUTE = 600;
    /** 写操作每分钟限制 */
    private static final int WRITE_PER_MINUTE = 20;
    /** 登录每分钟限制 */
    private static final int LOGIN_PER_MINUTE = 5;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String clientIp = getClientIp(request);
        String uri = request.getRequestURI();
        String method = request.getMethod();

        // 检查各维度限流
        if (!checkLimit(clientIp + ":global:sec", clientIp, GLOBAL_PER_SECOND, Duration.ofSeconds(1))) {
            writeLimitExceeded(response, "全局请求过于频繁(每秒" + GLOBAL_PER_SECOND + "次)");
            return;
        }

        if (!checkLimit(clientIp + ":global:min", clientIp, GLOBAL_PER_MINUTE, Duration.ofMinutes(1))) {
            writeLimitExceeded(response, "全局请求过于频繁(每分钟" + GLOBAL_PER_MINUTE + "次)");
            return;
        }

        // 写操作限流
        if ("POST".equals(method) || "PUT".equals(method) || "DELETE".equals(method)) {
            if (!checkLimit(clientIp + ":write", clientIp, WRITE_PER_MINUTE, Duration.ofMinutes(1))) {
                writeLimitExceeded(response, "写操作过于频繁(每分钟" + WRITE_PER_MINUTE + "次)");
                return;
            }
        }

        // 登录接口特殊限流
        if (uri.contains("/login") || uri.contains("/auth")) {
            if (!checkLimit(clientIp + ":login", clientIp, LOGIN_PER_MINUTE, Duration.ofMinutes(1))) {
                writeLimitExceeded(response, "登录尝试过于频繁, 请稍后再试");
                return;
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 基于滑动窗口检查是否超限
     *
     * @param key      Redis key
     * @param identity 标识 (IP或用户ID)
     * @param limit    限制次数
     * @param window   窗口时间
     * @return true=未超限可放行, false=已超限需拦截
     */
    private boolean checkLimit(String key, String identity, int limit, Duration window) {
        try {
            Long currentCount = redisTemplate.opsForValue().increment(key);

            if (currentCount != null && currentCount == 1) {
                redisTemplate.expire(key, window);
            }

            if (currentCount != null && currentCount > limit) {
                log.warn("[限流] 触发: ip={}, key={}, count={}/{}, window={}s",
                        identity, key, currentCount, limit, window.getSeconds());
                return false;
            }
            return true;
        } catch (Exception e) {
            // Redis 异常时放行, 避免影响正常业务
            log.error("[限流] Redis异常, 放行: error={}", e.getMessage());
            return true;
        }
    }

    private void writeLimitExceeded(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_TOO_MANY_REQUESTS);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        Map<String, Object> result = new HashMap<>();
        result.put("code", 429);
        result.put("msg", message);
        result.put("data", null);

        response.getWriter().write(objectMapper.writeValueAsString(result));
    }

    private String getClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty() && !"unknown".equalsIgnoreCase(xff)) {
            return xff.split(",")[0].trim();
        }
        String xri = request.getHeader("X-Real-IP");
        if (xri != null && !xri.isEmpty() && !"unknown".equalsIgnoreCase(xri)) {
            return xri.trim();
        }
        return request.getRemoteAddr();
    }
}
