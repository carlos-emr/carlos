/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Preserves the selected Rx context across server-generated URLs and redirects. */
final class RxContextResponse extends HttpServletResponseWrapper {
    private final String contextPath;
    private final String contextId;

    RxContextResponse(HttpServletResponse response, String contextPath, String contextId) {
        super(response);
        this.contextPath = contextPath;
        this.contextId = contextId;
    }

    @Override
    public String encodeURL(String url) {
        return super.encodeURL(addContext(url));
    }

    @Override
    public String encodeRedirectURL(String url) {
        return super.encodeRedirectURL(addContext(url));
    }

    @Override
    public void sendRedirect(String location) throws IOException {
        super.sendRedirect(addContext(location));
    }

    @Override
    public void sendRedirect(String location, boolean clearBuffer) throws IOException {
        super.sendRedirect(addContext(location), clearBuffer);
    }

    @Override
    public void sendRedirect(String location, int status) throws IOException {
        super.sendRedirect(addContext(location), status);
    }

    @Override
    public void sendRedirect(String location, int status, boolean clearBuffer) throws IOException {
        super.sendRedirect(addContext(location), status, clearBuffer);
    }

    private String addContext(String url) {
        if (url == null || url.contains(RxSessionFilter.CONTEXT_PARAMETER + "=")) {
            return url;
        }
        String rxRoot = contextPath + "/rx/";
        if (!url.startsWith(rxRoot) || url.startsWith(contextPath + "/rx/choosePatient")) {
            return url;
        }

        int fragmentIndex = url.indexOf('#');
        String fragment = fragmentIndex >= 0 ? url.substring(fragmentIndex) : "";
        String base = fragmentIndex >= 0 ? url.substring(0, fragmentIndex) : url;
        return base + (base.contains("?") ? '&' : '?')
                + RxSessionFilter.CONTEXT_PARAMETER + '='
                + URLEncoder.encode(contextId, StandardCharsets.UTF_8) + fragment;
    }
}
