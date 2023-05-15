package ru.vzotov.ozon.security;

public record FinanceAccessToken(String value) {
    public static final String COOKIE = "__OBANK_session";
}
