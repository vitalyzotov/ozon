package ru.vzotov.ozon.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

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
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import static com.fasterxml.jackson.annotation.JsonFormat.Shape.STRING;
import static com.fasterxml.jackson.annotation.JsonTypeInfo.Id.DEDUCTION;
import static java.time.temporal.ChronoField.DAY_OF_MONTH;
import static java.time.temporal.ChronoField.HOUR_OF_DAY;
import static java.time.temporal.ChronoField.MINUTE_OF_HOUR;
import static java.time.temporal.ChronoField.MONTH_OF_YEAR;
import static java.time.temporal.ChronoField.YEAR;

public interface OzonApi {

    record AuthResponseV2(Boolean ok) {

    }
    record AuthResponse(String authToken, String refreshToken, Long exp) {
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

        public Set<String> widgetState(String component) {
            return layout == null ? Collections.emptySet() : layout.stream()
                    .filter(c -> component.equals(c.component))
                    .map(Component::stateId)
                    .distinct()
                    .map(widgetStates::get)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
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
                String id,
                String text,
                String favListsLink,
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

    record OrderDetailsPage(OrderTotal orderTotal, OrderActions orderActions, Set<ShipmentWidget> shipmentWidgets) {
        public List<ShipmentWidget.Postings> findPostings() {
            return shipmentWidgets.stream().flatMap(shipment -> shipment.items().stream())
                    .map(i -> i instanceof ShipmentWidget.Postings r ? r : null)
                    .filter(Objects::nonNull)
                    .toList();
        }
    }

    record OrderTotal(Summary summary) {
        record Summary(Header header, List<Price> prices, Footer footer) {
            record Header(List<Map<String, String>> titleLines, String subtitle,
                          String icon /*, Object atomSubtitle */) {
            }

            record Price(String title, PriceValue price) {
            }

            record PriceValue(String style, String price, Object testInfo) {
            }

            record Footer(String title, PriceValue price) {
            }
        }
    }

    record OrderActions(List<ComposerResponse.Button> buttons) {
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

    record ShipmentWidget(String id, List<ShipmentWidgetItem> items) {

        @JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type", defaultImpl = Unknown.class)
        @JsonSubTypes({
                @JsonSubTypes.Type(value = Title.class, name = "title"),
                @JsonSubTypes.Type(value = Status.class, name = "status"),
                @JsonSubTypes.Type(value = Actions.class, name = "actions"),
                @JsonSubTypes.Type(value = Postings.class, name = "postings")
        })
        public sealed interface ShipmentWidgetItem<D> permits Title, Status, Actions, Postings, Unknown {
            String type();

            D data();
        }

        public record Title(String type, @JsonProperty("title") Data data) implements ShipmentWidgetItem<Title.Data> {
            public record Text(String text) {
            }

            public record Content(List<Text> chunks) {
            }

            public record Data(Content title) {
            }
        }

        public record Status(String type,
                             @JsonProperty("status") Data data) implements ShipmentWidgetItem<Status.Data> {
            public record Data(String text, String backgroundColor) {
            }
        }

        public record Actions(String type,
                              @JsonProperty("actions") Data data) implements ShipmentWidgetItem<Actions.Data> {
            public record Data(List<ComposerResponse.Button> buttons) {
            }
        }

        public record Postings(String type,
                               @JsonProperty("postings") Data data) implements ShipmentWidgetItem<Postings.Data> {
            public record Product(String image, Boolean isAdult) {
            }

            public record Posting(String title, List<Product> products, ComposerResponse.Action action) {
            }

            public record Data(List<Posting> postings) {
            }
        }

        public record Unknown(String type, Object data) implements ShipmentWidgetItem<Object> {
        }
    }

    record OrderDetailsPosting(SellerProductsList sellerProducts) {

    }


    @JsonTypeInfo(use = DEDUCTION, defaultImpl = SellerProductsList.class)
    @JsonSubTypes({
            @JsonSubTypes.Type(value = SellerProductsList.class),
            @JsonSubTypes.Type(value = SellerProductsSkus.class)
    })
    sealed interface SellerProducts permits SellerProductsList, SellerProductsSkus {
    }

    record SellerProductsList(Header header, DesignType designType, List<Item> items) implements SellerProducts {
        public record Header(
                String id,
                String type,
                String title,
                String subtitle
        ) {
        }

        public record DesignType(String type, Map<String, Object> options, List<Item> items) {
        }

        public record Item(Number id, String type, String title, String image, Object images, String link,
                           String deeplink, Number finalPrice, Number priceOzonAccount, Boolean isFavorite,
                           Boolean isInCart, String currency, Number sellerId, Number brandId, Number categoryId,
                           String saleType, String bookType, String ExpressAvailabilityStatus, String alt) {
        }

    }

    record SellerProductsSkus(Header header, ProductContainer productContainer) implements SellerProducts {
        public record Header(Title title) {
        }

        public record Title(String text, String textStyle) {
        }

        public record ProductContainer(List<Product> products) {
        }

        public record Product(String skuId, Boolean isAdult, Boolean isFavorite, String link,
                              ComposerResponse.Button button, ComposerResponse.Button favoriteButton,
                              Object state//todo: define state api
        ) {
        }

    }
}
