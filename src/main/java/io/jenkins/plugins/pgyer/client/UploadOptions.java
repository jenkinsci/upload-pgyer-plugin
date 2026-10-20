package io.jenkins.plugins.pgyer.client;

import java.io.Serializable;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Parameters of one upload, sent from the controller to the agent that holds the file.
 */
public class UploadOptions implements Serializable {

    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_PROCESSING_TIMEOUT = 600;

    private String apiKey;
    private String buildType;
    private String installType;
    private String password;
    private String description;
    private String updateDescription;
    private String installStartDate;
    private String installEndDate;
    private String channelShortcut;
    private Boolean oversea;
    private String fileName;
    private int processingTimeout = DEFAULT_PROCESSING_TIMEOUT;
    private Integer uploadTimeout;
    private String client;

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getBuildType() {
        return buildType;
    }

    /** One of {@code android}, {@code ios} or {@code harmony}. */
    public void setBuildType(String buildType) {
        this.buildType = buildType;
    }

    public String getInstallType() {
        return installType;
    }

    /** {@code 1} public, {@code 2} password, {@code 3} invitation only. */
    public void setInstallType(String installType) {
        this.installType = installType;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setUpdateDescription(String updateDescription) {
        this.updateDescription = updateDescription;
    }

    /** Dates are {@code yyyy-MM-dd}; both must be set to limit the install period. */
    public void setInstallPeriod(String startDate, String endDate) {
        this.installStartDate = startDate;
        this.installEndDate = endDate;
    }

    public void setChannelShortcut(String channelShortcut) {
        this.channelShortcut = channelShortcut;
    }

    /** {@code null} lets PGYER choose by the client IP address. */
    public void setOversea(Boolean oversea) {
        this.oversea = oversea;
    }

    public String getFileName() {
        return fileName;
    }

    /** The file name users see when they download the build. */
    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public int getProcessingTimeout() {
        return processingTimeout;
    }

    /** Seconds to wait for PGYER to finish processing the uploaded file. */
    public void setProcessingTimeout(int processingTimeout) {
        this.processingTimeout = processingTimeout;
    }

    public Integer getUploadTimeout() {
        return uploadTimeout;
    }

    /** Seconds allowed for sending the file; {@code null} means no limit. */
    public void setUploadTimeout(Integer uploadTimeout) {
        this.uploadTimeout = uploadTimeout;
    }

    public String getClient() {
        return client;
    }

    /** Names the caller in the User-Agent header, such as {@code upload-pgyer-jenkins/3.1 Jenkins/2.479.3}. */
    public void setClient(String client) {
        this.client = client;
    }

    Map<String, String> toTokenForm() {
        Map<String, String> form = new LinkedHashMap<>();
        form.put("_api_key", apiKey);
        form.put("buildType", buildType);
        putIfSet(form, "buildInstallType", installType);
        putIfSet(form, "buildPassword", password);
        putIfSet(form, "buildDescription", description);
        putIfSet(form, "buildUpdateDescription", updateDescription);
        if (isSet(installStartDate) && isSet(installEndDate)) {
            form.put("buildInstallDate", "1");
            form.put("buildInstallStartDate", installStartDate);
            form.put("buildInstallEndDate", installEndDate);
        }
        putIfSet(form, "buildChannelShortcut", channelShortcut);
        if (oversea != null) {
            form.put("oversea", oversea ? "1" : "0");
        }
        return form;
    }

    private static void putIfSet(Map<String, String> form, String key, String value) {
        if (isSet(value)) {
            form.put(key, value);
        }
    }

    private static boolean isSet(String value) {
        return value != null && !value.isBlank();
    }
}
