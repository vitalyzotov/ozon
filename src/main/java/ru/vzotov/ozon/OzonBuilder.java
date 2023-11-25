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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufMono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import ru.vzotov.ozon.model.OzonApi;
import ru.vzotov.ozon.security.SecurityApi;

import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;
import java.util.function.UnaryOperator;

import static java.util.Objects.requireNonNull;
import static ru.vzotov.ozon.model.OzonApi.ComposerResponse.C_CHEQUES;
import static ru.vzotov.ozon.model.OzonApi.ComposerResponse.C_ORDER_LIST_APP;

public class OzonBuilder {

    private static final Logger log = LoggerFactory.getLogger(OzonBuilder.class);

    public static final String OZON_API = "https://api.ozon.ru/";
    public static final String FINANCE_API = "https://finance.ozon.ru/api/v2/";

    private static final boolean DEBUG = Boolean.getBoolean("ozon.debug");

    private HttpClient httpClient;

    private ObjectMapper objectMapper;

    public OzonBuilder() {
        AtomicReference<List<Cookie>> newCookies = new AtomicReference<>();
        httpClient = HttpClient.create()
                .baseUrl(OZON_API)
                .doOnRedirect((res, conn) -> {
                    log.debug("doOnRedirect {}", res);
                    final List<String> cookies = res.responseHeaders().getAll(HttpHeaderNames.SET_COOKIE);
                    if (cookies != null) {
                        newCookies.set(
                                cookies.stream().map(ClientCookieDecoder.LAX::decode).toList()
                        );
                    }

                })
                .followRedirect(true, (headers, request) -> {
                    log.debug("followRedirect {}; headers {}", request, headers);
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
                log.debug("fromJson: {}", DEBUG ? prettyPrint(in) : in);
                return Mono.just(objectMapper.readValue(in, reference));
            } catch (IOException e) {
                return Mono.error(e);
            }
        });
    }

    private static String prettyPrint(String json) throws JsonProcessingException {
        final ObjectMapper mapper = new ObjectMapper();
        return mapper.writerWithDefaultPrettyPrinter().writeValueAsString(mapper.readValue(json, Object.class));
    }

    private Mono<OzonApi.AuthResponse> handleAuthResponse(HttpClientResponse res, ByteBufMono body) {
        return fromJson(objectMapper, body, OzonApi.AuthResponseV2.class)
                .flatMap(v2 -> {
                    if (v2.ok() != null && !v2.ok()) {
                        return Mono.empty();
                    } else {
                        final Cookie accessCookie = res.cookies().get(SecurityApi.FinanceAccessToken.COOKIE).stream().findFirst().orElse(null);
                        final Cookie refreshCookie = res.cookies().get(SecurityApi.FinanceRefreshToken.COOKIE).stream().findFirst().orElse(null);
                        return accessCookie == null || refreshCookie == null ? Mono.empty() : Mono.just(new OzonApi.AuthResponse(
                                accessCookie.value(),
                                refreshCookie.value(),
                                accessCookie.maxAge()
                        ));
                    }
                });
    }

    public Ozon authorize(Mono<SecurityApi.OzonAuthentication> auth, Mono<SecurityApi.PinCode> pinCode) {
        final HttpClient client = createHttpClient();
        final Mono<SecurityApi.OzonAuthorization> authorization = auth.zipWith(pinCode)
                .flatMap(authenticated -> {

                    return client
                            .post()
                            .uri("/auth_login")
                            .send((req, out) -> {
                                authenticated.getT1().cookies()
                                        .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                                return out.send(toJsonBytes(objectMapper, Map.of("pincode", authenticated.getT2().value())));
                            })
                            .responseSingle(this::handleAuthResponse)
                            .map(response -> new SecurityApi.OzonAuthorization(
                                    authenticated.getT1(),
                                    new SecurityApi.FinanceAccessToken(response.authToken()),
                                    new SecurityApi.FinanceRefreshToken(response.refreshToken())
                            ));
                })
                .cache(s -> Duration.ofMillis(Long.MAX_VALUE), e -> Duration.ZERO, () -> Duration.ZERO);
        return new AuthorizedInstance(client, authorization, objectMapper);
    }

    static class AuthorizedInstance implements Ozon {
        private final HttpClient httpClient;
        private final ObjectMapper mapper;
        private final Mono<SecurityApi.OzonAuthorization> authorization;

        AuthorizedInstance(HttpClient httpClient, Mono<SecurityApi.OzonAuthorization> authorization, ObjectMapper mapper) {
            this.httpClient = requireNonNull(httpClient);
            this.authorization = requireNonNull(authorization);
            this.mapper = requireNonNull(mapper);
        }

        private Mono<OzonApi.ClientOperations> clientOperationsPage(OzonApi.ClientOperationsRequest request) {
            return authorization.single().flatMap(authorization ->
                    httpClient.post().uri("/clientOperations")
                            .send((req, out) -> {
                                authorization.cookies()
                                        .forEach((name, value) -> req.addCookie(new DefaultCookie(name, value)));
                                return out.send(toJsonBytes(mapper, request));
                            })
                            .responseSingle((res, body) -> fromJson(mapper, body, OzonApi.ClientOperations.class))
            );
        }

        @Override
        public Flux<OzonApi.ClientOperations> clientOperations(OzonApi.ClientOperationsRequest request) {
            return clientOperationsPage(request)
                    .expandDeep(page -> page.hasNextPage() ?
                            clientOperationsPage(
                                    new OzonApi.ClientOperationsRequest(
                                            page.cursors(),
                                            request.filter(),
                                            request.page(),
                                            request.perPage()
                                    )
                            ) :
                            Mono.empty());
        }

        @Override
        public Flux<OzonApi.OrderList> orders(OzonApi.OrderListFilter filter) {
            return page("/my/orderlist")
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final OzonApi.OrderList item = mapToComponentState(page, C_ORDER_LIST_APP, OzonApi.OrderList.class).stream().findFirst().orElse(null);
                            return Mono.justOrEmpty(item);
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    });
        }

        @Override
        public Flux<OzonApi.OrderDetailsPage> orderDetails(String orderId) {
            return page("/my/orderDetails/?order=%s".formatted(orderId))
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final OzonApi.OrderTotal total = mapToComponentState(page, "orderTotal", OzonApi.OrderTotal.class).stream().findFirst().orElse(null);
                            final OzonApi.OrderActions actions = mapToComponentState(page, "orderActions", OzonApi.OrderActions.class).stream().findFirst().orElse(null);
                            final Set<OzonApi.ShipmentWidget> shipmentWidget = mapToComponentState(page, "shipmentWidget", OzonApi.ShipmentWidget.class);
                            return Mono.justOrEmpty(new OzonApi.OrderDetailsPage(total, actions, shipmentWidget));
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    });
        }

        @Override
        public Flux<OzonApi.OrderDetailsPosting> orderDetailsPosting(String uri) {
            if (!uri.startsWith("ozon://my/orderDetailsPosting")) throw new IllegalArgumentException();
            return page(uri.substring("ozon:/".length()))
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final Set<OzonApi.SellerProducts> sellerProducts = mapToComponentState(page, "sellerProducts", OzonApi.SellerProducts.class);
                            log.debug("SellerProducts: {}", sellerProducts);
                            return Mono.justOrEmpty(new OzonApi.OrderDetailsPosting(
                                    sellerProducts.stream()
                                            .map(i -> i instanceof OzonApi.SellerProductsList list ? list : null)
                                            .filter(Objects::nonNull).findFirst().orElse(null)
                            ));
                        } catch (JsonProcessingException e) {
                            return Mono.error(e);
                        }
                    });
        }

        private Mono<OzonApi.ComposerResponse> page(String pageUrl) {
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
                    .responseSingle((res, body) -> {
                        log.debug("parse json page response: {}", pageUrl);
                        return fromJson(mapper, body, OzonApi.ComposerResponse.class);
                    })
                    .map(response -> {
                        if (DEBUG) {
                            for (Map.Entry<String, String> entry : response.widgetStates().entrySet()) {
                                try {
                                    log.debug("State {}: {}", entry.getKey(), prettyPrint(entry.getValue()));
                                } catch (JsonProcessingException e) {
                                    log.error("Parse error", e);
                                }
                            }
                        }
                        return response;
                    })
            );
        }

        private void defaultHeaders(HttpHeaders headers) {
            headers.set(HttpHeaderNames.USER_AGENT, "ozonapp_android/16.4+2333");
            headers.set("x-o3-device-type", "mobile");
        }

        @Override
        public Flux<OzonApi.EChecks> eChecks() {
            return page("/my/e-check?archive=1")
                    .expandDeep(composerResponse -> page(composerResponse.nextPage()))
                    .flatMap(page -> {
                        try {
                            final OzonApi.EChecks item = mapToComponentState(page, C_CHEQUES, OzonApi.EChecks.class)
                                    .stream().findFirst().orElse(null);
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
                            .remove(HttpHeaderNames.USER_AGENT)
                            .add(HttpHeaderNames.USER_AGENT, "ozonapp_android/16.16.0+2366")
                            .add(HttpHeaderNames.ACCEPT, "application/json; charset=utf-8")
                            //.add(HttpHeaderNames.ACCEPT_ENCODING, "br,gzip")
                            .add("no-authorization", "false")
                            .add("x-o3-app-name", "ozonapp_android")
                            .add("x-o3-app-version", "16.16.0(2366)")
                            .add("x-o3-device-type", "mobile")
                            .add("x-o3-sample-trace", "false")
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

        private <T> Set<T> mapToComponentState(OzonApi.ComposerResponse in, String component, Class<T> resultClass) throws JsonProcessingException {
            final Set<String> state = in.widgetState(component);
            if (state == null) {
                throw new IllegalStateException();
            } else {
                final Set<T> result = new LinkedHashSet<>(state.size());
                for (String s : state) {
                    result.add(mapper.readValue(s, resultClass));
                }
                return result;
            }
        }
    }
}
