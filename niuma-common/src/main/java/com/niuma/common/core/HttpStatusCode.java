package com.niuma.common.core;

/**
 * HTTP 状态码常量
 * <p>
 * 补充 javax.servlet.HttpServletResponse 中缺失的高版本 (Servlet 4.0+) 状态码,
 * 使项目在 JDK 8 / Servlet 3.x 环境下仍可使用标准化的状态码命名。
 *
 * @see javax.servlet.HttpServletResponse
 */
public final class HttpStatusCode {

    private HttpStatusCode() {
        // 常量类禁止实例化
    }

    // ==================== Servlet 4.0+ 新增 ====================

    /** 208 Already Reported */
    public static final int SC_ALREADY_REPORTED = 208;

    /** 226 IM Used */
    public static final int SC_IM_USED = 226;

    // ==================== Servlet 5.0 / HTTP 新增 ====================

    /** 308 Permanent Redirect */
    public static final int SC_PERMANENT_REDIRECT = 308;

    /** 429 Too Many Requests (RFC 6585) */
    public static final int SC_TOO_MANY_REQUESTS = 429;

    /** 451 Unavailable For Legal Reasons (RFC 7725) */
    public static final int SC_UNAVAILABLE_FOR_LEGAL_REASONS = 451;
}
