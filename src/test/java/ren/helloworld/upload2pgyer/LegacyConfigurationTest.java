package ren.helloworld.upload2pgyer;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasEntry;
import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import hudson.EnvVars;
import hudson.Launcher;
import hudson.model.AbstractBuild;
import hudson.model.BuildListener;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.model.Label;
import hudson.model.ParametersDefinitionProperty;
import hudson.model.Result;
import hudson.model.StringParameterDefinition;
import hudson.model.TaskListener;
import hudson.tasks.Builder;
import hudson.util.Secret;
import io.jenkins.plugins.pgyer.FakePgyerServer;
import io.jenkins.plugins.pgyer.PgyerGlobalConfiguration;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.jvnet.hudson.test.FailureBuilder;
import org.jvnet.hudson.test.JenkinsRule;
import org.jvnet.hudson.test.TestBuilder;
import org.jvnet.hudson.test.junit.jupiter.WithJenkins;

/**
 * Jobs saved by version 2.1 must keep working after the upgrade.
 */
@WithJenkins
class LegacyConfigurationTest {

    /** The step as version 2.1 saved it, with the defaults its form filled in. */
    private static final String STEP_2_1 = "<apiKey>legacy-key</apiKey>\n"
            + "<scanDir>${WORKSPACE}</scanDir>\n"
            + "<wildcard>**/*.apk</wildcard>\n"
            + "<buildInstallType>2</buildInstallType>\n"
            + "<buildPassword>pw</buildPassword>\n"
            + "<buildUpdateDescription>Uploaded by jenkins upload-pgyer plugin</buildUpdateDescription>\n"
            + "<buildType>android</buildType>\n"
            + "<buildChannelShortcut></buildChannelShortcut>\n";

    private JenkinsRule j;
    private FakePgyerServer pgyer;

    @BeforeEach
    void setUp(JenkinsRule j) throws Exception {
        this.j = j;
        pgyer = new FakePgyerServer();
        PgyerGlobalConfiguration.get().setApiServer(pgyer.url());
    }

    @AfterEach
    void tearDown() {
        pgyer.close();
    }

    private FreeStyleProject projectFrom21(String builders, String publishers) throws Exception {
        String xml = "<?xml version='1.1' encoding='UTF-8'?>\n<project>\n<builders>" + builders
                + "</builders>\n<publishers>" + publishers + "</publishers>\n</project>";
        return (FreeStyleProject) j.jenkins.createProjectFromXML(
                "old-job", new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)));
    }

    private static Builder writeApk() {
        return new TestBuilder() {
            @Override
            public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener)
                    throws java.io.IOException, InterruptedException {
                build.getWorkspace().child("app/build/app-release.apk").write("APK", "UTF-8");
                return true;
            }
        };
    }

    @Test
    void buildStepFrom21UploadsFromAnAgentAndSetsTheSameVariables() throws Exception {
        FreeStyleProject project = projectFrom21(
                "<ren.helloworld.upload2pgyer.UploadBuilderV2 plugin=\"upload-pgyer@2.1\">" + STEP_2_1
                        + "</ren.helloworld.upload2pgyer.UploadBuilderV2>",
                "");
        UploadBuilderV2 step = project.getBuildersList().get(UploadBuilderV2.class);
        assertEquals(Secret.fromString("legacy-key"), step.getApiKey());
        assertEquals("${WORKSPACE}", step.getScanDir());
        assertEquals("**/*.apk", step.getWildcard());
        project.getBuildersList().replaceBy(List.of(writeApk(), step));
        project.setAssignedLabel(j.createOnlineSlave(Label.get("remote")).getSelfLabel());

        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        assertThat(pgyer.tokenRequests.get(0), hasEntry("_api_key", "legacy-key"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildInstallType", "2"));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("buildPassword", "pw"));
        assertThat(
                pgyer.tokenRequests.get(0),
                hasEntry("buildUpdateDescription", "Uploaded by jenkins upload-pgyer plugin"));
        EnvVars env = build.getEnvironment(TaskListener.NULL);
        assertEquals(FakePgyerServer.SHORTCUT, env.get("buildShortcutUrl"));
        assertEquals(FakePgyerServer.BUILD_KEY, env.get("buildKey"));
        assertEquals(pgyer.url() + "/" + FakePgyerServer.SHORTCUT, env.get("appPgyerURL"));
        assertEquals(pgyer.url() + "/" + FakePgyerServer.BUILD_KEY, env.get("appBuildURL"));
        assertEquals(env.get("buildQRCodeURL"), env.get("appQRCodeURL"));
    }

    @Test
    void postBuildActionFrom21Uploads() throws Exception {
        FreeStyleProject project = projectFrom21(
                "",
                "<ren.helloworld.upload2pgyer.UploadPublisherV2 plugin=\"upload-pgyer@2.1\">" + STEP_2_1
                        + "</ren.helloworld.upload2pgyer.UploadPublisherV2>");
        project.getBuildersList().add(writeApk());

        j.buildAndAssertSuccess(project);

        assertThat(pgyer.tokenRequests, hasSize(1));
        assertThat(pgyer.tokenRequests.get(0), hasEntry("_api_key", "legacy-key"));
    }

    @Test
    void postBuildActionSkipsFailedBuilds() throws Exception {
        FreeStyleProject project = projectFrom21(
                "",
                "<ren.helloworld.upload2pgyer.UploadPublisherV2>" + STEP_2_1
                        + "</ren.helloworld.upload2pgyer.UploadPublisherV2>");
        project.getBuildersList().replaceBy(List.of(writeApk(), new FailureBuilder()));

        FreeStyleBuild build = j.buildAndAssertStatus(Result.FAILURE, project);

        j.assertLogContains("[PGYER] The build FAILURE, so nothing was uploaded.", build);
        assertThat(pgyer.tokenRequests, hasSize(0));
    }

    @Test
    void isUploadPgyerFalseSkipsTheUpload() throws Exception {
        FreeStyleProject project = projectFrom21(
                "<ren.helloworld.upload2pgyer.UploadBuilderV2>" + STEP_2_1
                        + "</ren.helloworld.upload2pgyer.UploadBuilderV2>",
                "");
        project.addProperty(new ParametersDefinitionProperty(new StringParameterDefinition("isUploadPgyer", "false")));

        FreeStyleBuild build = j.buildAndAssertSuccess(project);

        j.assertLogContains("isUploadPgyer is false", build);
        assertThat(pgyer.tokenRequests, hasSize(0));
    }

    @Test
    void newSettingsSurviveTheConfigPage() throws Exception {
        UploadBuilderV2 before = new UploadBuilderV2();
        before.setCredentialsId("pgyer");
        before.setScanDir("${WORKSPACE}/app");
        before.setWildcard("**/*.ipa");
        before.setBuildType("ios");
        before.setBuildInstallType("2");
        before.setBuildPassword(Secret.fromString("pw"));
        before.setBuildDescription("My app");
        before.setBuildUpdateDescription("Changes");
        before.setBuildChannelShortcut("beta");
        before.setBuildInstallStartDate("2026-11-01");
        before.setBuildInstallEndDate("2026-11-30");
        FreeStyleProject project = j.createFreeStyleProject();
        project.getBuildersList().add(before);

        j.configRoundtrip(project);

        j.assertEqualDataBoundBeans(before, project.getBuildersList().get(UploadBuilderV2.class));
    }

    @Test
    void freestyleStepsAreNotOfferedToPipeline() {
        // Pipeline jobs use pgyerUpload; the snippet generator would otherwise offer a second, unusable step.
        assertFalse(jenkins.tasks.SimpleBuildStep.class.isAssignableFrom(UploadBuilderV2.class));
        assertFalse(jenkins.tasks.SimpleBuildStep.class.isAssignableFrom(UploadPublisherV2.class));
    }
}
