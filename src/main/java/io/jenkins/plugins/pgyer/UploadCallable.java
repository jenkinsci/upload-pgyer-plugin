package io.jenkins.plugins.pgyer;

import hudson.model.TaskListener;
import hudson.remoting.VirtualChannel;
import io.jenkins.plugins.pgyer.client.PgyerClient;
import io.jenkins.plugins.pgyer.client.ProxySettings;
import io.jenkins.plugins.pgyer.client.UploadOptions;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import jenkins.MasterToSlaveFileCallable;

/**
 * Runs the upload on the machine that holds the file, so builds on agents don't copy it to the controller.
 */
class UploadCallable extends MasterToSlaveFileCallable<LinkedHashMap<String, String>> {

    private static final long serialVersionUID = 1L;

    private final List<String> apiServers;
    private final ProxySettings proxy;
    private final UploadOptions options;
    private final TaskListener listener;

    UploadCallable(List<String> apiServers, ProxySettings proxy, UploadOptions options, TaskListener listener) {
        this.apiServers = new ArrayList<>(apiServers);
        this.proxy = proxy;
        this.options = options;
        this.listener = listener;
    }

    @Override
    public LinkedHashMap<String, String> invoke(File file, VirtualChannel channel)
            throws IOException, InterruptedException {
        PgyerClient client = new PgyerClient(apiServers, proxy, listener.getLogger());
        return new LinkedHashMap<>(client.upload(file.toPath(), options));
    }
}
