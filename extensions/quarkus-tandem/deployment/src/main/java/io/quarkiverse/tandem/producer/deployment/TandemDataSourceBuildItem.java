package io.quarkiverse.tandem.producer.deployment;

import io.quarkus.builder.item.SimpleBuildItem;

/**
 * The Agroal datasource the outbox rows are written to, once the build has checked it. Later tiers
 * consume it instead of reading the configuration again.
 */
final class TandemDataSourceBuildItem extends SimpleBuildItem {

    private final String name;

    TandemDataSourceBuildItem(String name) {
        this.name = name;
    }

    String name() {
        return name;
    }
}
