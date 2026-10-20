package io.jenkins.plugins.pgyer;

import static org.junit.jupiter.api.Assertions.assertEquals;

import io.jenkins.plugins.casc.misc.ConfiguredWithCode;
import io.jenkins.plugins.casc.misc.JenkinsConfiguredWithCodeRule;
import io.jenkins.plugins.casc.misc.junit.jupiter.WithJenkinsConfiguredWithCode;
import org.junit.jupiter.api.Test;

@WithJenkinsConfiguredWithCode
class ConfigurationAsCodeTest {

    @Test
    @ConfiguredWithCode("casc.yml")
    void readsTheGlobalSettings(JenkinsConfiguredWithCodeRule j) {
        PgyerGlobalConfiguration config = PgyerGlobalConfiguration.get();
        assertEquals("pgyer-api-key", config.getCredentialsId());
        assertEquals("https://staging.example.com", config.getApiServer());
    }
}
