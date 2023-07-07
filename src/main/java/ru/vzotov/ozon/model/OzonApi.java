package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.format.SignStyle;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public interface OzonApi {

    record AuthResponse(OzonClient client, String authToken, String refreshToken, Long exp) {
    }

    final class Check implements OzonRecord {
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

    record ClientOperation(
            String id,
            String operationId,
            String purpose,
            OffsetDateTime time,
            String merchantCategoryCode,
            Merchant merchant,
            String merchantName,
            OzonImage image,
            String type,
            String status,
            String sbpMessage,
            String ozonOrderNumber,
            String categoryGroupName,
            Long accountAmount,
            //Direction direction,
            List<Object> bonus,
            Object meta
    ) implements OzonRecord {
        @Override
        public LocalDate date() {
            return time.toInstant().atZone(ZoneId.systemDefault()).toLocalDate();
        }
    }

    record ClientOperations(
            Cursors cursors,
            Boolean hasNextPage,
            List<ClientOperation> items
    ) implements OzonCollection<ClientOperation> {
    }

    record ClientOperationsFilter(
            List<Object> categories,
            DateRange date,
            String effect
    ) {
        /**
         * Outgoing operations
         */
        public static final String EFFECT_CREDIT = "EFFECT_CREDIT";

        /**
         * All operations
         */
        public static final String EFFECT_UNKNOWN = "EFFECT_UNKNOWN";

        /**
         * Incoming operations
         */
        public static final String EFFECT_DEBIT = "EFFECT_DEBIT";
    }

    record ClientOperationsRequest(
            Cursors cursors,
            ClientOperationsFilter filter,
            int page,
            int perPage
    ) {
    }

    record ComposerResponse(
            List<Component> layout,
            Map<String, String> widgetStates,
            Object browser,
            String layoutTrackingInfo,
            String shared,
            String nextPage,
            Object pageInfo,
            Map<String, String> trackingPayloads,
            String pageToken,
            String userToken,
            String requestID
    ) {
        public static final String C_ORDER_LIST_APP = "orderListApp";
        public static final String C_CHEQUES = "cheques";

        public String widgetState(String component) {
            final String stateId = layout.stream().filter(c -> component.equals(c.component)).findFirst()
                    .map(Component::stateId).orElse(null);
            if (stateId != null) {
                return widgetStates.get(stateId);
            }
            return null;
        }

        public record Component(
                String component,
                String params,
                String stateId,
                Long version,
                String vertical,
                String widgetTrackingInfo,
                String widgetToken,
                Long timeSpent,
                String name,
                Long id,
                Boolean isStatic
        ) {
        }

        public record Button(
                String text,
                Action action,
                Object trackingInfo,
                Object testInfo,
                String theme
        ) {
        }

        public record Action(
                String behavior,
                String link,
                Map<String, String> params
        ) {
        }
    }

    record Cursors(
            String next,
            String prev
    ) {
        public Cursors() {
            this(null, null);
        }
    }

    record DateRange(
            @JsonFormat(shape = STRING) LocalDate from,
            @JsonFormat(shape = STRING) LocalDate to
    ) {
        public DateRange(YearMonth month) {
            this(month.atDay(1), month.atEndOfMonth());
        }
    }

    enum Direction {
        @JsonProperty("outgoing")
        OUTGOING,
        @JsonProperty("incoming")
        INCOMING;
    }

    record EChecks(
            String title,
            @JsonProperty("cheques")
            List<Check> items
    ) implements OzonCollection<Check> {
    }

    record Merchant(
            String name,
            String logoUrl
    ) {
    }

    final class Order implements OzonRecord {

        private static final DateTimeFormatter TITLE_FORMATTER = new DateTimeFormatterBuilder()
                .parseCaseInsensitive()
                .appendLiteral("Заказ от ")
                .appendValue(DAY_OF_MONTH, 1, 2, SignStyle.NEVER)
                .appendLiteral(' ')
                .appendText(MONTH_OF_YEAR, TextStyle.FULL)
                .appendLiteral(' ')
                .appendValue(YEAR, 4)
                .toFormatter(new Locale("ru"));


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

    record OrderList(
            @JsonProperty("orderListApp") List<Order> items
    ) implements OzonCollection<Order> {
    }

    record OrderListFilter(
            int sort
    ) {
        public static final OrderListFilter ALL = new OrderListFilter(0);
        public static final OrderListFilter WAIT_FOR_PAYMENT = new OrderListFilter(1);
        public static final OrderListFilter IN_PROGRESS = new OrderListFilter(2);
        public static final OrderListFilter COMPLETED = new OrderListFilter(3);
        public static final OrderListFilter CANCELLED = new OrderListFilter(4);
    }

    record OzonAccount(
            String accountNumber,
            Long balance,
            String status
    ) {
    }

    record OzonAccounts(
            List<OzonAccount> accounts,
            OzonAccount mainAccount
    ) {
    }

    record OzonClient(
            String id,
            Boolean isLoggedIn,
            String identificationLevel,
            String identificationType,
            String name,
            String fullName,
            LocalDate dateOfBirth,
            String passportInfo,
            List<Object> companies,
            String status
    ) {
    }

    sealed interface OzonCollection<T extends OzonRecord> permits ClientOperations, EChecks, OrderList {
        List<T> items();
    }

    record OzonImage(
            @JsonProperty("default")
            String defaultUrl
    ) {
    }

    sealed interface OzonRecord permits Check, ClientOperation, Order {
        LocalDate date();
    }
}
