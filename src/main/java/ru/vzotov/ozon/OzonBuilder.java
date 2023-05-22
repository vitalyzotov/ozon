package ru.vzotov.ozon;

import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import ru.vzotov.ozon.model.AuthResponse;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsRequest;
import ru.vzotov.ozon.security.FinanceAccessToken;
import ru.vzotov.ozon.security.FinanceRefreshToken;
import ru.vzotov.ozon.security.OzonAuthentication;
import ru.vzotov.ozon.security.OzonAuthorization;
import ru.vzotov.ozon.security.PinCode;

import java.util.Map;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class OzonBuilder {

    private final WebClient webClient;

    public OzonBuilder() {
        this(defaultWebClient().build());
    }

    public OzonBuilder(WebClient webClient) {
        this.webClient = webClient;
    }

    public Ozon authorize(Mono<OzonAuthentication> auth, Mono<PinCode> pinCode) {
        final Mono<OzonAuthorization> authorization = auth.zipWith(pinCode)
                .flatMap(authenticated -> webClient
                        .post()
                        .uri(b -> b.pathSegment("authorize.json").build())
                        .bodyValue(Map.of("pincode", authenticated.getT2().value()))
                        .cookies(cookies -> {
                            cookies.setAll(authenticated.getT1().cookies());
                        })
                        .retrieve()
                        .bodyToMono(AuthResponse.class)
                        .map(response -> new OzonAuthorization(
                                authenticated.getT1(),
                                new FinanceAccessToken(response.authToken()),
                                new FinanceRefreshToken(response.refreshToken())
                        )))
                .cache();
        return new AuthorizedInstance(webClient, authorization);
    }

    public static WebClient.Builder defaultWebClient() {
        return WebClient.builder()
                .baseUrl("https://finance.ozon.ru/api/")
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE);
    }

    static class AuthorizedInstance implements Ozon {
        private final WebClient webClient;
        private final Mono<OzonAuthorization> authorization;

        public AuthorizedInstance(WebClient webClient, Mono<OzonAuthorization> authorization) {
            this.webClient = requireNonNull(webClient);
            this.authorization = requireNonNull(authorization);
        }

        @Override
        public Mono<ClientOperations> clientOperations(Mono<ClientOperationsRequest> request) {
            return authorization.flatMap(authorization -> webClient.post()
                    .uri(b -> b.pathSegment("clientOperations.json").build())
                    .cookies(cookies -> {
                        cookies.setAll(authorization.cookies());
                    })
                    .body(request, ClientOperationsRequest.class)
                    .retrieve()
                    .bodyToMono(ClientOperations.class)
            );
        }
    }
}
