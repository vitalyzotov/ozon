package ru.vzotov.ozon.security;

public record FinanceRefreshToken(String value) {
    public static final String COOKIE = "__OBANK_refresh";
}
