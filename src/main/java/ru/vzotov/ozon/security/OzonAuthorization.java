package ru.vzotov.ozon.security;

import java.util.HashMap;
import java.util.Map;

public record OzonAuthorization(
        OzonAuthentication authentication,
        FinanceAccessToken financeAccessToken,
        FinanceRefreshToken financeRefreshToken) {
    public Map<String, String> cookies() {
        Map<String, String> result = new HashMap<>(authentication().cookies());
        result.put(FinanceAccessToken.COOKIE, financeAccessToken().value());
        result.put(FinanceRefreshToken.COOKIE, financeRefreshToken().value());
        return result;
    }
}
