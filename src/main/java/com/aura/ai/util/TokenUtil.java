package com.aura.ai.util;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;

public class TokenUtil {

    public static String getUsernameFromToken() {
        Jwt jwt = getJwt();
        if (jwt == null) return null;
        String username = jwt.getClaimAsString("preferred_username");
        if (username != null && username.contains("@")) {
            username = username.substring(0, username.indexOf("@"));
        }
        return username;
    }

    public static String getSessionIdFromToken() {
        Jwt jwt = getJwt();
        return jwt != null ? jwt.getClaimAsString("sid") : null;
    }

    private static Jwt getJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof Jwt)) {
            return null;
        }
        return (Jwt) authentication.getPrincipal();
    }
}
