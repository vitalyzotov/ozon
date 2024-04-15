package ru.vzotov.ozon.security;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarCookie;
import de.sstoehr.harreader.model.HarEntry;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

public interface SecurityApi {
    record FinanceAccessToken(String value) {
        public static final String COOKIE = "__OBANK_session";
    }

    record FinanceRefreshToken(String value) {
        public static final String COOKIE = "__OBANK_refresh";
    }

    record OzonAccessToken(String value) {
        public static final String COOKIE = "__Secure-access-token";
    }

    record OzonAuthentication(
            OzonUserId userId,
            OzonAccessToken accessToken,
            OzonRefreshToken refreshToken
    ) {

        public Map<String, String> cookies() {
            return Map.of(
                    OzonUserId.COOKIE, userId().value(),
                    OzonAccessToken.COOKIE, accessToken().value(),
                    OzonRefreshToken.COOKIE, refreshToken().value()
            );
        }

        public static OzonAuthentication fromHar(String input) throws IOException {
            try {
                HarReader harReader = new HarReader();
                Har har = harReader.readFromString(input);
                return fromHar(har);
            } catch (HarReaderException e) {
                throw new IOException(e);
            }
        }

        public static OzonAuthentication fromHar(File input) throws IOException {
            try {
                HarReader harReader = new HarReader();
                Har har = harReader.readFromFile(input);
                return fromHar(har);
            } catch (HarReaderException e) {
                throw new IOException(e);
            }
        }

        private static OzonAuthentication fromHar(Har har) {
            for (HarEntry entry : har.getLog().getEntries()) {
                final Map<String, String> cookies = entry.getRequest().getCookies().stream()
                        .collect(Collectors.toMap(HarCookie::getName, HarCookie::getValue, (a, b) -> a));
                final String userId = cookies.get(OzonUserId.COOKIE);
                final String accessToken = cookies.get(OzonAccessToken.COOKIE);
                final String refreshToken = cookies.get(OzonRefreshToken.COOKIE);

                if (userId != null && accessToken != null && refreshToken != null) {
                    return new OzonAuthentication(
                            new OzonUserId(userId),
                            new OzonAccessToken(accessToken),
                            new OzonRefreshToken(refreshToken)
                    );
                }
            }
            return null;
        }

        public static OzonAuthentication empty() {
            return new OzonAuthentication(
                    new OzonUserId(""),
                    new OzonAccessToken(""),
                    new OzonRefreshToken("")
            );
        }
    }

    record OzonAuthorization(
            OzonAuthentication authentication,
            FinanceAccessToken financeAccessToken,
            FinanceRefreshToken financeRefreshToken
    ) {
        public Map<String, String> cookies() {
            Map<String, String> result = new HashMap<>(authentication().cookies());
            result.put(FinanceAccessToken.COOKIE, financeAccessToken().value());
            result.put(FinanceRefreshToken.COOKIE, financeRefreshToken().value());
            return result;
        }
    }

    record OzonRefreshToken(String value) {
        public static final String COOKIE = "__Secure-refresh-token";
    }

    record OzonUserId(String value) {
        public static final String COOKIE = "__Secure-user-id";
    }

    record PinCode(String value) {
    }
}
