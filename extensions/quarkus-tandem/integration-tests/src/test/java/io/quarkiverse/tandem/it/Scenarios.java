package io.quarkiverse.tandem.it;

import static io.restassured.RestAssured.when;

import java.util.UUID;

/** Runs the application's scenarios over HTTP, each on an order of its own. */
final class Scenarios {

    private Scenarios() {
    }

    static String newOrderId() {
        return "order-" + UUID.randomUUID();
    }

    static ScenarioResource.Outcome plain(String scenario, String orderId) {
        return run("/scenarios/plain/{scenario}/{orderId}", scenario, orderId, ScenarioResource.Outcome.class);
    }

    static ScenarioResource.Outcome template(String scenario, String orderId) {
        return run("/scenarios/template/{scenario}/{orderId}", scenario, orderId, ScenarioResource.Outcome.class);
    }

    static OrderingScenarios.Ordering ordering(String scenario, String orderId) {
        return run("/scenarios/ordering/{scenario}/{orderId}", scenario, orderId, OrderingScenarios.Ordering.class);
    }

    private static <T> T run(String path, String scenario, String orderId, Class<T> type) {
        return when().post(path, scenario, orderId).then().statusCode(200).extract().as(type);
    }
}
