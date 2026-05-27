/**
 * File: OAuthCallbackUrls.java
 *
 * Purpose:
 *   Normalizes and validates OAuth callback URLs before they are stored or
 *   used as redirect targets.
 */

package io.github.carlos_emr.carlos.webserv.oauth.util;

import io.github.carlos_emr.carlos.webserv.oauth.OAuth1Exception;

import java.net.URI;

public final class OAuthCallbackUrls {

    private OAuthCallbackUrls() {
    }

    public static String normalizeHttpCallback(String url) {
        URI uri = parse(url);
        validateHttpCallback(uri);

        String scheme = uri.getScheme().toLowerCase();
        String host = uri.getHost().toLowerCase();
        int port = uri.getPort();
        if ((port == 80 && "http".equals(scheme)) || (port == 443 && "https".equals(scheme))) {
            port = -1;
        }
        String path = (uri.getPath() == null || uri.getPath().isEmpty()) ? "/" : uri.getPath();

        try {
            return new URI(scheme, uri.getUserInfo(), host, port, path, uri.getQuery(), uri.getFragment()).toString();
        } catch (Exception e) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
    }

    public static URI requireHttpCallbackUri(String url) {
        URI uri = parse(url);
        validateHttpCallback(uri);
        return uri;
    }

    private static URI parse(String url) {
        try {
            return URI.create(url).normalize();
        } catch (Exception e) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
    }

    private static void validateHttpCallback(URI uri) {
        String scheme = uri.getScheme();
        if (!"http".equalsIgnoreCase(scheme) && !"https".equalsIgnoreCase(scheme)) {
            throw new OAuth1Exception(400, "invalid_callback_scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new OAuth1Exception(400, "invalid_callback");
        }
    }
}
