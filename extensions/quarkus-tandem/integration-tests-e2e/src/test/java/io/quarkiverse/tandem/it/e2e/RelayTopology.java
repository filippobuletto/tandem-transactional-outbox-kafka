package io.quarkiverse.tandem.it.e2e;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;

/**
 * HLD-quarkus §3's topology on one Testcontainers network: PostgreSQL migrated with Tandem's schema, a KRaft
 * broker, and the {@code tandem-relay} image reading the same database (LLD-quarkus §13.5). The application
 * under test reaches PostgreSQL through the mapped port.
 */
public class RelayTopology implements QuarkusTestResourceLifecycleManager {

    static final String POSTGRES_IMAGE = "postgres:18";
    static final String KAFKA_IMAGE = "apache/kafka:4.2.1";
    static final String RELAY_IMAGE = "ghcr.io/alirux/tandem-relay";
    static final String BUCKET_COUNT = "64";
    static final String TOPIC = "order-topic"; // the relay's default route for aggregate type Order

    static final String JDBC_URL = "tandem.it.e2e.jdbc-url";
    static final String USERNAME = "tandem.it.e2e.username";
    static final String PASSWORD = "tandem.it.e2e.password";
    static final String BOOTSTRAP_SERVERS = "tandem.it.e2e.bootstrap-servers";

    private static final String CHANGELOG = "tandem/schema/postgres/changelog/";
    private static final List<String> SCHEMA_FILES = List.of("v1-baseline.sql", "v2-replays.sql",
            "v3-managed-seq.sql", "v4-optional-seq.sql", "v5-claim-under-backlog.sql");
    private static final int MANAGEMENT_PORT = 8081;

    private Network network;
    private PostgreSQLContainer postgres;
    private KafkaContainer kafka;
    private GenericContainer<?> relay;

    @Override
    public Map<String, String> start() {
        network = Network.newNetwork();
        postgres = new PostgreSQLContainer(POSTGRES_IMAGE).withNetwork(network).withNetworkAliases("postgres");
        kafka = new KafkaContainer(KAFKA_IMAGE).withNetwork(network).withListener("kafka:19092");
        postgres.start();
        kafka.start();
        applySchema();
        createTopic();
        relay = new GenericContainer<>(RELAY_IMAGE + ":" + System.getProperty("tandem-relay.image.tag"))
                .withNetwork(network)
                .withEnv("SPRING_DATASOURCE_URL", "jdbc:postgresql://postgres:5432/" + postgres.getDatabaseName())
                .withEnv("SPRING_DATASOURCE_USERNAME", postgres.getUsername())
                .withEnv("SPRING_DATASOURCE_PASSWORD", postgres.getPassword())
                .withEnv("TANDEM_KAFKA_SOURCE", "/tandem/quarkus-e2e")
                .withEnv("TANDEM_KAFKA_PRODUCER_BOOTSTRAP_SERVERS", "kafka:19092")
                .withEnv("TANDEM_OUTBOX_BUCKET_COUNT", BUCKET_COUNT)
                .withExposedPorts(MANAGEMENT_PORT)
                .waitingFor(Wait.forHttp("/actuator/health/readiness").forPort(MANAGEMENT_PORT)
                        .withStartupTimeout(Duration.ofMinutes(3)));
        relay.start();
        System.setProperty(JDBC_URL, postgres.getJdbcUrl());
        System.setProperty(USERNAME, postgres.getUsername());
        System.setProperty(PASSWORD, postgres.getPassword());
        System.setProperty(BOOTSTRAP_SERVERS, kafka.getBootstrapServers());
        // An explicit URL keeps Dev Services from starting a database of its own.
        return Map.of("quarkus.datasource.jdbc.url", postgres.getJdbcUrl(),
                "quarkus.datasource.username", postgres.getUsername(),
                "quarkus.datasource.password", postgres.getPassword());
    }

    @Override
    public void stop() {
        for (AutoCloseable container : new AutoCloseable[] { relay, kafka, postgres, network }) {
            if (container != null) {
                try {
                    container.close();
                } catch (Exception e) {
                    // the reaper removes whatever is left
                }
            }
        }
    }

    static Connection connect() throws SQLException {
        return DriverManager.getConnection(System.getProperty(JDBC_URL), System.getProperty(USERNAME),
                System.getProperty(PASSWORD));
    }

    private void applySchema() {
        try (Connection connection = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(),
                postgres.getPassword()); Statement statement = connection.createStatement()) {
            for (String file : SCHEMA_FILES) {
                statement.execute(read(CHANGELOG + file));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void createTopic() {
        Properties properties = new Properties();
        properties.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        try (Admin admin = Admin.create(properties)) {
            admin.createTopics(Set.of(new NewTopic(TOPIC, 1, (short) 1))).all().get();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static String read(String resource) {
        try (InputStream in = RelayTopology.class.getClassLoader().getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Schema file not on the classpath resource:" + resource);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
