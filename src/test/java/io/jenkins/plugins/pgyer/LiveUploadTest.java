package io.jenkins.plugins.pgyer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import hudson.model.Label;
import hudson.slaves.DumbSlave;
import hudson.util.Secret;
import java.io.File;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Uploads real packages to pgyer.com. Skipped unless {@code PGYER_LIVE_API_KEY} is set; {@code PGYER_LIVE_FILES}
 * lists the packages to upload, separated by commas.
 *
 * <pre>PGYER_LIVE_API_KEY=... PGYER_LIVE_FILES=/path/app.apk,/path/entry.hap mvn test -Dtest=LiveUploadTest</pre>
 */
@WithJenkins
@EnabledIfEnvironmentVariable(named = "PGYER_LIVE_API_KEY", matches = ".+")
class LiveUploadTest {

    @Test
    void uploadsEachPackageFromAnAgent(JenkinsRule j) throws Exception {
        CredentialsProvider.lookupStores(j.jenkins)
                .iterator()
                .next()
                .addCredentials(
                        Domain.global(),
                        new StringCredentialsImpl(
                                CredentialsScope.GLOBAL,
                                "pgyer-live",
                                "PGYER",
                                Secret.fromString(System.getenv("PGYER_LIVE_API_KEY"))));
        DumbSlave agent = j.createOnlineSlave(Label.get("remote"));

        for (String path : System.getenv("PGYER_LIVE_FILES").split(",")) {
            File file = new File(path.trim());
            WorkflowJob job = j.createProject(WorkflowJob.class);
            FilePath workspace = agent.getWorkspaceFor(job);
            assertNotNull(workspace);
            workspace.child(file.getName()).copyFrom(new FilePath(file));
            job.setDefinition(new CpsFlowDefinition(
                    "node('remote') {\n"
                            + "  def r = pgyerUpload file: '" + file.getName() + "', credentialsId: 'pgyer-live',\n"
                            + "      updateDescription: 'upload-pgyer 3.0 live test, build ${BUILD_NUMBER}'\n"
                            + "  echo \"RESULT ${r.buildName} ${r.buildVersion} ${r.buildFileName} ${r.buildUpdateDescription} ${r.appPgyerURL}\"\n"
                            + "}",
                    true));

            WorkflowRun run = j.buildAndAssertSuccess(job);

            j.assertLogContains("RESULT ", run);
            j.assertLogContains("live test, build 1", run);
            System.out.println(JenkinsRule.getLog(run));
        }
    }
}
