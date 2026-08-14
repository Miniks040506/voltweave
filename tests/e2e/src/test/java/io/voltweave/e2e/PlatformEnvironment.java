package io.voltweave.e2e;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class PlatformEnvironment implements AutoCloseable {
    private static final Duration STARTUP_TIMEOUT = Duration.ofMinutes(3);
    private static final String VERSION = "0.1.0-SNAPSHOT";

    private final Path root = Path.of(System.getProperty("voltweave.root")).toAbsolutePath();
    private final Path work = root.resolve("tests/e2e/target/runtime-" + ProcessHandle.current().pid());
    private final Path composeFile = root.resolve("infrastructure/compose/compose.yml");
    private final Path envFile = work.resolve("compose.env");
    private final String project = "voltweave-e2e-" + ProcessHandle.current().pid();
    private final Map<String, Integer> ports = new LinkedHashMap<>();
    private final Map<String, Process> services = new LinkedHashMap<>();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)).build();

    void start() throws Exception {
        Files.createDirectories(work.resolve("logs"));
        for (String name : List.of(
                "postgres", "keycloak", "kafka", "mqtt", "gateway", "portfolio",
                "telemetry", "intelligence", "dispatch", "settlement"
        )) {
            ports.put(name, availablePort());
        }
        writeComposeEnvironment();
        compose("up", "-d", "--wait", "postgres", "keycloak", "kafka", "mosquitto");
        compose("run", "--rm", "--no-deps", "kafka-init");
        compose("run", "--rm", "--no-deps", "mosquitto-init");

        startService("portfolio", "services/portfolio-service", "portfolio-service");
        startService("telemetry", "services/telemetry-service", "telemetry-service");
        startService("intelligence", "services/intelligence-service", "intelligence-service");
        startService("dispatch", "services/dispatch-service", "dispatch-service");
        startService("settlement", "services/settlement-service", "settlement-service");
        startService("gateway", "services/api-gateway", "api-gateway");
        for (String name : services.keySet()) {
            waitForHealth(name);
        }
    }

    URI gateway(String path) {
        return URI.create("http://localhost:" + ports.get("gateway") + path);
    }

    URI keycloak(String path) {
        return URI.create("http://localhost:" + ports.get("keycloak") + path);
    }

    int mqttPort() {
        return ports.get("mqtt");
    }

    String serviceMetrics(String name) throws Exception {
        var response = http.send(
                HttpRequest.newBuilder(URI.create(serviceUrl(name) + "/actuator/prometheus"))
                        .timeout(Duration.ofSeconds(5)).GET().build(),
                java.net.http.HttpResponse.BodyHandlers.ofString()
        );
        if (response.statusCode() != HttpURLConnection.HTTP_OK) {
            throw new IllegalStateException(name + " metrics returned " + response.statusCode());
        }
        return response.body();
    }

    void restartDispatch() throws Exception {
        stopService("dispatch");
        startService("dispatch", "services/dispatch-service", "dispatch-service");
        waitForHealth("dispatch");
    }

    private void startService(String name, String module, String artifact) throws IOException {
        Path jar = root.resolve(module + "/target/" + artifact + "-" + VERSION + ".jar");
        if (!Files.isRegularFile(jar)) {
            throw new IllegalStateException("Missing executable jar: " + jar);
        }
        Path log = work.resolve("logs/" + name + ".log");
        ProcessBuilder builder = new ProcessBuilder(javaCommand(), "-jar", jar.toString());
        builder.directory(root.toFile());
        builder.redirectErrorStream(true);
        builder.redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
        Map<String, String> environment = builder.environment();
        environment.putAll(serviceEnvironment());
        environment.put("PORT", ports.get(name).toString());
        services.put(name, builder.start());
    }

    private Map<String, String> serviceEnvironment() {
        String keycloak = "http://localhost:" + ports.get("keycloak");
        String postgres = "jdbc:postgresql://localhost:" + ports.get("postgres") + "/";
        return Map.ofEntries(
                Map.entry("PORTFOLIO_DB_URL", postgres + "portfolio_db"),
                Map.entry("PORTFOLIO_DB_PASSWORD", "local-portfolio-change-me"),
                Map.entry("TELEMETRY_DB_URL", postgres + "telemetry_db"),
                Map.entry("TELEMETRY_DB_PASSWORD", "local-telemetry-change-me"),
                Map.entry("INTELLIGENCE_DB_URL", postgres + "intelligence_db"),
                Map.entry("INTELLIGENCE_DB_PASSWORD", "local-intelligence-change-me"),
                Map.entry("DISPATCH_DB_URL", postgres + "dispatch_db"),
                Map.entry("DISPATCH_DB_PASSWORD", "local-dispatch-change-me"),
                Map.entry("SETTLEMENT_DB_URL", postgres + "settlement_db"),
                Map.entry("SETTLEMENT_DB_PASSWORD", "local-settlement-change-me"),
                Map.entry("KAFKA_BOOTSTRAP_SERVERS", "localhost:" + ports.get("kafka")),
                Map.entry("MQTT_BROKER_URI", "tcp://127.0.0.1:" + ports.get("mqtt")),
                Map.entry("MQTT_ADMIN_USERNAME", "voltweave-provisioner"),
                Map.entry("MQTT_ADMIN_PASSWORD", "local-mqtt-admin-change-me"),
                Map.entry("MQTT_TELEMETRY_USERNAME", "telemetry-service"),
                Map.entry("MQTT_TELEMETRY_PASSWORD", "local-telemetry-mqtt-change-me"),
                Map.entry("OIDC_ISSUER_URI", keycloak + "/realms/voltweave"),
                Map.entry("OIDC_TOKEN_URI", keycloak + "/realms/voltweave/protocol/openid-connect/token"),
                Map.entry("KEYCLOAK_INTERNAL_CLIENT_SECRET", "local-internal-client-change-me"),
                Map.entry("PORTFOLIO_SERVICE_URL", serviceUrl("portfolio")),
                Map.entry("TELEMETRY_SERVICE_URL", serviceUrl("telemetry")),
                Map.entry("INTELLIGENCE_SERVICE_URL", serviceUrl("intelligence")),
                Map.entry("DISPATCH_SERVICE_URL", serviceUrl("dispatch")),
                Map.entry("SETTLEMENT_SERVICE_URL", serviceUrl("settlement")),
                Map.entry("AUTOMATION_EVALUATION_DELAY", "1h")
        );
    }

    private String serviceUrl(String name) {
        return "http://localhost:" + ports.get(name);
    }

    private void waitForHealth(String name) throws Exception {
        URI uri = URI.create(serviceUrl(name) + "/actuator/health");
        Instant deadline = Instant.now().plus(STARTUP_TIMEOUT);
        while (Instant.now().isBefore(deadline)) {
            Process process = services.get(name);
            if (!process.isAlive()) {
                throw new IllegalStateException(name + " exited during startup:\n" + logTail(name));
            }
            try {
                var response = http.send(
                        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(2)).GET().build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()
                );
                if (response.statusCode() == HttpURLConnection.HTTP_OK) return;
            } catch (IOException ignored) {
                // The socket is not accepting connections yet.
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException(name + " did not become healthy:\n" + logTail(name));
    }

    private String logTail(String name) throws IOException {
        List<String> lines = Files.readAllLines(work.resolve("logs/" + name + ".log"));
        return String.join(System.lineSeparator(), lines.subList(Math.max(0, lines.size() - 40), lines.size()));
    }

    private void writeComposeEnvironment() throws IOException {
        Files.writeString(envFile, """
                POSTGRES_HOST_PORT=%d
                POSTGRES_ADMIN_USER=voltweave_admin
                POSTGRES_ADMIN_PASSWORD=local-postgres-admin-change-me
                KAFKA_HOST_PORT=%d
                MQTT_HOST_PORT=%d
                MOSQUITTO_ADMIN_USERNAME=voltweave-provisioner
                MOSQUITTO_ADMIN_PASSWORD=local-mqtt-admin-change-me
                MQTT_TELEMETRY_USERNAME=telemetry-service
                MQTT_TELEMETRY_PASSWORD=local-telemetry-mqtt-change-me
                PORTFOLIO_DB_PASSWORD=local-portfolio-change-me
                TELEMETRY_DB_PASSWORD=local-telemetry-change-me
                INTELLIGENCE_DB_PASSWORD=local-intelligence-change-me
                DISPATCH_DB_PASSWORD=local-dispatch-change-me
                SETTLEMENT_DB_PASSWORD=local-settlement-change-me
                KEYCLOAK_DB_PASSWORD=local-keycloak-db-change-me
                KEYCLOAK_HOST_PORT=%d
                KEYCLOAK_ADMIN_USERNAME=admin
                KEYCLOAK_ADMIN_PASSWORD=local-keycloak-admin-change-me
                KEYCLOAK_INTERNAL_CLIENT_SECRET=local-internal-client-change-me
                DEMO_CUSTOMER_PASSWORD=local-customer-change-me
                DEMO_OPERATOR_PASSWORD=local-operator-change-me
                DEMO_ADMIN_PASSWORD=local-admin-change-me
                """.formatted(
                ports.get("postgres"), ports.get("kafka"), ports.get("mqtt"), ports.get("keycloak")
        ));
    }

    private void compose(String... arguments) throws Exception {
        var command = new java.util.ArrayList<>(List.of(
                "docker", "compose", "--project-name", project,
                "--env-file", envFile.toString(), "-f", composeFile.toString()
        ));
        command.addAll(List.of(arguments));
        run(command, Duration.ofMinutes(4));
    }

    private static void run(List<String> command, Duration timeout) throws Exception {
        Process process = new ProcessBuilder(command).inheritIO().start();
        if (!process.waitFor(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)) {
            process.destroyForcibly();
            throw new IllegalStateException("Command timed out: " + String.join(" ", command));
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Command failed: " + String.join(" ", command));
        }
    }

    private static int availablePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static String javaCommand() {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java";
        return Path.of(System.getProperty("java.home"), "bin", executable).toString();
    }

    private void stopService(String name) throws InterruptedException {
        Process process = services.remove(name);
        if (process == null) return;
        process.destroy();
        if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
            process.destroyForcibly();
            if (!process.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                throw new IllegalStateException(name + " did not stop");
            }
        }
    }

    @Override
    public void close() throws Exception {
        for (String name : List.copyOf(services.keySet()).reversed()) stopService(name);
        compose("down", "--volumes", "--remove-orphans");
    }
}
