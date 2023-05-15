package ru.vzotov.ozon.security;

public record OzonAccessToken(String value) {
    public static final String COOKIE = "__Secure-access-token";
}
