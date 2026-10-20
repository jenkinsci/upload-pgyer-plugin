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
            listener.getLogger().println(Messages.Log_SkipFailedBuild(result.toString()));
            return;
        }
        if ("false".equalsIgnoreCase(env.get("isUploadPgyer", "true").trim())) {
            listener.getLogger().println(Messages.Log_SkipDisabled());
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
        items.add(Messages.BuildType_Detect(), "");
        items.add(Messages.BuildType_Android(), "android");
        items.add(Messages.BuildType_Ios(), "ios");
        items.add(Messages.BuildType_Harmony(), "harmony");
        return items;
    }

    public static ListBoxModel installTypeItems() {
        ListBoxModel items = new ListBoxModel();
        items.add(Messages.InstallType_Keep(), "");
        items.add(Messages.InstallType_Public(), "1");
        items.add(Messages.InstallType_Password(), "2");
        items.add(Messages.InstallType_Invitation(), "3");
        return items;
    }
}
