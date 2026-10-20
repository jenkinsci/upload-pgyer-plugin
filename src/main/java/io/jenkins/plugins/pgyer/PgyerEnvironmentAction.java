package io.jenkins.plugins.pgyer;

import hudson.EnvVars;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;
import hudson.model.Run;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes the published build to later Freestyle build steps as environment variables such as
 * {@code ${buildShortcutUrl}} and {@code ${appQRCodeURL}}, using the names earlier versions set.
 */
public class PgyerEnvironmentAction extends InvisibleAction implements EnvironmentContributingAction {

    private final LinkedHashMap<String, String> variables;

    PgyerEnvironmentAction(Map<String, String> variables) {
        this.variables = new LinkedHashMap<>(variables);
    }

    @Override
    public void buildEnvironment(Run<?, ?> run, EnvVars env) {
        env.putAll(variables);
    }
}
