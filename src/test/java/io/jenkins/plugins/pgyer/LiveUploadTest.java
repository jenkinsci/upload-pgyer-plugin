package io.jenkins.plugins.pgyer;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.FilePath;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.TaskListener;
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
import ren.helloworld.upload2pgyer.UploadBuilderV2;

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
        addCredentials(j);
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

    @Test
    void freestyleBuildStepUploadsFromAnAgent(JenkinsRule j) throws Exception {
        addCredentials(j);
        DumbSlave agent = j.createOnlineSlave(Label.get("remote"));
        File file = new File(System.getenv("PGYER_LIVE_FILES").split(",")[0].trim());
        FreeStyleProject project = j.createFreeStyleProject();
        project.setAssignedLabel(agent.getSelfLabel());
        FilePath workspace = agent.getWorkspaceFor(project);
        assertNotNull(workspace);
        workspace.child("build/" + file.getName()).copyFrom(new FilePath(file));
        UploadBuilderV2 upload = new UploadBuilderV2();
        upload.setCredentialsId("pgyer-live");
        upload.setScanDir("${WORKSPACE}/build");
        upload.setBuildUpdateDescription("upload-pgyer 3.0 Freestyle live test, build ${BUILD_NUMBER}");
        project.getBuildersList().add(upload);

        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        String url = build.getEnvironment(TaskListener.NULL).get("appPgyerURL");
        assertNotNull(url);
        System.out.println(JenkinsRule.getLog(build));
        System.out.println("appPgyerURL=" + url);
    }

    private static void addCredentials(JenkinsRule j) throws Exception {
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
    }
}
