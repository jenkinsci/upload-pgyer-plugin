package io.jenkins.plugins.pgyer.client;

import java.io.IOException;

/**
 * An error code returned by the PGYER API.
 */
public class PgyerApiException extends IOException {

    private static final long serialVersionUID = 1L;

    private final int code;

    public PgyerApiException(int code, String serverMessage) {
        super(format(code, serverMessage));
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    private static String format(int code, String serverMessage) {
        StringBuilder sb = new StringBuilder(Messages.Error_ApiError(String.valueOf(code)));
        if (serverMessage != null && !serverMessage.isBlank()) {
            sb.append(": ").append(serverMessage.trim());
        }
        String hint = hint(code);
        if (hint != null) {
            sb.append(System.lineSeparator()).append(hint);
        }
        return sb.toString();
    }

    static String hint(int code) {
        switch (code) {
            case 1001:
            case 1002:
                return Messages.Hint_ApiKey();
            case 1003:
                return Messages.Hint_SubAccount();
            case 1019:
                return Messages.Hint_Certification();
            case 1020:
                return Messages.Hint_UploadDisabled();
            case 1022:
                return Messages.Hint_Storage();
            default:
                return null;
        }
    }
}
