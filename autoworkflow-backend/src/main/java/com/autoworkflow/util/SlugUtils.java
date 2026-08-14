package com.autoworkflow.util;

import java.security.SecureRandom;
import java.util.Base64;

public final class SlugUtils {
    private static final SecureRandom RANDOM = new SecureRandom();

    private SlugUtils() {}

    public static String randomToken(int byteLength) {
        byte[] bytes = new byte[byteLength];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
