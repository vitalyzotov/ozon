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

import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

public class Ozon {

    private final WebClient webClient;

    public Ozon() {
        this(defaultWebClient().build());
    }

    public Ozon(WebClient webClient) {
        this.webClient = webClient;
    }

    public Mono<OzonAuthorization> authorize(Mono<OzonAuthentication> auth, Mono<PinCode> pinCode) {
        return auth.flatMap(authenticated -> webClient.post()
                .uri(b -> b.pathSegment("authorize.json").build())
                .body(pinCode.map(pin -> Map.of("pincode", pin.value())), Map.class)
                .cookies(cookies -> {
                    cookies.setAll(authenticated.cookies());
                })
                .retrieve()
                .bodyToMono(AuthResponse.class)
                .map(response -> new OzonAuthorization(
                        authenticated,
                        new FinanceAccessToken(response.authToken()),
                        new FinanceRefreshToken(response.refreshToken())
                )));
    }

    public Mono<ClientOperations> clientOperations(Mono<OzonAuthorization> auth, Mono<ClientOperationsRequest> request) {
        return auth.flatMap(authorization -> webClient.post()
                .uri(b -> b.pathSegment("clientOperations.json").build())
                .cookies(cookies -> {
                    cookies.setAll(authorization.cookies());
                })
                .body(request, ClientOperationsRequest.class)
                .retrieve()
                .bodyToMono(ClientOperations.class)
        );
    }

    public static WebClient.Builder defaultWebClient() {
        return WebClient.builder()
                .baseUrl("https://finance.ozon.ru/api/")
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE);
    }

}
