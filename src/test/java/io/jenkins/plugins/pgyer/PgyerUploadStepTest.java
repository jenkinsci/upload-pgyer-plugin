package io.jenkins.plugins.pgyer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.cloudbees.plugins.credentials.CredentialsProvider;
import com.cloudbees.plugins.credentials.CredentialsScope;
import com.cloudbees.plugins.credentials.domains.Domain;
import hudson.model.Label;
import hudson.model.Result;
import hudson.util.Secret;
import java.util.List;
import org.jenkinsci.plugins.plaincredentials.impl.StringCredentialsImpl;
import org.jenkinsci.plugins.workflow.cps.CpsFlowDefinition;
import org.jenkinsci.plugins.workflow.job.WorkflowJob;
import org.jenkinsci.plugins.workflow.job.WorkflowRun;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

@WithJenkins
class PgyerUploadStepTest {

    private JenkinsRule j;
    private FakePgyerServer pgyer;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        pgyer = new FakePgyerServer();
        PgyerGlobalConfiguration.get().setApiServer(pgyer.url());
        CredentialsProvider.lookupStores(j.jenkins)
                .iterator()
                .next()
                .addCredentials(
                        Domain.global(),
                        new StringCredentialsImpl(
                                CredentialsScope.GLOBAL, "pgyer", "PGYER", Secret.fromString("key-from-credentials")));
    }

    @AfterEach
    void tearDown() {
        pgyer.close();
    }

    private WorkflowRun run(String script) throws Exception {
        WorkflowJob job = j.createProject(WorkflowJob.class);
        job.setDefinition(new CpsFlowDefinition(script, true));
        return job.scheduleBuild2(0).waitForStart();
    }

    @Test
    void uploadsFromAnAgentAndReturnsTheBuildInfo() throws Exception {
        j.createOnlineSlave(Label.get("remote"));

        WorkflowRun run = j.waitForCompletion(run("node('remote') {\n"
                + "  writeFile file: 'out/app-release.apk', text: 'APK'\n"
                + "  def result = pgyerUpload file: 'out/*.apk', credentialsId: 'pgyer',\n"
                + "      updateDescription: \"Build ${env.BUILD_NUMBER}\"\n"
                + "  echo \"link=${result.appPgyerURL} name=${result.buildName}\"\n"
                + "}"));

        j.assertBuildStatusSuccess(run);
        j.assertLogContains("link=" + pgyer.url() + "/" + FakePgyerServer.SHORTCUT + " name=My App", run);
        assertThat(pgyer.tokenRequests.get(0), hasEntry("_api_key", "key-from-credentials"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildType", "android"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildUpdateDescription", "Build 1"));
        PgyerBuildAction action = run.getAction(PgyerBuildAction.class);
        assertNotNull(action);
        assertEquals("My App", action.getAppName());
        assertEquals("1.2.0 (build 7)", action.getVersion());
    }

    @Test
    void readsTheApiKeyFromTheEnvironment() throws Exception {
        WorkflowRun run = j.waitForCompletion(run("node {\n"
                + "  writeFile file: 'app.ipa', text: 'IPA'\n"
                + "  withEnv(['PGYER_API_KEY=key-from-env']) { pgyerUpload() }\n"
                + "}"));

        j.assertBuildStatusSuccess(run);
        assertThat(pgyer.tokenRequests.get(0), hasEntry("_api_key", "key-from-env"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildType", "ios"));
    }

    @Test
    void usesTheDefaultCredentials() throws Exception {
        PgyerGlobalConfiguration.get().setCredentialsId("pgyer");

        WorkflowRun run = j.waitForCompletion(
                run("node { writeFile file: 'entry.hap', text: 'HAP'; pgyerUpload file: '*.hap' }"));

        j.assertBuildStatusSuccess(run);
        assertThat(pgyer.tokenRequests.get(0), hasEntry("_api_key", "key-from-credentials"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildType", "harmony"));
    }

    @Test
    void failsWhenNoFileMatches() throws Exception {
        WorkflowRun run = j.waitForCompletion(run("node { pgyerUpload file: '*.apk', credentialsId: 'pgyer' }"));

        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("No file matching '*.apk' found", run);
        assertThat(pgyer.tokenRequests, hasSize(0));
    }

    @Test
    void failsWithoutAnApiKey() throws Exception {
        WorkflowRun run = j.waitForCompletion(run("node { writeFile file: 'a.apk', text: 'A'; pgyerUpload() }"));

        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("No PGYER API key", run);
    }

    @Test
    void failsWithTheApiErrorAndAHint() throws Exception {
        pgyer.tokenError = 1019;

        WorkflowRun run = j.waitForCompletion(
                run("node { writeFile file: 'a.apk', text: 'A'; pgyerUpload credentialsId: 'pgyer' }"));

        j.assertBuildStatus(Result.FAILURE, run);
        j.assertLogContains("PGYER API error 1019", run);
        j.assertLogContains("real-name verification", run);
    }

    @Test
    void doesNotLogTheApiKey() throws Exception {
        WorkflowRun run = j.waitForCompletion(
                run("node { writeFile file: 'a.apk', text: 'A'; pgyerUpload credentialsId: 'pgyer' }"));

        j.assertBuildStatusSuccess(run);
        j.assertLogNotContains("key-from-credentials", run);
    }

    @Test
    void roundTripsThroughTheSnippetGenerator() throws Exception {
        PgyerUploadStep step = new PgyerUploadStep();
        step.setFile("**/*.apk");
        step.setCredentialsId("pgyer");
        step.setInstallType("2");
        step.setPassword("1234");
        step.setUpdateDescription("notes");
        step.setProcessingTimeout(120);

        new org.jenkinsci.plugins.workflow.cps.SnippetizerTester(j)
                .assertRoundTrip(
                        step,
                        "pgyerUpload credentialsId: 'pgyer', file: '**/*.apk', installType: '2', password: '1234', "
                                + "processingTimeout: 120, updateDescription: 'notes'");
    }

    @Test
    void globalConfigurationSurvivesTheConfigPage() throws Exception {
        PgyerGlobalConfiguration config = PgyerGlobalConfiguration.get();
        config.setCredentialsId("pgyer");
        config.setApiServer("https://staging.example.com/");

        j.configRoundtrip();

        assertEquals("pgyer", config.getCredentialsId());
        assertEquals("https://staging.example.com", config.getApiServer());
        assertEquals(List.of("https://staging.example.com"), config.apiServers());
    }
}
