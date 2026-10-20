package io.jenkins.plugins.pgyer.client;

import java.io.IOException;
import java.io.PrintStream;
import java.net.Authenticator;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpRequest.BodyPublisher;
import java.net.http.HttpRequest.BodyPublishers;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import net.sf.json.JSONException;
import net.sf.json.JSONNull;
import net.sf.json.JSONObject;

/**
 * Uploads a build with the PGYER API: get a COS upload token, send the file to COS, then wait for
 * PGYER to publish the build. Runs on the agent that holds the file and has no Jenkins dependencies.
 */
public class PgyerClient {

    /** Tried in order; the later ones help when DNS for the main domain is blocked or hijacked. */
    public static final List<String> DEFAULT_API_SERVERS =
            List.of("https://www.pgyer.com", "https://www.xcxwo.com", "https://www.pgyerapp.com");

    private static final int CODE_SYNCING = 1246;
    private static final int CODE_PROCESSING = 1247;
    private static final Duration API_TIMEOUT = Duration.ofSeconds(30);

    private final List<String> apiServers;
    private final HttpClient http;
    private final PrintStream log;
    private Duration pollInterval = Duration.ofSeconds(3);

    public PgyerClient(List<String> apiServers, ProxySettings proxy, PrintStream log) {
        this.apiServers = List.copyOf(apiServers);
        this.log = log;
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
                .followRedirects(HttpClient.Redirect.NEVER);
        if (proxy != null) {
            builder.proxy(proxy.selector());
            Authenticator authenticator = proxy.authenticator();
            if (authenticator != null) {
                builder.authenticator(authenticator);
            }
        }
        this.http = builder.build();
    }

    void setPollInterval(Duration pollInterval) {
        this.pollInterval = pollInterval;
    }

    /**
     * Uploads {@code file} and returns the build information PGYER reports once it is published.
     */
    public Map<String, String> upload(Path file, UploadOptions options) throws IOException, InterruptedException {
        Token token = requestToken(options);
        sendToCos(token, file, options);
        log.println(Messages.Log_UploadFinished());
        return waitForBuild(token.server, options.getApiKey(), token.key, options.getProcessingTimeout());
    }

    private Token requestToken(UploadOptions options) throws IOException, InterruptedException {
        Map<String, String> form = options.toTokenForm();
        IOException lastFailure = null;
        for (String server : apiServers) {
            JSONObject json;
            try {
                json = postForm(server + "/apiv2/app/getCOSToken", form);
            } catch (IOException e) {
                log.println(Messages.Log_Unreachable(server, e.getMessage()));
                lastFailure = e;
                continue;
            }
            JSONObject data = dataOf(json);
            JSONObject params = data.optJSONObject("params");
            String endpoint = data.optString("endpoint", "");
            String key = data.optString("key", "");
            if (params == null || params.isNullObject() || endpoint.isEmpty() || key.isEmpty()) {
                throw new IOException(Messages.Error_IncompleteToken(json));
            }
            return new Token(server, endpoint, key, flatten(params));
        }
        throw new IOException(
                "None of the PGYER API servers could be reached: " + String.join(", ", apiServers), lastFailure);
    }

    private void sendToCos(Token token, Path file, UploadOptions options) throws IOException, InterruptedException {
        long size = Files.size(file);
        String boundary = "----PgyerJenkins" + UUID.randomUUID().toString().replace("-", "");
        Map<String, String> fields = new LinkedHashMap<>(token.fields);
        String downloadName = options.getFileName();
        // COS stores x-cos-meta-* as HTTP headers, so only plain ASCII names are safe to send.
        if (downloadName != null && downloadName.chars().allMatch(c -> c >= 0x20 && c < 0x7f)) {
            fields.put("x-cos-meta-file-name", downloadName);
        }

        StringBuilder head = new StringBuilder();
        for (Map.Entry<String, String> field : fields.entrySet()) {
            head.append("--")
                    .append(boundary)
                    .append("\r\n")
                    .append("Content-Disposition: form-data; name=\"")
                    .append(field.getKey())
                    .append("\"\r\n\r\n")
                    .append(field.getValue())
                    .append("\r\n");
        }
        head.append("--")
                .append(boundary)
                .append("\r\n")
                .append("Content-Disposition: form-data; name=\"file\"; filename=\"")
                .append(asciiFileName(file))
                .append("\"\r\n")
                .append("Content-Type: application/octet-stream\r\n\r\n");
        String tail = "\r\n--" + boundary + "--\r\n";

        BodyPublisher body = BodyPublishers.concat(
                BodyPublishers.ofString(head.toString(), StandardCharsets.UTF_8),
                withProgress(BodyPublishers.ofFile(file), size),
                BodyPublishers.ofString(tail, StandardCharsets.UTF_8));
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(token.endpoint))
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(body);
        if (options.getUploadTimeout() != null) {
            request.timeout(Duration.ofSeconds(options.getUploadTimeout()));
        }

        log.println(Messages.Log_Uploading(String.valueOf(file.getFileName()), humanSize(size)));
        HttpResponse<String> response = http.send(request.build(), HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new IOException(Messages.Error_StorageUploadFailed(
                    String.valueOf(response.statusCode()), abbreviate(response.body())));
        }
    }

    private Map<String, String> waitForBuild(String server, String apiKey, String buildKey, int timeoutSeconds)
            throws IOException, InterruptedException {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("_api_key", apiKey);
        form.put("buildKey", buildKey);
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        boolean reportedNetworkError = false;
        while (true) {
            JSONObject json = null;
            try {
                json = postForm(server + "/apiv2/app/buildInfo", form);
            } catch (IOException e) {
                // The file is already uploaded, so keep polling through short network problems.
                if (!reportedNetworkError) {
                    log.println(Messages.Log_StatusRetry(e.getMessage()));
                    reportedNetworkError = true;
                }
            }
            if (json != null) {
                int code = json.optInt("code", -1);
                if (code == 0) {
                    return flatten(json.getJSONObject("data"));
                }
                if (code != CODE_SYNCING && code != CODE_PROCESSING) {
                    throw new PgyerApiException(code, json.optString("message"));
                }
            }
            if (System.nanoTime() > deadline) {
                throw new IOException(Messages.Error_ProcessingTimeout(String.valueOf(timeoutSeconds), buildKey));
            }
            Thread.sleep(pollInterval.toMillis());
        }
    }

    private JSONObject postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> field : form.entrySet()) {
            if (body.length() > 0) {
                body.append('&');
            }
            body.append(URLEncoder.encode(field.getKey(), StandardCharsets.UTF_8))
                    .append('=')
                    .append(URLEncoder.encode(field.getValue(), StandardCharsets.UTF_8));
        }
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .timeout(API_TIMEOUT)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(BodyPublishers.ofString(body.toString()))
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        try {
            JSONObject json = JSONObject.fromObject(response.body());
            if (!json.has("code")) {
                throw new IOException(Messages.Error_UnexpectedResponse(abbreviate(response.body())));
            }
            return json;
        } catch (JSONException e) {
            // Usually a captive portal, a hijacked DNS record or a proxy error page.
            throw new IOException(Messages.Error_NonJsonResponse(String.valueOf(response.statusCode())), e);
        }
    }

    private static JSONObject dataOf(JSONObject json) throws PgyerApiException {
        int code = json.optInt("code", -1);
        if (code != 0) {
            throw new PgyerApiException(code, json.optString("message"));
        }
        return json.getJSONObject("data");
    }

    private static Map<String, String> flatten(JSONObject data) {
        Map<String, String> result = new LinkedHashMap<>();
        for (Object entry : data.entrySet()) {
            Map.Entry<?, ?> e = (Map.Entry<?, ?>) entry;
            result.put(String.valueOf(e.getKey()), text(e.getValue()));
        }
        return result;
    }

    private static String text(Object value) {
        return value == null || value instanceof JSONNull ? "" : value.toString();
    }

    private BodyPublisher withProgress(BodyPublisher file, long size) {
        AtomicLong sent = new AtomicLong();
        AtomicLong lastReported = new AtomicLong();
        return BodyPublishers.fromPublisher(
                subscriber -> file.subscribe(new Flow.Subscriber<ByteBuffer>() {
                    @Override
                    public void onSubscribe(Flow.Subscription subscription) {
                        subscriber.onSubscribe(subscription);
                    }

                    @Override
                    public void onNext(ByteBuffer item) {
                        long total = sent.addAndGet(item.remaining());
                        long tenth = size == 0 ? 10 : total * 10 / size;
                        if (tenth > lastReported.get()) {
                            lastReported.set(tenth);
                            log.println(Messages.Log_Progress(String.valueOf(tenth * 10), humanSize(total)));
                        }
                        subscriber.onNext(item);
                    }

                    @Override
                    public void onError(Throwable throwable) {
                        subscriber.onError(throwable);
                    }

                    @Override
                    public void onComplete() {
                        subscriber.onComplete();
                    }
                }),
                size);
    }

    private static String asciiFileName(Path file) {
        String name = String.valueOf(file.getFileName());
        if (name.chars().allMatch(c -> c >= 0x20 && c < 0x7f && c != '"')) {
            return name;
        }
        int dot = name.lastIndexOf('.');
        return "upload" + (dot >= 0 ? name.substring(dot) : "");
    }

    static String humanSize(long bytes) {
        if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        }
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }

    private static String abbreviate(String text) {
        if (text == null) {
            return "";
        }
        return text.length() > 500 ? text.substring(0, 500) + "..." : text;
    }

    private static final class Token {
        final String server;
        final String endpoint;
        final String key;
        final Map<String, String> fields;

        Token(String server, String endpoint, String key, Map<String, String> fields) {
            this.server = server;
            this.endpoint = endpoint;
            this.key = key;
            this.fields = fields;
        }
    }
}
