/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpSession;

/** Supplies the context-scoped session facade to downstream actions and JSPs. */
final class RxContextRequest extends HttpServletRequestWrapper {
    private final RxContextSession session;

    RxContextRequest(HttpServletRequest request, RxWorkspaceRegistry.RxWorkspace workspace) {
        super(request);
        this.session = new RxContextSession(request.getSession(), workspace);
    }

    @Override public HttpSession getSession() { return session; }
    @Override public HttpSession getSession(boolean create) { return session; }
}
