package ru.vzotov.ozon.security;

public record OzonUserId(String value) {
    public static final String COOKIE = "__Secure-user-id";
}
