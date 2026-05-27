package com.niuma.admin.util;

import java.util.regex.Pattern;

/**
 * 敏感数据脱敏工具
 *
 * <p>覆盖以下数据类型:
 * <ul>
 *   <li>手机号: 138****1234 (中间4位遮盖)</li>
 *   <li>身份证: 110***********1234 (出生日期遮盖)</li>
 *   <li>银行卡号: 6222****1234 (中间部分遮盖)</li>
 *   <li>真实姓名: 张* 或 张** (仅保留姓氏)</li>
 *   <li>Email: t***@gmail.com</li>
 *   <li>IP地址: 192.168.*.*</li>
 * </ul>
 */
public final class SensitiveDataUtil {

    private static final Pattern PHONE_PATTERN = Pattern.compile("^1\\d{10}$");
    private static final Pattern IDCARD_PATTERN = Pattern.compile("^\\d{17}[\\dXx]$");
    private static final Pattern BANK_CARD_PATTERN = Pattern.compile("^\\d{16,19}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[\\w.-]+@[\\w.-]+\\.\\w+$");
    private static final Pattern IP_PATTERN = Pattern.compile(
            "^((25[0-5]|2[0-4]\\d|[01]?\\d\\d?)\\.){3}(25[0-5]|2[0-4]\\d|[01]?\\d\\d?)$");

    private SensitiveDataUtil() {}

    /**
     * 自动识别并脱敏
     */
    public static String desensitize(String value) {
        if (value == null || value.isEmpty()) return value;

        if (PHONE_PATTERN.matcher(value).matches()) {
            return desensitizePhone(value);
        }
        if (IDCARD_PATTERN.matcher(value).matches()) {
            return desensitizeIdCard(value);
        }
        if (BANK_CARD_PATTERN.matcher(value).matches()) {
            return desensitizeBankCard(value);
        }
        if (EMAIL_PATTERN.matcher(value).matches()) {
            return desensitizeEmail(value);
        }
        if (IP_PATTERN.matcher(value).matches()) {
            return desensitizeIp(value);
        }

        // 默认: 如果长度>2, 保留首尾
        if (value.length() <= 2) return value;
        if (value.length() <= 4) return value.charAt(0) + "***";

        return value.charAt(0) + repeat('*', value.length() - 2) + value.charAt(value.length() - 1);
    }

    /** 手机号脱敏: 138****1234 */
    public static String desensitizePhone(String phone) {
        if (phone == null || phone.length() != 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    /** 身份证脱敏: 110***********123X */
    public static String desensitizeIdCard(String idCard) {
        if (idCard == null || idCard.length() != 18) return idCard;
        return idCard.substring(0, 3) + "************" + idCard.substring(15);
    }

    /** 银行卡号脱敏: 6222****1234 */
    public static String desensitizeBankCard(String cardNo) {
        if (cardNo == null || cardNo.length() < 8) return cardNo;
        int maskLen = Math.min(cardNo.length() - 4, 12);
        int start = (cardNo.length() - maskLen) / 2;
        return cardNo.substring(0, start) + repeat('*', maskLen)
                + cardNo.substring(start + maskLen);
    }

    /** 姓名脱敏: 张* 或 欧阳** */
    public static String desensitizeName(String name) {
        if (name == null || name.isEmpty()) return name;
        if (name.length() == 1) return "*";
        if (name.length() == 2) return name.charAt(0) + "*";
        return name.charAt(0) + repeat('*', name.length() - 1);
    }

    /** 邮箱脱敏: t***@gmail.com */
    public static String desensitizeEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        int atIndex = email.indexOf('@');
        String localPart = email.substring(0, atIndex);
        String domain = email.substring(atIndex);

        if (localPart.length() <= 1) return "*" + domain;
        return localPart.charAt(0) + repeat('*', Math.min(localPart.length() - 1, 3)) + domain;
    }

    /** IP地址脱敏: 192.168.*.* */
    public static String desensitizeIp(String ip) {
        if (ip == null) return ip;
        String[] parts = ip.split("\\.");
        if (parts.length != 4) return ip;
        return parts[0] + "." + parts[1] + ".*.*";
    }

    /** 自定义脱敏: 保留前prefix和后suffix位, 中间用maskChar替代 */
    public static String custom(String value, int prefix, int suffix, char maskChar) {
        if (value == null || value.isEmpty()) return value;
        if (prefix + suffix >= value.length()) return value;

        return value.substring(0, prefix)
                + repeat(maskChar, value.length() - prefix - suffix)
                + value.substring(value.length() - suffix);
    }

    private static String repeat(char c, int count) {
        StringBuilder sb = new StringBuilder(count);
        for (int i = 0; i < count; i++) sb.append(c);
        return sb.toString();
    }
}
