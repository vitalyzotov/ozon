package ru.vzotov.ozon.security;

public record OzonRefreshToken(String value) {
    public static final String COOKIE = "__Secure-refresh-token";
}
