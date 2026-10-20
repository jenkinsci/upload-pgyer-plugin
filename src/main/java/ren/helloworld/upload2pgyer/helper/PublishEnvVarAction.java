package ren.helloworld.upload2pgyer.helper;

import hudson.EnvVars;
import hudson.model.AbstractBuild;
import hudson.model.EnvironmentContributingAction;
import hudson.model.InvisibleAction;

/**
 * Recorded in builds made by versions before 3.0; kept so those builds still load. New builds use
 * {@link io.jenkins.plugins.pgyer.PgyerEnvironmentAction}.
 */
@Deprecated
public class PublishEnvVarAction extends InvisibleAction implements EnvironmentContributingAction {
    /**
     * The environment variable key.
     */
    private final String key;

    /**
     * The environment variable value.
     */
    private final String value;

    /**
     * Constructor.
     *
     * @param key   the environment variable key
     * @param value the environment variable value
     */
    public PublishEnvVarAction(String key, String value) {
        this.key = key;
        this.value = value;
    }

    /* (non-Javadoc)
     * @see hudson.model.EnvironmentContributingAction#buildEnvVars(hudson.model.AbstractBuild, hudson.EnvVars)
     */
    public void buildEnvVars(AbstractBuild<?, ?> build, EnvVars env) {
        env.put(key, value);
    }
}
