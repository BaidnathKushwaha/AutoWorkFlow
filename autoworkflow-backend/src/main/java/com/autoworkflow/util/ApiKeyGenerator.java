package com.autoworkflow.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class ApiKeyGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();

    private ApiKeyGenerator() {}

    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return "awf_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String lastFour(String key) {
        if (key == null || key.length() < 4) return key;
        return key.substring(key.length() - 4);
    }
}
