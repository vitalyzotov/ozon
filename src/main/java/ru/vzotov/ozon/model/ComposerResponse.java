package ru.vzotov.ozon.model;

import java.util.List;
import java.util.Map;

public record ComposerResponse(
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
