package io.jenkins.plugins.pgyer.client;

import java.io.IOException;
import java.io.Serializable;
import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.Proxy;
import java.net.ProxySelector;
import java.net.SocketAddress;
import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * The controller's HTTP proxy settings, copied so the agent can use them.
 */
public class ProxySettings implements Serializable {

    private static final long serialVersionUID = 1L;

    private final String host;
    private final int port;
    private final String username;
    private final String password;
    private final List<Pattern> noProxyHosts;

    public ProxySettings(String host, int port, String username, String password, List<Pattern> noProxyHosts) {
        this.host = host;
        this.port = port;
        this.username = username;
        this.password = password;
        this.noProxyHosts = new ArrayList<>(noProxyHosts);
    }

    ProxySelector selector() {
        Proxy proxy = new Proxy(Proxy.Type.HTTP, InetSocketAddress.createUnresolved(host, port));
        return new ProxySelector() {
            @Override
            public List<Proxy> select(URI uri) {
                String target = uri.getHost();
                for (Pattern p : noProxyHosts) {
                    if (target != null && p.matcher(target).matches()) {
                        return List.of(Proxy.NO_PROXY);
                    }
                }
                return List.of(proxy);
            }

            @Override
            public void connectFailed(URI uri, SocketAddress sa, IOException ioe) {}
        };
    }

    Authenticator authenticator() {
        if (username == null || username.isEmpty()) {
            return null;
        }
        char[] secret = password == null ? new char[0] : password.toCharArray();
        return new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                if (getRequestorType() == RequestorType.PROXY) {
                    return new PasswordAuthentication(username, secret);
                }
                return null;
            }
        };
    }
}
