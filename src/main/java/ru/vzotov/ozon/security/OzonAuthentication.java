package ru.vzotov.ozon.security;

import de.sstoehr.harreader.HarReader;
import de.sstoehr.harreader.HarReaderException;
import de.sstoehr.harreader.model.Har;
import de.sstoehr.harreader.model.HarCookie;
import de.sstoehr.harreader.model.HarEntry;

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

public record OzonAuthentication(
        OzonUserId userId,
        OzonAccessToken accessToken,
        OzonRefreshToken refreshToken) {

    public Map<String, String> cookies() {
        return Map.of(
                OzonUserId.COOKIE, userId().value(),
                OzonAccessToken.COOKIE, accessToken().value(),
                OzonRefreshToken.COOKIE, refreshToken().value()
        );
    }

    public static OzonAuthentication fromHar(File input) throws IOException {
        try {
            HarReader harReader = new HarReader();
            Har har = harReader.readFromFile(input);

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
        } catch (HarReaderException e) {
            throw new IOException(e);
        }
        return null;
    }
}
