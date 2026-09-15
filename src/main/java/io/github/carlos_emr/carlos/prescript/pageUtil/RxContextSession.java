/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;

/** Exposes legacy Rx session attributes without writing them to the real session. */
final class RxContextSession implements HttpSession {

    private final HttpSession delegate;
    private final RxWorkspaceRegistry.RxWorkspace workspace;

    RxContextSession(HttpSession delegate, RxWorkspaceRegistry.RxWorkspace workspace) {
        this.delegate = delegate;
        this.workspace = workspace;
    }

    @Override public long getCreationTime() { return delegate.getCreationTime(); }
    @Override public String getId() { return delegate.getId(); }
    @Override public long getLastAccessedTime() { return delegate.getLastAccessedTime(); }
    @Override public ServletContext getServletContext() { return delegate.getServletContext(); }
    @Override public void setMaxInactiveInterval(int interval) { delegate.setMaxInactiveInterval(interval); }
    @Override public int getMaxInactiveInterval() { return delegate.getMaxInactiveInterval(); }

    @Override
    public Object getAttribute(String name) {
        return RxWorkspaceRegistry.SCOPED_SESSION_KEYS.contains(name)
                ? workspace.getAttribute(name) : delegate.getAttribute(name);
    }

    @Override
    public Enumeration<String> getAttributeNames() {
        Set<String> names = new LinkedHashSet<>();
        Enumeration<String> delegateNames = delegate.getAttributeNames();
        while (delegateNames.hasMoreElements()) {
            String name = delegateNames.nextElement();
            if (!RxWorkspaceRegistry.SCOPED_SESSION_KEYS.contains(name)) {
                names.add(name);
            }
        }
        names.addAll(workspace.getAttributeNames());
        return Collections.enumeration(names);
    }

    @Override
    public void setAttribute(String name, Object value) {
        if (RxWorkspaceRegistry.SCOPED_SESSION_KEYS.contains(name)) {
            workspace.setAttribute(name, value);
        } else {
            delegate.setAttribute(name, value);
        }
    }

    @Override
    public void removeAttribute(String name) {
        if (RxWorkspaceRegistry.SCOPED_SESSION_KEYS.contains(name)) {
            workspace.removeAttribute(name);
        } else {
            delegate.removeAttribute(name);
        }
    }

    @Override public void invalidate() { delegate.invalidate(); }
    @Override public boolean isNew() { return delegate.isNew(); }
}
