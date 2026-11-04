package io.quarkiverse.tandem.it.e2e;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.inject.Inject;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.Test;

import com.codingful.tandem.core.CloudEventsHeaders;
import com.codingful.tandem.core.OutboxMessage;

import io.quarkiverse.tandem.producer.TransactionalOutboxTemplate;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;

/**
 * A row the extension writes is published by the {@code tandem-relay} image: same count, same order, and the
 * CloudEvents {@code id} of each event equal to its outbox id (HLD-quarkus §3, LLD-quarkus §13.5).
 */
@QuarkusTest
@QuarkusTestResource(value = RelayTopology.class, restrictToAnnotatedClass = true)
class RelayEndToEndTest {

    private static final String AGGREGATE_TYPE = "Order";
    private static final String ORDER_ID = "order-e2e";
    private static final int EVENTS = 5;
    private static final Duration DELIVERY = Duration.ofSeconds(60);

    @Inject
    TransactionalOutboxTemplate template;

    @Test
    void GIVEN_the_relay_image_on_the_same_database_WHEN_the_extension_writes_an_aggregates_events_THEN_they_reach_kafka_in_order_with_their_outbox_ids()
            throws Exception {
        template.executeWithoutResult(collector -> {
            for (long seq = 1; seq <= EVENTS; seq++) {
                collector.add(OutboxMessage.builder()
                        .aggregateType(AGGREGATE_TYPE)
                        .aggregateId(ORDER_ID)
                        .payload(("{\"seq\": " + seq + "}").getBytes(StandardCharsets.UTF_8))
                        .seq(seq)
                        .build());
            }
        });

        List<String> outboxIds = outboxIdsInWriteOrder();
        List<ConsumerRecord<String, byte[]>> delivered = consume(EVENTS);

        assertThat(outboxIds).hasSize(EVENTS);
        assertThat(delivered).extracting(ConsumerRecord::key).containsOnly(ORDER_ID);
        assertThat(delivered).extracting(record -> header(record, CloudEventsHeaders.CE_ID))
                .containsExactlyElementsOf(outboxIds);
    }

    private static List<String> outboxIdsInWriteOrder() throws Exception {
        List<String> ids = new ArrayList<>();
        try (Connection connection = RelayTopology.connect();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT id FROM tandem_outbox WHERE aggregate_id = ? ORDER BY seq")) {
            statement.setString(1, ORDER_ID);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    ids.add(String.valueOf(result.getLong(1)));
                }
            }
        }
        return ids;
    }

    private static List<ConsumerRecord<String, byte[]>> consume(int expected) {
        Map<String, Object> config = Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, System.getProperty(RelayTopology.BOOTSTRAP_SERVERS),
                ConsumerConfig.GROUP_ID_CONFIG, "quarkus-e2e",
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        List<ConsumerRecord<String, byte[]>> records = new ArrayList<>();
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(config, new StringDeserializer(),
                new ByteArrayDeserializer())) {
            consumer.subscribe(Set.of(RelayTopology.TOPIC));
            long deadline = System.nanoTime() + DELIVERY.toNanos();
            while (records.size() < expected && System.nanoTime() < deadline) {
                consumer.poll(Duration.ofMillis(500)).forEach(records::add);
            }
        }
        return records;
    }

    private static String header(ConsumerRecord<String, byte[]> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }
}
