package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.Objects;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public final class Check implements OzonRecord {
    private static final DateTimeFormatter SUBTITLE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
            .appendLiteral(' ')
            .appendText(MONTH_OF_YEAR, TextStyle.FULL)
            .appendLiteral(' ')
            .appendValue(YEAR, 4)
            .appendLiteral(" в ")
            .appendValue(HOUR_OF_DAY, 2)
            .appendLiteral(':')
            .appendValue(MINUTE_OF_HOUR, 2)
            .toFormatter(new Locale("ru"));
    private final String title;
    private final String subtitle;
    private final String price;
    private final String link;
    private final String deeplink;
    private final Object trackingInfo;
    private final ComposerResponse.Button button;

    @JsonCreator
    public Check(
            @JsonProperty("title")
            String title,
            @JsonProperty("subtitle")
            String subtitle,
            @JsonProperty("price")
            String price,
            @JsonProperty("link")
            String link,
            @JsonProperty("deeplink")
            String deeplink,
            @JsonProperty("trackingInfo")
            Object trackingInfo,
            @JsonProperty("button")
            ComposerResponse.Button button
    ) {
        this.title = title;
        this.subtitle = subtitle;
        this.price = price;
        this.link = link;
        this.deeplink = deeplink;
        this.trackingInfo = trackingInfo;
        this.button = button;
    }

    @JsonIgnore
    private LocalDateTime dateTime;

    public LocalDate date() {
        return dateTime().toLocalDate();
    }

    public LocalDateTime dateTime() {
        if (dateTime == null) {
            try {
                dateTime = LocalDateTime.parse(subtitle(), SUBTITLE_FORMATTER);
            } catch (DateTimeParseException e) {
                dateTime = LocalDateTime.MIN;
            }
        }
        return dateTime;
    }

    public String title() {
        return title;
    }

    public String subtitle() {
        return subtitle;
    }

    public String price() {
        return price;
    }

    public String link() {
        return link;
    }

    public String deeplink() {
        return deeplink;
    }

    public Object trackingInfo() {
        return trackingInfo;
    }

    public ComposerResponse.Button button() {
        return button;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Check) obj;
        return Objects.equals(this.title, that.title) &&
                Objects.equals(this.subtitle, that.subtitle) &&
                Objects.equals(this.price, that.price) &&
                Objects.equals(this.link, that.link) &&
                Objects.equals(this.deeplink, that.deeplink) &&
                Objects.equals(this.trackingInfo, that.trackingInfo) &&
                Objects.equals(this.button, that.button);
    }

    @Override
    public int hashCode() {
        return Objects.hash(title, subtitle, price, link, deeplink, trackingInfo, button);
    }

    @Override
    public String toString() {
        return "Check[" +
                "title=" + title + ", " +
                "subtitle=" + subtitle + ", " +
                "price=" + price + ", " +
                "link=" + link + ", " +
                "deeplink=" + deeplink + ", " +
                "trackingInfo=" + trackingInfo + ", " +
                "button=" + button + ']';
    }

}
