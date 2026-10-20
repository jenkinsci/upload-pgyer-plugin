package io.jenkins.plugins.pgyer;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.EnvVars;
import hudson.FilePath;
import hudson.model.Item;
import hudson.model.Result;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.security.ACL;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import jenkins.model.Jenkins;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;

/**
 * Helpers for the Freestyle build steps, whose classes keep their pre-3.0 names in another package.
 */
public final class UploadSupport {

    private UploadSupport() {}

    /** The settings of a Freestyle build step, named as they are stored in job configurations. */
    public record Settings(
            Secret apiKey,
            String credentialsId,
            String scanDir,
            String wildcard,
            String buildType,
            String buildInstallType,
            Secret buildPassword,
            String buildDescription,
            String buildUpdateDescription,
            String buildChannelShortcut,
            String buildInstallStartDate,
            String buildInstallEndDate) {}

    public static void perform(Run<?, ?> run, FilePath workspace, EnvVars env, TaskListener listener, Settings s)
            throws IOException, InterruptedException {
        Result result = run.getResult();
        if (result != null && result.isWorseThan(Result.UNSTABLE)) {
            listener.getLogger().println("[PGYER] The build " + result + ", so nothing was uploaded.");
            return;
        }
        if ("false".equalsIgnoreCase(env.get("isUploadPgyer", "true").trim())) {
            listener.getLogger().println("[PGYER] isUploadPgyer is false, so nothing was uploaded.");
            return;
        }
        UploadRequest request = new UploadRequest();
        request.legacyApiKey = s.apiKey();
        request.credentialsId = s.credentialsId();
        request.baseDir = s.scanDir();
        request.filePattern = s.wildcard();
        request.buildType = s.buildType();
        request.installType = s.buildInstallType();
        request.password = s.buildPassword() == null ? null : s.buildPassword().getPlainText();
        request.description = s.buildDescription();
        request.updateDescription = s.buildUpdateDescription();
        request.channelShortcut = s.buildChannelShortcut();
        request.installStartDate = s.buildInstallStartDate();
        request.installEndDate = s.buildInstallEndDate();
        Map<String, String> info = PgyerUploader.upload(run, workspace, env, listener, request);
        run.addAction(new PgyerEnvironmentAction(info));
    }

    public static Secret emptyToNull(Secret secret) {
        return secret == null || secret.getPlainText().isEmpty() ? null : secret;
    }

    public static ListBoxModel credentialsItems(Item item, String credentialsId) {
        StandardListBoxModel result = new StandardListBoxModel();
        if (item == null) {
            if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
                return result.includeCurrentValue(credentialsId);
            }
        } else if (!item.hasPermission(Item.EXTENDED_READ) && !item.hasPermission(CredentialsProvider.USE_ITEM)) {
            return result.includeCurrentValue(credentialsId);
        }
        return result.includeEmptyValue()
                .includeAs(ACL.SYSTEM2, item, StringCredentials.class, List.of())
                .includeCurrentValue(credentialsId);
    }

    public static ListBoxModel buildTypeItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Detect from the file extension", "");
        items.add("Android (.apk)", "android");
        items.add("iOS (.ipa)", "ios");
        items.add("HarmonyOS (.hap)", "harmony");
        return items;
    }

    public static ListBoxModel installTypeItems() {
        ListBoxModel items = new ListBoxModel();
        items.add("Keep the app's current setting", "");
        items.add("Public", "1");
        items.add("Password", "2");
        items.add("Invitation only", "3");
        return items;
    }
}
