package io.jenkins.plugins.pgyer;

import hudson.model.Action;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shows the published build, with its download link and QR code, on the build page.
 */
public class PgyerBuildAction implements Action {

    private final LinkedHashMap<String, String> info;

    PgyerBuildAction(Map<String, String> info) {
        this.info = new LinkedHashMap<>(info);
    }

    /** Everything PGYER returned for the build, plus the derived {@code app*URL} links. */
    public Map<String, String> getInfo() {
        return Collections.unmodifiableMap(info);
    }

    public String getAppName() {
        return info.getOrDefault("buildName", "");
    }

    public String getVersion() {
        String version = info.getOrDefault("buildVersion", "");
        String build = info.getOrDefault("buildBuildVersion", "");
        return build.isEmpty() ? version : version + " (build " + build + ")";
    }

    public String getDownloadUrl() {
        return webUrl(info.get("appPgyerURL"));
    }

    public String getQrCodeUrl() {
        return webUrl(info.get("buildQRCodeURL"));
    }

    /** Only links to web pages are rendered, whatever the server returned. */
    private static String webUrl(String url) {
        if (url != null && (url.startsWith("https://") || url.startsWith("http://"))) {
            return url;
        }
        return "";
    }

    @Override
    public String getIconFileName() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "PGYER";
    }

    @Override
    public String getUrlName() {
        return null;
    }
}
