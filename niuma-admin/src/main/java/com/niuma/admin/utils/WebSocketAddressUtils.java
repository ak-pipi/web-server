package com.niuma.admin.utils;

import com.niuma.common.utils.StringUtils;

public final class WebSocketAddressUtils {
    private static final String WS_PREFIX = "ws://";
    private static final String WSS_PREFIX = "wss://";

    private WebSocketAddressUtils() {
    }

    public static String ensureSecure(String address) {
        if (StringUtils.isEmpty(address)) {
            return address;
        }
        String normalized = address.trim();
        if (normalized.regionMatches(true, 0, WS_PREFIX, 0, WS_PREFIX.length())) {
            return WSS_PREFIX + normalized.substring(WS_PREFIX.length());
        }
        return normalized;
    }
}
