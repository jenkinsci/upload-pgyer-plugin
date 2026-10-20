package io.jenkins.plugins.pgyer.client;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.jenkins.plugins.pgyer.FakePgyerServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PgyerClientTest {

    @TempDir
    Path dir;

    private FakePgyerServer pgyer;
    private Path apk;
    private final ByteArrayOutputStream log = new ByteArrayOutputStream();

    @BeforeEach
    void setUp() throws IOException {
        pgyer = new FakePgyerServer();
        apk = Files.writeString(dir.resolve("app-release.apk"), "APK-CONTENT");
    }

    @AfterEach
    void tearDown() {
        pgyer.close();
    }

    private PgyerClient client(String... servers) {
        PgyerClient client =
                new PgyerClient(List.of(servers), null, new PrintStream(log, true, StandardCharsets.UTF_8));
        client.setPollInterval(Duration.ofMillis(10));
        return client;
    }

    private static UploadOptions options() {
        UploadOptions options = new UploadOptions();
        options.setApiKey("key-1");
        options.setBuildType("android");
        options.setFileName("app-release.apk");
        return options;
    }

    @Test
    void uploadsTheFileAndWaitsUntilTheBuildIsPublished() throws Exception {
        pgyer.processingPolls = 2;
        UploadOptions options = options();
        options.setInstallType("2");
        options.setPassword("secret");
        options.setUpdateDescription("Fixed the login crash");
        options.setInstallPeriod("2026-11-01", "2026-11-30");
        options.setOversea(false);

        Map<String, String> info = client(pgyer.url()).upload(apk, options);

        assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
        assertThat(info, hasEntry("buildShortcutUrl", FakePgyerServer.SHORTCUT));
        assertThat(info, hasEntry("buildDescription", ""));
        Map<String, String> token = pgyer.tokenRequests.get(0);
        assertThat(token, hasEntry("_api_key", "key-1"));
        assertThat(token, hasEntry("buildType", "android"));
        assertThat(token, hasEntry("buildInstallType", "2"));
        assertThat(token, hasEntry("buildPassword", "secret"));
        assertThat(token, hasEntry("buildUpdateDescription", "Fixed the login crash"));
        assertThat(token, hasEntry("buildInstallDate", "1"));
        assertThat(token, hasEntry("buildInstallStartDate", "2026-11-01"));
        assertThat(token, hasEntry("buildInstallEndDate", "2026-11-30"));
        assertThat(token, hasEntry("oversea", "0"));
        String upload = pgyer.uploads.get(0);
        assertThat(upload, containsString("name=\"signature\"\r\n\r\nsig\r\n"));
        assertThat(upload, containsString("name=\"x-cos-meta-file-name\"\r\n\r\napp-release.apk\r\n"));
        assertThat(upload, containsString("APK-CONTENT"));
        assertThat(pgyer.buildInfoRequests, hasSize(3));
        assertThat(pgyer.buildInfoRequests.get(0), hasEntry("buildKey", FakePgyerServer.BUILD_KEY + ".apk"));
    }

    @Test
    void identifiesTheCaller() throws Exception {
        UploadOptions options = options();
        options.setClient("upload-pgyer-jenkins/3.1 Jenkins/2.479.3");

        client(pgyer.url()).upload(apk, options);

        assertThat(pgyer.userAgents.get(0), startsWith("upload-pgyer-jenkins/3.1 Jenkins/2.479.3 Java/"));
    }

    @Test
    void leavesOutSettingsThatAreNotSet() throws Exception {
        client(pgyer.url()).upload(apk, options());

        Map<String, String> token = pgyer.tokenRequests.get(0);
        assertThat(token.keySet(), is(java.util.Set.of("_api_key", "buildType")));
    }

    @Test
    void doesNotSendNonAsciiDownloadNames() throws Exception {
        UploadOptions options = options();
        options.setFileName("测试.apk");

        client(pgyer.url()).upload(apk, options);

        assertThat(pgyer.uploads.get(0), not(containsString("x-cos-meta-file-name")));
    }

    @Test
    void reportsApiErrorsWithAHint() {
        pgyer.tokenError = 1022;

        PgyerApiException e =
                assertThrows(PgyerApiException.class, () -> client(pgyer.url()).upload(apk, options()));

        assertEquals(1022, e.getCode());
        assertThat(e.getMessage(), containsString("error from fake server"));
        assertThat(e.getMessage(), containsString("storage is full"));
        assertThat(pgyer.uploads, hasSize(0));
    }

    @Test
    void triesTheNextServerWhenOneIsUnreachable() throws Exception {
        Map<String, String> info = client("http://127.0.0.1:1", pgyer.url()).upload(apk, options());

        assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
        assertThat(log.toString(StandardCharsets.UTF_8), containsString("http://127.0.0.1:1 is not reachable"));
    }

    @Test
    void triesTheNextServerWhenTheResponseIsNotJson() throws Exception {
        try (FakePgyerServer hijacked = new FakePgyerServer()) {
            hijacked.htmlResponses = true;

            Map<String, String> info = client(hijacked.url(), pgyer.url()).upload(apk, options());

            assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
            assertThat(pgyer.tokenRequests, hasSize(1));
        }
    }

    @Test
    void failsWhenNoServerCanBeReached() {
        IOException e = assertThrows(
                IOException.class, () -> client("http://127.0.0.1:1").upload(apk, options()));

        assertThat(e.getMessage(), containsString("None of the PGYER API servers could be reached"));
    }

    @Test
    void failsWhenPublishingFails() {
        pgyer.buildInfoError = 1216;

        PgyerApiException e =
                assertThrows(PgyerApiException.class, () -> client(pgyer.url()).upload(apk, options()));

        assertEquals(1216, e.getCode());
        assertThat(e.getMessage(), containsString("App failed to publish."));
    }

    @Test
    void givesUpWhenProcessingTakesTooLong() {
        pgyer.processingPolls = Integer.MAX_VALUE;
        UploadOptions options = options();
        options.setProcessingTimeout(1);

        IOException e =
                assertThrows(IOException.class, () -> client(pgyer.url()).upload(apk, options));

        assertThat(e.getMessage(), containsString("still processing the build after 1 seconds"));
        assertThat(e.getMessage(), containsString(FakePgyerServer.BUILD_KEY));
    }

    private PgyerClient clientVia(ProxySettings proxy, String... servers) {
        PgyerClient client =
                new PgyerClient(List.of(servers), proxy, new PrintStream(log, true, StandardCharsets.UTF_8));
        client.setPollInterval(Duration.ofMillis(10));
        return client;
    }

    @Test
    void sendsEveryRequestThroughTheProxy() throws Exception {
        // The fake server is the proxy; the API host never resolves, so only proxied requests can succeed.
        ProxySettings proxy = new ProxySettings("127.0.0.1", port(), null, null, List.of());

        Map<String, String> info = clientVia(proxy, "http://pgyer.invalid").upload(apk, options());

        assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
        assertThat(pgyer.requestTargets.get(0), is("http://pgyer.invalid/apiv2/app/getCOSToken"));
        assertThat(pgyer.requestTargets.get(1), is(pgyer.url() + "/cos"));
    }

    @Test
    void authenticatesWithTheProxy() throws Exception {
        pgyer.requiredProxyCredentials = "alice:s3cret";
        ProxySettings proxy = new ProxySettings("127.0.0.1", port(), "alice", "s3cret", List.of());

        Map<String, String> info = clientVia(proxy, "http://pgyer.invalid").upload(apk, options());

        assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
    }

    @Test
    void connectsDirectlyToHostsExcludedFromTheProxy() throws Exception {
        ProxySettings deadProxy = new ProxySettings(
                "127.0.0.1", 1, null, null, List.of(java.util.regex.Pattern.compile("127\\.0\\.0\\.1")));

        Map<String, String> info = clientVia(deadProxy, pgyer.url()).upload(apk, options());

        assertThat(info, hasEntry("buildKey", FakePgyerServer.BUILD_KEY));
        assertThat(pgyer.requestTargets.get(0), is("/apiv2/app/getCOSToken"));
    }

    @Test
    void hasChineseMessages() {
        java.util.Locale zh = java.util.Locale.SIMPLIFIED_CHINESE;
        assertThat(Messages._Hint_Storage().toString(zh), containsString("存储空间已满"));
        assertThat(Messages._Error_ApiError("1022").toString(zh), is("蒲公英 API 错误 1022"));
        assertThat(
                io.jenkins.plugins.pgyer.Messages._Error_NoFileFound("*.apk", "/ws")
                        .toString(zh),
                is("在 /ws 中没有找到匹配 '*.apk' 的文件"));
    }

    private int port() {
        return java.net.URI.create(pgyer.url()).getPort();
    }
}
