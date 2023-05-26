package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Objects;

import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public final class Order implements OzonRecord {

    private static final DateTimeFormatter TITLE_FORMATTER = new DateTimeFormatterBuilder()
            .parseCaseInsensitive()
            .appendLiteral("Заказ от ")
            .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
            .appendLiteral(' ')
            .appendText(MONTH_OF_YEAR, TextStyle.FULL)
            .appendLiteral(' ')
            .appendValue(YEAR, 4)
            .toFormatter();


    private final Header header;
    private final String deeplink;
    private final List<Section> sections;

    @JsonIgnore
    private LocalDate date;

    @JsonCreator
    public Order(
            @JsonProperty("header")
            Header header,
            @JsonProperty("deeplink")
            String deeplink,
            @JsonProperty("sections")
            List<Section> sections
    ) {
        this.header = header;
        this.deeplink = deeplink;
        this.sections = sections;
    }

    @Override
    public LocalDate date() {
        if (date == null) {
            try {
                date = LocalDate.parse(header().title(), TITLE_FORMATTER);
            } catch (DateTimeParseException e) {
                date = LocalDate.MIN;
            }
        }
        return date;

    }

    public Header header() {
        return header;
    }

    public String deeplink() {
        return deeplink;
    }

    public List<Section> sections() {
        return sections;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        if (obj == null || obj.getClass() != this.getClass()) return false;
        var that = (Order) obj;
        return Objects.equals(this.header, that.header) &&
                Objects.equals(this.deeplink, that.deeplink) &&
                Objects.equals(this.sections, that.sections);
    }

    @Override
    public int hashCode() {
        return Objects.hash(header, deeplink, sections);
    }

    @Override
    public String toString() {
        return "Order[" +
                "header=" + header + ", " +
                "deeplink=" + deeplink + ", " +
                "sections=" + sections + ']';
    }


    public record Header(
            String title,
            String number
    ) {

    }

    public record Section(
            String title,
            Status status,
            List<Description> description,
            List<Product> products,
            List<ComposerResponse.Button> buttons,
            String shipmentId
    ) {
    }

    public record Status(
            String color,
            String name
    ) {
    }

    public record Description(
            String type,
            String text
    ) {
    }

    public record Product(
            String image,
            String deeplink
    ) {
    }

}
