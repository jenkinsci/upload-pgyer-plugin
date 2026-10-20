package ren.helloworld.upload2pgyer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractProject;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.BuildStepMonitor;
import hudson.tasks.Publisher;
import hudson.tasks.Recorder;
import hudson.util.ListBoxModel;
import hudson.util.Secret;
import io.jenkins.plugins.pgyer.Messages;
import io.jenkins.plugins.pgyer.UploadSupport;
import java.io.IOException;
import jenkins.tasks.SimpleBuildStep;
import org.jenkinsci.Symbol;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * "Upload to PGYER" post-build action for Freestyle jobs.
 *
 * <p>The class name and field names are stored in existing job configurations, so they must not change.
 */
public class UploadPublisherV2 extends Recorder implements SimpleBuildStep {

    private Secret apiKey;
    private String scanDir;
    private String wildcard;
    private String buildType;
    private String buildInstallType;
    private Secret buildPassword;
    private String buildUpdateDescription;
    private String buildChannelShortcut;
    // Added in 3.0
    private String credentialsId;
    private String buildDescription;
    private String buildInstallStartDate;
    private String buildInstallEndDate;

    @DataBoundConstructor
    public UploadPublisherV2() {}

    public Secret getApiKey() {
        return apiKey;
    }

    @DataBoundSetter
    public void setApiKey(Secret apiKey) {
        this.apiKey = UploadSupport.emptyToNull(apiKey);
    }

    public String getScanDir() {
        return scanDir;
    }

    @DataBoundSetter
    public void setScanDir(String scanDir) {
        this.scanDir = Util.fixEmptyAndTrim(scanDir);
    }

    public String getWildcard() {
        return wildcard;
    }

    @DataBoundSetter
    public void setWildcard(String wildcard) {
        this.wildcard = Util.fixEmptyAndTrim(wildcard);
    }

    public String getBuildType() {
        return buildType;
    }

    @DataBoundSetter
    public void setBuildType(String buildType) {
        this.buildType = Util.fixEmptyAndTrim(buildType);
    }

    public String getBuildInstallType() {
        return buildInstallType;
    }

    @DataBoundSetter
    public void setBuildInstallType(String buildInstallType) {
        this.buildInstallType = Util.fixEmptyAndTrim(buildInstallType);
    }

    public Secret getBuildPassword() {
        return buildPassword;
    }

    @DataBoundSetter
    public void setBuildPassword(Secret buildPassword) {
        this.buildPassword = UploadSupport.emptyToNull(buildPassword);
    }

    public String getBuildUpdateDescription() {
        return buildUpdateDescription;
    }

    @DataBoundSetter
    public void setBuildUpdateDescription(String buildUpdateDescription) {
        this.buildUpdateDescription = Util.fixEmpty(buildUpdateDescription);
    }

    public String getBuildChannelShortcut() {
        return buildChannelShortcut;
    }

    @DataBoundSetter
    public void setBuildChannelShortcut(String buildChannelShortcut) {
        this.buildChannelShortcut = Util.fixEmptyAndTrim(buildChannelShortcut);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    public String getBuildDescription() {
        return buildDescription;
    }

    @DataBoundSetter
    public void setBuildDescription(String buildDescription) {
        this.buildDescription = Util.fixEmpty(buildDescription);
    }

    public String getBuildInstallStartDate() {
        return buildInstallStartDate;
    }

    @DataBoundSetter
    public void setBuildInstallStartDate(String buildInstallStartDate) {
        this.buildInstallStartDate = Util.fixEmptyAndTrim(buildInstallStartDate);
    }

    public String getBuildInstallEndDate() {
        return buildInstallEndDate;
    }

    @DataBoundSetter
    public void setBuildInstallEndDate(String buildInstallEndDate) {
        this.buildInstallEndDate = Util.fixEmptyAndTrim(buildInstallEndDate);
    }

    @Override
    public void perform(
            @NonNull Run<?, ?> run,
            @NonNull FilePath workspace,
            @NonNull EnvVars env,
            @NonNull Launcher launcher,
            @NonNull TaskListener listener)
            throws InterruptedException, IOException {
        UploadSupport.perform(
                run,
                workspace,
                env,
                listener,
                new UploadSupport.Settings(
                        apiKey,
                        credentialsId,
                        scanDir,
                        wildcard,
                        buildType,
                        buildInstallType,
                        buildPassword,
                        buildDescription,
                        buildUpdateDescription,
                        buildChannelShortcut,
                        buildInstallStartDate,
                        buildInstallEndDate));
    }

    @Override
    public BuildStepMonitor getRequiredMonitorService() {
        return BuildStepMonitor.NONE;
    }

    @Symbol("upload-pgyer-v2")
    @Extension
    public static final class DescriptorImpl extends BuildStepDescriptor<Publisher> {

        @Override
        public boolean isApplicable(Class<? extends AbstractProject> jobType) {
            return true;
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.UploadStep_DisplayName();
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            return UploadSupport.credentialsItems(item, credentialsId);
        }

        public ListBoxModel doFillBuildTypeItems() {
            return UploadSupport.buildTypeItems();
        }

        public ListBoxModel doFillBuildInstallTypeItems() {
            return UploadSupport.installTypeItems();
        }
    }
}
