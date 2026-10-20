package io.jenkins.plugins.pgyer;

import com.cloudbees.plugins.credentials.common.StandardListBoxModel;
import hudson.Extension;
import hudson.Util;
import hudson.security.ACL;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import io.jenkins.plugins.pgyer.client.PgyerClient;
import java.net.URI;
import java.util.List;
import jenkins.model.GlobalConfiguration;
import jenkins.model.Jenkins;
import org.jenkinsci.Symbol;
import org.jenkinsci.plugins.plaincredentials.StringCredentials;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.verb.POST;

/**
 * System-wide defaults, under "Manage Jenkins » System » PGYER".
 */
@Extension
@Symbol("pgyer")
public class PgyerGlobalConfiguration extends GlobalConfiguration {

    private String credentialsId;
    private String apiServer;

    public PgyerGlobalConfiguration() {
        load();
    }

    public static PgyerGlobalConfiguration get() {
        return GlobalConfiguration.all().getInstance(PgyerGlobalConfiguration.class);
    }

    /** The API key used by jobs that don't choose one. */
    public String getCredentialsId() {
        return credentialsId;
    }

    @DataBoundSetter
    public void setCredentialsId(String credentialsId) {
        this.credentialsId = Util.fixEmptyAndTrim(credentialsId);
        save();
    }

    /** Overrides the PGYER API server, for example to test against a staging site. */
    public String getApiServer() {
        return apiServer;
    }

    @DataBoundSetter
    public void setApiServer(String apiServer) {
        String value = Util.fixEmptyAndTrim(apiServer);
        this.apiServer = value == null ? null : value.replaceAll("/+$", "");
        save();
    }

    List<String> apiServers() {
        return apiServer == null ? PgyerClient.DEFAULT_API_SERVERS : List.of(apiServer);
    }

    /** Base URL for the download page links shown to users. */
    String webServer() {
        return apiServer == null ? PgyerClient.DEFAULT_API_SERVERS.get(0) : apiServer;
    }

    @POST
    public ListBoxModel doFillCredentialsIdItems(@QueryParameter String credentialsId) {
        if (!Jenkins.get().hasPermission(Jenkins.ADMINISTER)) {
            return new StandardListBoxModel().includeCurrentValue(credentialsId);
        }
        return new StandardListBoxModel()
                .includeEmptyValue()
                .includeAs(ACL.SYSTEM2, Jenkins.get(), StringCredentials.class, List.of())
                .includeCurrentValue(credentialsId);
    }

    @POST
    public FormValidation doCheckApiServer(@QueryParameter String value) {
        Jenkins.get().checkPermission(Jenkins.ADMINISTER);
        String url = Util.fixEmptyAndTrim(value);
        if (url == null) {
            return FormValidation.ok();
        }
        try {
            URI uri = URI.create(url);
            if (("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())) && uri.getHost() != null) {
                return FormValidation.ok();
            }
        } catch (IllegalArgumentException e) {
            // reported below
        }
        return FormValidation.error("Enter an http:// or https:// URL, such as https://www.pgyer.com");
    }
}
