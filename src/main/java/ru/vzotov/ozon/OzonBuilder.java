package ru.vzotov.ozon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import ru.vzotov.ozon.model.AuthResponse;
import ru.vzotov.ozon.model.ClientOperations;
import ru.vzotov.ozon.model.ClientOperationsRequest;
import ru.vzotov.ozon.model.ComposerResponse;
import ru.vzotov.ozon.model.EChecks;
import ru.vzotov.ozon.model.OrderList;
import ru.vzotov.ozon.model.OrderListFilter;
import ru.vzotov.ozon.security.FinanceAccessToken;
import ru.vzotov.ozon.security.FinanceRefreshToken;
import ru.vzotov.ozon.security.OzonAuthentication;
import ru.vzotov.ozon.security.OzonAuthorization;
import ru.vzotov.ozon.security.PinCode;

import java.net.URI;
import java.util.Map;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static org.springframework.http.HttpHeaders.ACCEPT;
import static org.springframework.http.HttpHeaders.CONTENT_TYPE;
import static org.springframework.http.HttpHeaders.USER_AGENT;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;
import static org.springframework.web.util.UriComponentsBuilder.fromUri;
import static ru.vzotov.ozon.model.ComposerResponse.C_CHEQUES;
import static ru.vzotov.ozon.model.ComposerResponse.C_ORDER_LIST_APP;

public class OzonBuilder {

    public static final String OZON_API = "https://api.ozon.ru/";
    public static final String FINANCE_API = "https://finance.ozon.ru/api/";

    private WebClient.Builder webClient;

    private HttpClient httpClient;

    private ObjectMapper objectMapper;

    public OzonBuilder() {
        webClient = WebClient.builder();
        httpClient = HttpClient.create().followRedirect(true, (headers, request) -> request.headers(headers));
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public OzonBuilder webClient(UnaryOperator<WebClient.Builder> webClientOperator) {
        this.webClient = webClientOperator.apply(this.webClient);
        return this;
    }

    public OzonBuilder httpClient(UnaryOperator<HttpClient> httpClientOperator) {
        this.httpClient = httpClientOperator.apply(this.httpClient);
        return this;
    }

    public OzonBuilder objectMapper(UnaryOperator<ObjectMapper> objectMapperOperator) {
        this.objectMapper = objectMapperOperator.apply(this.objectMapper);
        return this;
    }

    private WebClient createWebClient() {
        return webClient
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .baseUrl(FINANCE_API)
                .defaultHeader(CONTENT_TYPE, APPLICATION_JSON_VALUE)
                .defaultHeader(ACCEPT, APPLICATION_JSON_VALUE)
                .build();
    }

    public Ozon authorize(Mono<OzonAuthentication> auth, Mono<PinCode> pinCode) {
        final WebClient client = createWebClient();
        final Mono<OzonAuthorization> authorization = auth.zipWith(pinCode)
                .flatMap(authenticated -> client
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
        return new AuthorizedInstance(client, authorization, objectMapper);
    }

    static class AuthorizedInstance implements Ozon {
        private final WebClient webClient;
        private final ObjectMapper mapper;
        private final Mono<OzonAuthorization> authorization;

        AuthorizedInstance(WebClient webClient, Mono<OzonAuthorization> authorization, ObjectMapper mapper) {
            this.webClient = requireNonNull(webClient);
            this.authorization = requireNonNull(authorization);
            this.mapper = requireNonNull(mapper);
        }

        private Mono<ClientOperations> clientOperationsPage(ClientOperationsRequest request) {
            return authorization.flatMap(authorization -> webClient.post()
                    .uri(b -> b.pathSegment("clientOperations.json").build())
                    .cookies(cookies -> {
                        cookies.setAll(authorization.cookies());
                    })
                    .bodyValue(request)
                    .retrieve()
                    .bodyToMono(ClientOperations.class)
            );
        }

        @Override
        public Flux<ClientOperations> clientOperations(ClientOperationsRequest request) {
            return clientOperationsPage(request)
                    .expandDeep(page -> page.hasNextPage() ?
                            clientOperationsPage(
                                    new ClientOperationsRequest(
                                            page.cursors(),
                                            request.filter(),
                                            request.page(),
                                            request.perPage()
                                    )
                            ) :
                            Mono.empty());
        }

        @Override
        public Flux<OrderList> orders(OrderListFilter filter) {
            return page("/my/orderlist")
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final OrderList item = mapToComponentState(page, C_ORDER_LIST_APP, OrderList.class);
                            return Mono.justOrEmpty(item);
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    });
        }

        private Mono<ComposerResponse> page(String pageUrl) {
            if (pageUrl == null) return Mono.empty();
            return authorization.flatMap(authorization -> webClient.get()
                    .uri(OZON_API, b -> b
                            .pathSegment("composer-api.bx", "page", "json", "v2")
                            .queryParam("url", pageUrl)
                            .build()
                    )
                    .cookies(cookies -> {
                        cookies.setAll(authorization.authentication().cookies());
                    })
                    .headers(this::defaultHeaders)
                    .retrieve()
                    .bodyToMono(ComposerResponse.class)
            );
        }

        private void defaultHeaders(HttpHeaders headers) {
            headers.set(USER_AGENT, "ozonapp_android/16.4+2333");
            headers.set("x-o3-device-type", "mobile");
        }

        @Override
        public Flux<EChecks> eChecks() {
            return page("/my/e-check?archive=1")
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final EChecks item = mapToComponentState(page, C_CHEQUES, EChecks.class);
                            return Mono.justOrEmpty(item);
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    });
        }

        @Override
        public Flux<DataBuffer> download(URI uri) {
            final String scheme = uri.getScheme();
            if (!"ozon".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Scheme " + scheme + " is not supported");
            }
            final String host = uri.getHost();
            if (!"pdf".equalsIgnoreCase(host)) {
                throw new IllegalArgumentException("Host " + host + " is not supported");
            }
            final String url = requireNonNull(fromUri(uri).build().getQueryParams().getFirst("url"));
            return authorization.flatMapMany(authorization -> webClient.get()
                    .uri(url)
                    .cookies(cookies -> {
                        cookies.setAll(authorization.authentication().cookies());
                    })
                    .retrieve()
                    .bodyToFlux(DataBuffer.class)
            );
        }

        private <T> Mono<T> mapToComponentState(Mono<ComposerResponse> in, String component, Class<T> result) {
            return in.flatMap(response -> {
                final String state = response.widgetState(component);
                if (state == null) {
                    return Mono.empty();
                } else {
                    try {
                        return Mono.just(mapper.readValue(state, result));
                    } catch (JsonProcessingException e) {
                        return Mono.error(e);
                    }
                }
            });
        }

        private <T> T mapToComponentState(ComposerResponse in, String component, Class<T> result) throws JsonProcessingException {
            final String state = in.widgetState(component);
            if (state == null) {
                return null;
            } else {
                return mapper.readValue(state, result);
            }
        }
    }
}
