package io.jenkins.plugins.pgyer;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.AbortException;
import hudson.EnvVars;
import hudson.Extension;
import hudson.FilePath;
import hudson.Util;
import hudson.model.Item;
import hudson.model.Run;
import hudson.model.TaskListener;
import hudson.util.ListBoxModel;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import org.jenkinsci.plugins.workflow.steps.Step;
import org.jenkinsci.plugins.workflow.steps.StepContext;
import org.jenkinsci.plugins.workflow.steps.StepDescriptor;
import org.jenkinsci.plugins.workflow.steps.StepExecution;
import org.jenkinsci.plugins.workflow.steps.SynchronousNonBlockingStepExecution;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * {@code pgyerUpload file: 'app/build/outputs/apk/release/*.apk', credentialsId: 'pgyer-api-key'}
 *
 * <p>Returns a map with the published build, for example {@code result.appPgyerURL}.
 */
public class PgyerUploadStep extends Step {

    private String file;
    private String credentialsId;
    private String buildType;
    private String installType;
    private String password;
    private String description;
    private String updateDescription;
    private String channelShortcut;
    private String installStartDate;
    private String installEndDate;
    private Boolean oversea;
    private Integer processingTimeout;

    @DataBoundConstructor
    public PgyerUploadStep() {}

    public String getFile() {
        return file;
    }

    /** Ant-style pattern relative to the workspace; defaults to any .apk, .ipa or .hap file. */
    @DataBoundSetter
    public void setFile(String file) {
        this.file = Util.fixEmptyAndTrim(file);
    }

    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
    }

    public String getBuildType() {
        return buildType;
    }

    @DataBoundSetter
    public void setBuildType(String buildType) {
        this.buildType = Util.fixEmptyAndTrim(buildType);
    }

    public String getInstallType() {
        return installType;
    }

    @DataBoundSetter
    public void setInstallType(String installType) {
        this.installType = Util.fixEmptyAndTrim(installType);
    }

    public String getPassword() {
        return password;
    }

    @DataBoundSetter
    public void setPassword(String password) {
        this.password = Util.fixEmpty(password);
    }

    public String getDescription() {
        return description;
    }

    @DataBoundSetter
    public void setDescription(String description) {
        this.description = Util.fixEmpty(description);
    }

    public String getUpdateDescription() {
        return updateDescription;
    }

    @DataBoundSetter
    public void setUpdateDescription(String updateDescription) {
        this.updateDescription = Util.fixEmpty(updateDescription);
    }

    public String getChannelShortcut() {
        return channelShortcut;
    }

    @DataBoundSetter
    public void setChannelShortcut(String channelShortcut) {
        this.channelShortcut = Util.fixEmptyAndTrim(channelShortcut);
    }

    public String getInstallStartDate() {
        return installStartDate;
    }

    @DataBoundSetter
    public void setInstallStartDate(String installStartDate) {
        this.installStartDate = Util.fixEmptyAndTrim(installStartDate);
    }

    public String getInstallEndDate() {
        return installEndDate;
    }

    @DataBoundSetter
    public void setInstallEndDate(String installEndDate) {
        this.installEndDate = Util.fixEmptyAndTrim(installEndDate);
    }

    public Boolean getOversea() {
        return oversea;
    }

    @DataBoundSetter
    public void setOversea(Boolean oversea) {
        this.oversea = oversea;
    }

    public Integer getProcessingTimeout() {
        return processingTimeout;
    }

    /** Seconds to wait for PGYER to publish the build after the upload; 600 by default. */
    @DataBoundSetter
    public void setProcessingTimeout(Integer processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    UploadRequest toRequest() {
        UploadRequest request = new UploadRequest();
        request.filePattern = file;
        request.credentialsId = credentialsId;
        request.buildType = buildType;
        request.installType = installType;
        request.password = password;
        request.description = description;
        request.updateDescription = updateDescription;
        request.channelShortcut = channelShortcut;
        request.installStartDate = installStartDate;
        request.installEndDate = installEndDate;
        request.oversea = oversea;
        request.processingTimeout = processingTimeout;
        return request;
    }

    @Override
    public StepExecution start(StepContext context) {
        return new Execution(toRequest(), context);
    }

    private static class Execution extends SynchronousNonBlockingStepExecution<Map<String, String>> {

        private static final long serialVersionUID = 1L;

        private final transient UploadRequest request;

        Execution(UploadRequest request, StepContext context) {
            super(context);
            this.request = request;
        }

        @Override
        protected Map<String, String> run() throws Exception {
            StepContext context = getContext();
            FilePath workspace = context.get(FilePath.class);
            if (workspace == null) {
                throw new AbortException(Messages.PgyerUploadStep_NeedsNode());
            }
            Map<String, String> result = PgyerUploader.upload(
                    context.get(Run.class),
                    workspace,
                    context.get(EnvVars.class),
                    context.get(TaskListener.class),
                    request);
            return new LinkedHashMap<>(result);
        }
    }

    @Extension
    public static class DescriptorImpl extends StepDescriptor {

        @Override
        public String getFunctionName() {
            return "pgyerUpload";
        }

        @NonNull
        @Override
        public String getDisplayName() {
            return Messages.PgyerUploadStep_DisplayName();
        }

        @Override
        public Set<? extends Class<?>> getRequiredContext() {
            return Set.of(Run.class, FilePath.class, EnvVars.class, TaskListener.class);
        }

        @POST
        public ListBoxModel doFillCredentialsIdItems(@AncestorInPath Item item, @QueryParameter String credentialsId) {
            return UploadSupport.credentialsItems(item, credentialsId);
        }

        public ListBoxModel doFillBuildTypeItems() {
            return UploadSupport.buildTypeItems();
        }

        public ListBoxModel doFillInstallTypeItems() {
            return UploadSupport.installTypeItems();
        }
    }
}
