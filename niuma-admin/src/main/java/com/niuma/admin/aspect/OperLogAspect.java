package com.niuma.admin.aspect;

import com.niuma.common.core.domain.entity.SysUser;
import com.niuma.common.utils.SecurityUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.stereotype.Component;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志切面
 *
 * <p>全量覆盖所有 Controller 写操作 (POST/PUT/DELETE),
 * 记录操作人、IP、方法、参数、耗时等信息。
 *
 * <p>日志格式:
 * [操作日志] user={} | ip={} | method={} | uri={} | params={} | cost={}ms | result={}
 */
@Slf4j
@Aspect
@Component
public class OperLogAspect {

    /**
     * 切入点: 所有 Controller 中的写操作
     */
    @Pointcut(
            "execution(* com.niuma.admin.controller..*.*(..)) && " +
            "( @annotation(org.springframework.web.bind.annotation.PostMapping) ||" +
            "  @annotation(org.springframework.web.bind.annotation.PutMapping) ||" +
            "  @annotation(org.springframework.web.bind.annotation.DeleteMapping) )"
    )
    public void writeOperation() {}

    /**
     * 环绕通知: 记录操作日志
     */
    @Around("writeOperation()")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 获取请求信息
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String methodName = joinPoint.getSignature().getName();
        Object[] args = joinPoint.getArgs();
        HttpServletRequest request = extractRequest(args);

        try {
            Object result = joinPoint.proceed();
            long costMs = System.currentTimeMillis() - startTime;

            logOper(className, methodName, request, args, costMs, "SUCCESS", null);
            return result;
        } catch (Throwable e) {
            long costMs = System.currentTimeMillis() - startTime;

            logOper(className, methodName, request, args, costMs, "FAILED", e.getMessage());
            throw e;
        }
    }

    private void logOper(String className, String methodName,
                         HttpServletRequest request, Object[] args,
                         long costMs, String status, String errorMsg) {
        try {
            SysUser currentUser = null;
            try {
                currentUser = SecurityUtils.getLoginUser().getUser();
            } catch (Exception ignored) {}

            String username = currentUser != null ? currentUser.getUserName() : "anonymous";
            String clientIp = request != null ? getClientIp(request) : "-";

            Map<String, Object> operLog = new HashMap<>();
            operLog.put("user", username);
            operLog.put("ip", clientIp);
            operLog.put("class", className);
            operLog.put("method", methodName);
            operLog.put("params", sanitizeArgs(args));
            operLog.put("costMs", costMs);
            operLog.put("status", status);
            if (errorMsg != null) {
                operLog.put("error", errorMsg);
            }

            log.info("[操作日志] {}", operLog);

            // TODO: 可选 - 异步写入 sys_oper_log 表做持久化存储

        } catch (Exception e) {
            log.error("[操作日志] 记录失败: error={}", e.getMessage(), e);
        }
    }

    private HttpServletRequest extractRequest(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof HttpServletRequest) {
                return (HttpServletRequest) arg;
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        if (request == null) return "-";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isEmpty()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }

    /**
     * 脱敏处理参数 (避免记录密码/token等敏感信息)
     */
    private Object sanitizeArgs(Object[] args) {
        if (args == null || args.length == 0) return "[]";

        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < args.length; i++) {
            if (i > 0) sb.append(", ");
            Object arg = args[i];

            // 跳过非业务对象
            if (arg instanceof HttpServletRequest ||
                arg instanceof HttpServletResponse ||
                arg instanceof javax.servlet.http.HttpSession) {
                sb.append(arg.getClass().getSimpleName()).append("(skipped)");
                continue;
            }

            // 对字符串参数做简单脱敏
            if (arg instanceof String) {
                String str = (String) arg;
                if (str.length() > 200) {
                    sb.append(str.substring(0, 200)).append("...(truncated)");
                } else {
                    sb.append(str.contains("password") ? "***" : str);
                }
            } else {
                // TODO: 更精细的 JSON 字段级脱敏 (手机号/身份证/token等)
                sb.append(arg.getClass().getSimpleName())
                  .append("@").append(System.identityHashCode(arg));
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
