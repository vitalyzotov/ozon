package ru.vzotov.ozon;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.QueryStringEncoder;
import io.netty.handler.codec.http.cookie.ClientCookieDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.DefaultCookie;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
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

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static ru.vzotov.ozon.model.ComposerResponse.C_CHEQUES;
import static ru.vzotov.ozon.model.ComposerResponse.C_ORDER_LIST_APP;

public class OzonBuilder {

    public static final String OZON_API = "https://api.ozon.ru/";
    public static final String FINANCE_API = "https://finance.ozon.ru/api/";

    private HttpClient httpClient;

    private ObjectMapper objectMapper;

    public OzonBuilder() {
        AtomicReference<List<Cookie>> newCookies = new AtomicReference<>();
        httpClient = HttpClient.create()
                .baseUrl(OZON_API)
                .doOnRedirect((res, conn) -> {
                    final List<String> cookies = res.responseHeaders().getAll(HttpHeaderNames.SET_COOKIE);
                    if (cookies != null) {
                        newCookies.set(
                                cookies.stream().map(ClientCookieDecoder.LAX::decode).toList()
                        );
                    }

                })
                .followRedirect(true, (headers, request) -> {

                    final List<Cookie> cookies = newCookies.get();
                    if (cookies != null) {
                        for (Cookie cookie : cookies) {
                            request.addCookie(cookie);
                        }
                    }
                    request.headers(headers);
                });
        objectMapper = new ObjectMapper()
                .findAndRegisterModules()
                .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    }

    public OzonBuilder httpClient(UnaryOperator<HttpClient> httpClientOperator) {
        this.httpClient = httpClientOperator.apply(this.httpClient);
        return this;
    }

    @SuppressWarnings("unused")
    public OzonBuilder objectMapper(UnaryOperator<ObjectMapper> objectMapperOperator) {
        this.objectMapper = objectMapperOperator.apply(this.objectMapper);
        return this;
    }

    private HttpClient createHttpClient() {
        return httpClient
                .baseUrl(FINANCE_API)
                .headers(headers -> headers
                        .add(HttpHeaderNames.CONTENT_TYPE, HttpHeaderValues.APPLICATION_JSON)
                        .add(HttpHeaderNames.ACCEPT, HttpHeaderValues.APPLICATION_JSON)
                );
    }

    private static <T> Mono<String> toJson(ObjectMapper objectMapper, T value) {
        try {
            return Mono.just(objectMapper.writeValueAsString(value));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    private static <T> ByteBufMono toJsonBytes(ObjectMapper objectMapper, T value) {
        return ByteBufMono.fromString(toJson(objectMapper, value));
    }

    protected static <T> Mono<T> fromJson(ObjectMapper objectMapper, ByteBufMono body, Class<T> reference) {
        return body.asString().flatMap(in -> {
            try {
                //log.debug("fromJson: {}", in);
                return Mono.just(objectMapper.readValue(in, reference));
            } catch (IOException e) {
                return Mono.error(e);
            }
        });
    }


    public Ozon authorize(Mono<OzonAuthentication> auth, Mono<PinCode> pinCode) {
        final HttpClient client = createHttpClient();
        final Mono<OzonAuthorization> authorization = auth.zipWith(pinCode)
                .flatMap(authenticated -> {
                    return client
                            .post()
                            .uri("/authorize.json")
                            .send((req, out) -> {
                                authenticated.getT1().cookies()
                                        .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                                return out.send(toJsonBytes(objectMapper, Map.of("pincode", authenticated.getT2().value())));
                            })
                            .responseSingle((res, body) -> fromJson(objectMapper, body, AuthResponse.class))
                            .map(response -> new OzonAuthorization(
                                    authenticated.getT1(),
                                    new FinanceAccessToken(response.authToken()),
                                    new FinanceRefreshToken(response.refreshToken())
                            ));
                })
                .cache(s -> Duration.ofMillis(Long.MAX_VALUE), e -> Duration.ZERO, () -> Duration.ZERO);
        return new AuthorizedInstance(client, authorization, objectMapper);
    }

    static class AuthorizedInstance implements Ozon {
        private final HttpClient httpClient;
        private final ObjectMapper mapper;
        private final Mono<OzonAuthorization> authorization;

        AuthorizedInstance(HttpClient httpClient, Mono<OzonAuthorization> authorization, ObjectMapper mapper) {
            this.httpClient = requireNonNull(httpClient);
            this.authorization = requireNonNull(authorization);
            this.mapper = requireNonNull(mapper);
        }

        private Mono<ClientOperations> clientOperationsPage(ClientOperationsRequest request) {
            return authorization.single().flatMap(authorization ->
                    httpClient.post().uri("/clientOperations.json")
                            .send((req, out) -> {
                                authorization.cookies()
                                        .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                                return out.send(toJsonBytes(mapper, request));
                            })
                            .responseSingle((res, body) -> fromJson(mapper, body, ClientOperations.class))
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
            final QueryStringEncoder uri = new QueryStringEncoder(OZON_API + "composer-api.bx/page/json/v2");
            uri.addParam("url", pageUrl);
            return authorization.single().flatMap(authorization -> httpClient
                    .doOnRequest((req, conn) -> {
                        authorization.authentication().cookies()
                                .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                    })
                    .headers(this::defaultHeaders)
                    .get()
                    .uri(uri.toString())
                    .responseSingle((res, body) -> fromJson(mapper, body, ComposerResponse.class))
            );
        }

        private void defaultHeaders(HttpHeaders headers) {
            headers.set(HttpHeaderNames.USER_AGENT, "ozonapp_android/16.4+2333");
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
        public Flux<ByteBuf> download(URI uri) {
            final String scheme = uri.getScheme();
            if (!"ozon".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Scheme " + scheme + " is not supported");
            }
            final String host = uri.getHost();
            if (!"pdf".equalsIgnoreCase(host)) {
                throw new IllegalArgumentException("Host " + host + " is not supported");
            }
            final QueryStringDecoder decoder = new QueryStringDecoder(uri);
            final String url = requireNonNull(decoder.parameters().get("url")).stream().findFirst()
                    .orElseThrow(NullPointerException::new);
            return authorization.single().flatMapMany(authorization -> httpClient
                    .headers(headers -> headers
                            .remove(HttpHeaderNames.CONTENT_TYPE)
                            .remove(HttpHeaderNames.ACCEPT)
                    )
                    .doOnRequest((req, conn) -> {
                        authorization.authentication().cookies()
                                .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                    })
                    .get()
                    .uri(url)
                    .response((res, body) -> {
                        if (!HttpResponseStatus.OK.equals(res.status())) {
                            return Mono.error(new IllegalStateException(res.status().toString()));
                        } else {
                            return body;
                        }
                    })
            );
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
