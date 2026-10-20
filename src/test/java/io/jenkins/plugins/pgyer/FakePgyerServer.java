package io.jenkins.plugins.pgyer;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Stands in for the PGYER API and the COS bucket it hands out upload tokens for.
 */
public class FakePgyerServer implements AutoCloseable {

    public static final String BUILD_KEY = "abc123";
    public static final String SHORTCUT = "myapp";

    private final HttpServer server;

    /** Forms posted to getCOSToken. */
    public final List<Map<String, String>> tokenRequests = new CopyOnWriteArrayList<>();
    /** Raw multipart bodies posted to the storage endpoint, decoded as ISO-8859-1. */
    public final List<String> uploads = new CopyOnWriteArrayList<>();
    /** Forms posted to buildInfo. */
    public final List<Map<String, String>> buildInfoRequests = new CopyOnWriteArrayList<>();

    /** Number of buildInfo calls answered with "still processing" before the build is published. */
    public volatile int processingPolls;
    /** When set, getCOSToken answers with this error code. */
    public volatile Integer tokenError;
    /** When set, buildInfo answers with this error code instead of publishing. */
    public volatile Integer buildInfoError;
    /** When true, getCOSToken answers with an HTML page, as a hijacked DNS record would. */
    public volatile boolean htmlResponses;

    /** Request targets as received; requests sent through a proxy use the absolute form, {@code http://host/...}. */
    public final List<String> requestTargets = new CopyOnWriteArrayList<>();
    /** When set, every request must carry this {@code user:password} as Basic proxy credentials. */
    public volatile String requiredProxyCredentials;

    private final AtomicInteger polls = new AtomicInteger();

    public FakePgyerServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/apiv2/app/getCOSToken", this::token);
        server.createContext("/cos", this::cos);
        server.createContext("/apiv2/app/buildInfo", this::buildInfo);
        server.start();
    }

    public String url() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** Records the request and answers 407 when proxy credentials are required but missing. */
    private boolean admit(HttpExchange exchange) throws IOException {
        requestTargets.add(exchange.getRequestURI().toString());
        String required = requiredProxyCredentials;
        if (required == null) {
            return true;
        }
        String expected = "Basic " + Base64.getEncoder().encodeToString(required.getBytes(StandardCharsets.UTF_8));
        if (expected.equals(exchange.getRequestHeaders().getFirst("Proxy-Authorization"))) {
            return true;
        }
        exchange.getRequestBody().readAllBytes();
        exchange.getResponseHeaders().set("Proxy-Authenticate", "Basic realm=\"proxy\"");
        exchange.sendResponseHeaders(407, -1);
        exchange.close();
        return false;
    }

    private void token(HttpExchange exchange) throws IOException {
        if (!admit(exchange)) {
            return;
        }
        tokenRequests.add(form(exchange));
        if (htmlResponses) {
            respond(exchange, 200, "text/html", "<html><body>Welcome to our Wi-Fi</body></html>");
            return;
        }
        if (tokenError != null) {
            respond(
                    exchange,
                    200,
                    "application/json",
                    "{\"code\":" + tokenError + ",\"message\":\"error from fake server\"}");
            return;
        }
        String key = BUILD_KEY + ".apk";
        respond(
                exchange,
                200,
                "application/json",
                "{\"code\":0,\"message\":\"\",\"data\":{"
                        + "\"params\":{\"key\":\"" + key + "\",\"signature\":\"sig\",\"x-cos-security-token\":\"tok\"},"
                        + "\"key\":\"" + key + "\",\"endpoint\":\"" + url() + "/cos\"}}");
    }

    private void cos(HttpExchange exchange) throws IOException {
        if (!admit(exchange)) {
            return;
        }
        uploads.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.ISO_8859_1));
        exchange.sendResponseHeaders(204, -1);
        exchange.close();
    }

    private void buildInfo(HttpExchange exchange) throws IOException {
        if (!admit(exchange)) {
            return;
        }
        buildInfoRequests.add(form(exchange));
        if (buildInfoError != null) {
            respond(
                    exchange,
                    200,
                    "application/json",
                    "{\"code\":" + buildInfoError + ",\"message\":\"App failed to publish.\"}");
            return;
        }
        if (polls.incrementAndGet() <= processingPolls) {
            respond(
                    exchange,
                    200,
                    "application/json",
                    "{\"code\":1247,\"message\":\"App is being processed. Please call this API later\"}");
            return;
        }
        respond(
                exchange,
                200,
                "application/json",
                "{\"code\":0,\"message\":\"\",\"data\":{"
                        + "\"buildKey\":\"" + BUILD_KEY + "\",\"buildType\":\"2\",\"buildName\":\"My App\","
                        + "\"buildVersion\":\"1.2.0\",\"buildBuildVersion\":\"7\",\"buildShortcutUrl\":\"" + SHORTCUT
                        + "\","
                        + "\"buildDescription\":null,"
                        + "\"buildQRCodeURL\":\"https://www.pgyer.com/app/qrcodeHistory/" + BUILD_KEY + "\"}}");
    }

    private static Map<String, String> form(HttpExchange exchange) throws IOException {
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Map<String, String> form = new LinkedHashMap<>();
        for (String pair : body.split("&")) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            form.put(
                    URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        return form;
    }

    private static void respond(HttpExchange exchange, int status, String type, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
