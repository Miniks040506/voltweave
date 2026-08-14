package io.voltweave.e2e;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.function.Predicate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

final class PlatformClient {
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(15);

    private final PlatformEnvironment environment;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(3)).build();
    private final ObjectMapper json = JsonMapper.builder().build();

    PlatformClient(PlatformEnvironment environment) {
        this.environment = environment;
    }

    String token(String username, String password) throws Exception {
        String body = form(Map.of(
                "client_id", "voltweave-e2e",
                "grant_type", "password",
                "username", username,
                "password", password
        ));
        HttpRequest request = HttpRequest.newBuilder(environment.keycloak(
                        "/realms/voltweave/protocol/openid-connect/token"
                ))
                .timeout(REQUEST_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) {
            throw new IllegalStateException("Token request failed: " + response.body());
        }
        return json.readTree(response.body()).path("access_token").asString();
    }

    String subject(String token) throws IOException {
        String payload = new String(
                Base64.getUrlDecoder().decode(token.split("\\.")[1]), StandardCharsets.UTF_8
        );
        return json.readTree(payload).path("sub").asString();
    }

    JsonNode get(String path, String token) throws Exception {
        return requireJson(send("GET", path, token, null, Map.of()), 200);
    }

    JsonNode post(String path, String token, String body, int expectedStatus) throws Exception {
        return requireJson(send("POST", path, token, body, Map.of()), expectedStatus);
    }

    JsonNode post(
            String path,
            String token,
            String body,
            int expectedStatus,
            Map<String, String> headers
    ) throws Exception {
        return requireJson(send("POST", path, token, body, headers), expectedStatus);
    }

    JsonNode patch(String path, String token, String body) throws Exception {
        return requireJson(send("PATCH", path, token, body, Map.of()), 200);
    }

    Response send(
            String method,
            String path,
            String token,
            String body,
            Map<String, String> headers
    ) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(environment.gateway(path))
                .timeout(REQUEST_TIMEOUT)
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/json");
        headers.forEach(request::header);
        if (body == null) {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        } else {
            request.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        }
        HttpResponse<String> response = http.send(
                request.build(), HttpResponse.BodyHandlers.ofString()
        );
        return new Response(response.statusCode(), response.body());
    }

    JsonNode awaitGet(
            String path,
            String token,
            Predicate<JsonNode> accepted,
            Duration timeout
    ) throws Exception {
        Instant deadline = Instant.now().plus(timeout);
        Exception lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                Response response = send("GET", path, token, null, Map.of());
                if (response.status() == 200) {
                    JsonNode body = json.readTree(response.body());
                    if (accepted.test(body)) return body;
                }
            } catch (Exception exception) {
                lastFailure = exception;
            }
            Thread.sleep(500);
        }
        throw new IllegalStateException("Condition did not become true for " + path, lastFailure);
    }

    private JsonNode requireJson(Response response, int expectedStatus) throws IOException {
        if (response.status() != expectedStatus) {
            throw new IllegalStateException(
                    "Expected HTTP " + expectedStatus + ", got " + response.status()
                            + ": " + response.body()
            );
        }
        return response.body().isBlank() ? json.createObjectNode() : json.readTree(response.body());
    }

    private static String form(Map<String, String> values) {
        return values.entrySet().stream()
                .map(entry -> encode(entry.getKey()) + "=" + encode(entry.getValue()))
                .collect(java.util.stream.Collectors.joining("&"));
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    record Response(int status, String body) {
    }
}
