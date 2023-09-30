module ozon.api {
    requires org.slf4j;
    requires reactor.core;
    requires reactor.netty.core;
    requires reactor.netty.http;
    requires com.fasterxml.jackson.databind;
    requires com.fasterxml.jackson.annotation;
    requires io.netty.codec.http;
    requires org.reactivestreams;
    requires io.netty.buffer;
    requires har.reader;
    requires io.netty.handler;

    exports ru.vzotov.ozon;
    exports ru.vzotov.ozon.model;
    exports ru.vzotov.ozon.security;
    opens ru.vzotov.ozon.model to com.fasterxml.jackson.databind;
    opens ru.vzotov.ozon.security to com.fasterxml.jackson.databind;
}
