package io.jenkins.plugins.pgyer;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.PluginWrapper;
import hudson.ProxyConfiguration;
import hudson.Util;
import hudson.model.Run;
import hudson.model.TaskListener;
import io.jenkins.plugins.pgyer.client.ProxySettings;
import io.jenkins.plugins.pgyer.client.UploadOptions;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Shared by the Pipeline step and the Freestyle build steps: finds the file, resolves the API key and runs the
 * upload on the node that holds the file.
 */
final class PgyerUploader {

    /** Lets a job provide the key through {@code withCredentials} or a global environment variable. */
    static final String API_KEY_VARIABLE = "PGYER_API_KEY";

    static final String DEFAULT_FILE_PATTERN = "**/*.apk, **/*.ipa, **/*.hap";

    private PgyerUploader() {}

    static Map<String, String> upload(
            Run<?, ?> run, FilePath workspace, EnvVars env, TaskListener listener, UploadRequest request)
            throws IOException, InterruptedException {
        PrintStream log = listener.getLogger();
        PgyerGlobalConfiguration global = PgyerGlobalConfiguration.get();

        FilePath file = findFile(workspace, env, request, log);
        String buildType = buildType(file.getName(), expand(env, request.buildType));

        UploadOptions options = new UploadOptions();
        options.setApiKey(apiKey(run, env, request, global));
        options.setBuildType(buildType);
        options.setInstallType(expand(env, request.installType));
        options.setPassword(expand(env, request.password));
        options.setDescription(expand(env, request.description));
        options.setUpdateDescription(expand(env, request.updateDescription));
        options.setChannelShortcut(expand(env, request.channelShortcut));
        String start = expand(env, request.installStartDate);
        String end = expand(env, request.installEndDate);
        if ((start == null) != (end == null)) {
            throw new AbortException(Messages.Error_InstallPeriodIncomplete());
        }
        options.setInstallPeriod(start, end);
        options.setOversea(request.oversea);
        options.setFileName(file.getName());
        if (request.processingTimeout != null && request.processingTimeout > 0) {
            options.setProcessingTimeout(request.processingTimeout);
        }
        options.setUploadTimeout(uploadTimeout(env));
        options.setClient(clientName());

        log.println(Messages.Log_Uploading(file.getRemote(), buildType));
        Map<String, String> result = file.act(new UploadCallable(global.apiServers(), proxy(), options, listener));

        addLinks(result, global.webServer());
        PgyerBuildAction action = new PgyerBuildAction(result);
        log.println(Messages.Log_Published(action.getAppName(), action.getVersion()));
        log.println(Messages.Log_DownloadPage(result.get("appPgyerURL")));
        log.println(Messages.Log_QrCode(result.get("buildQRCodeURL")));
        run.addAction(action);
        return result;
    }

    private static FilePath findFile(FilePath workspace, EnvVars env, UploadRequest request, PrintStream log)
            throws IOException, InterruptedException {
        String dir = expand(env, request.baseDir);
        FilePath base = dir == null ? workspace : workspace.child(dir);
        String pattern = expand(env, request.filePattern);
        if (pattern == null) {
            pattern = DEFAULT_FILE_PATTERN;
        }
        if (!base.isDirectory()) {
            // An empty workspace may not exist yet; that is the same as finding no file.
            throw new AbortException(
                    dir == null
                            ? Messages.Error_NoFileFound(pattern, base.getRemote())
                            : Messages.Error_DirectoryNotFound(base.getRemote()));
        }
        FilePath[] matches = base.list(pattern);
        if (matches.length == 0) {
            throw new AbortException(Messages.Error_NoFileFound(pattern, base.getRemote()));
        }
        if (matches.length == 1) {
            return matches[0];
        }
        // Same rule as earlier versions: several matches upload the most recently modified file.
        long[] modified = new long[matches.length];
        for (int i = 0; i < matches.length; i++) {
            modified[i] = matches[i].lastModified();
        }
        Integer[] order = new Integer[matches.length];
        Arrays.setAll(order, i -> i);
        Arrays.sort(order, Comparator.comparingLong((Integer i) -> modified[i]).reversed());
        log.println(Messages.Log_ManyMatches(String.valueOf(matches.length), pattern));
        return matches[order[0]];
    }

    static String buildType(String fileName, String configured) throws AbortException {
        String name = fileName.toLowerCase(Locale.ROOT);
        if (name.endsWith(".apk")) {
            return "android";
        }
        if (name.endsWith(".ipa")) {
            return "ios";
        }
        if (name.endsWith(".hap")) {
            return "harmony";
        }
        String type = Util.fixEmptyAndTrim(configured);
        if (type == null) {
            throw new AbortException(Messages.Error_UnknownBuildType(fileName));
        }
        return type.toLowerCase(Locale.ROOT);
    }

    private static String apiKey(Run<?, ?> run, EnvVars env, UploadRequest request, PgyerGlobalConfiguration global)
            throws AbortException {
        String credentialsId = Util.fixEmptyAndTrim(request.credentialsId);
        if (credentialsId != null) {
            return secretText(run, credentialsId);
        }
        if (request.legacyApiKey != null) {
            String key = Util.fixEmptyAndTrim(env.expand(request.legacyApiKey.getPlainText()));
            if (key != null) {
                return key;
            }
        }
        String fromEnv = Util.fixEmptyAndTrim(env.get(API_KEY_VARIABLE));
        if (fromEnv != null) {
            return fromEnv;
        }
        if (global.getCredentialsId() != null) {
            return secretText(run, global.getCredentialsId());
        }
        throw new AbortException(Messages.Error_NoApiKey(API_KEY_VARIABLE));
    }

    private static String secretText(Run<?, ?> run, String credentialsId) throws AbortException {
        StringCredentials credentials =
                CredentialsProvider.findCredentialById(credentialsId, StringCredentials.class, run);
        if (credentials == null) {
            throw new AbortException(Messages.Error_CredentialsNotFound(credentialsId));
        }
        CredentialsProvider.track(run, credentials);
        return credentials.getSecret().getPlainText();
    }

    private static Integer uploadTimeout(EnvVars env) {
        // Earlier versions read the upload timeout, in seconds, from this variable.
        String value = Util.fixEmptyAndTrim(env.get("uploadPgyerTimeout"));
        if (value == null) {
            return null;
        }
        try {
            int seconds = Integer.parseInt(value);
            return seconds > 0 ? seconds : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Lets PGYER tell uploads from this plugin, and which versions are in use, apart from other clients. */
    private static String clientName() {
        PluginWrapper plugin = Jenkins.get().getPluginManager().whichPlugin(PgyerUploader.class);
        String version = plugin == null ? "unknown" : plugin.getVersion().split(" ")[0];
        return "upload-pgyer-jenkins/" + version + " Jenkins/" + Jenkins.VERSION;
    }

    private static ProxySettings proxy() {
        ProxyConfiguration proxy = Jenkins.get().getProxy();
        if (proxy == null || Util.fixEmptyAndTrim(proxy.getName()) == null) {
            return null;
        }
        String password = proxy.getSecretPassword() == null
                ? null
                : proxy.getSecretPassword().getPlainText();
        return new ProxySettings(
                proxy.getName(), proxy.getPort(), proxy.getUserName(), password, proxy.getNoProxyHostPatterns());
    }

    /** Adds the URLs earlier versions exposed, so jobs that read them keep working. */
    private static void addLinks(Map<String, String> result, String webServer) {
        String shortcut = result.getOrDefault("buildShortcutUrl", "");
        String buildKey = result.getOrDefault("buildKey", "");
        result.put("appPgyerURL", webServer + "/" + (shortcut.isEmpty() ? buildKey : shortcut));
        result.put("appBuildURL", webServer + "/" + buildKey);
        result.put("appQRCodeURL", result.getOrDefault("buildQRCodeURL", ""));
    }

    private static String expand(EnvVars env, String value) {
        return value == null ? null : Util.fixEmptyAndTrim(env.expand(value));
    }
}
