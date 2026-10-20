package io.jenkins.plugins.pgyer;

import hudson.util.Secret;

/**
 * What a job asked to upload, before environment variables are expanded.
 */
class UploadRequest {
    String credentialsId;
    /** API key typed into jobs created before 3.0. */
    Secret legacyApiKey;
    /** Directory to search in; the workspace when empty. */
    String baseDir;
    /** Ant-style pattern of the file to upload, such as {@code **}{@code /*.apk}. */
    String filePattern;

    String buildType;
    String installType;
    String password;
    String description;
    String updateDescription;
    String channelShortcut;
    String installStartDate;
    String installEndDate;
    Boolean oversea;
    Integer processingTimeout;
}
