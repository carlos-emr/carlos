/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import jakarta.servlet.http.HttpSession;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Stores independent prescription workspaces in a user's HTTP session. */
public final class RxWorkspaceRegistry {

    static final String SESSION_KEY = RxWorkspaceRegistry.class.getName();

    static final Set<String> SCOPED_SESSION_KEYS = Collections.unmodifiableSet(Set.of(
            "RxSessionBean", "Patient", "tmpBeanRX", "rePrint", "comment", "RX_ADDR",
            "rxPageSize", "profileViewSpec", "demographicNo", "hideResources"));

    private final Map<String, RxWorkspace> workspaces = new ConcurrentHashMap<>();

    private RxWorkspaceRegistry() {
        // Created through getOrCreate(HttpSession).
    }

    public static RxWorkspaceRegistry getOrCreate(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_KEY);
            if (existing instanceof RxWorkspaceRegistry) {
                return (RxWorkspaceRegistry) existing;
            }
            RxWorkspaceRegistry registry = new RxWorkspaceRegistry();
            session.setAttribute(SESSION_KEY, registry);
            return registry;
        }
    }

    public static RxWorkspaceRegistry get(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        return existing instanceof RxWorkspaceRegistry ? (RxWorkspaceRegistry) existing : null;
    }

    public RxWorkspace create(int demographicNo, String providerNo) {
        if (demographicNo <= 0) {
            throw new IllegalArgumentException("demographicNo must be positive");
        }
        if (providerNo == null || providerNo.isBlank()) {
            throw new IllegalArgumentException("providerNo is required");
        }

        String contextId;
        do {
            contextId = UUID.randomUUID().toString();
        } while (workspaces.containsKey(contextId));

        RxWorkspace workspace = new RxWorkspace(contextId, demographicNo, providerNo);
        workspaces.put(contextId, workspace);
        return workspace;
    }

    public RxWorkspace find(String contextId) {
        return contextId == null ? null : workspaces.get(contextId);
    }

    public void remove(String contextId) {
        if (contextId != null) {
            workspaces.remove(contextId);
        }
    }

    /** Mutable state belonging to exactly one browser prescription flow. */
    public static final class RxWorkspace {
        private final String contextId;
        private final int demographicNo;
        private final String providerNo;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final ReentrantLock requestLock = new ReentrantLock();

        private RxWorkspace(String contextId, int demographicNo, String providerNo) {
            this.contextId = contextId;
            this.demographicNo = demographicNo;
            this.providerNo = providerNo;

            RxSessionBean bean = new RxSessionBean();
            bean.setDemographicNo(demographicNo);
            bean.setProviderNo(providerNo);
            attributes.put("RxSessionBean", bean);
            attributes.put("demographicNo", Integer.toString(demographicNo));
        }

        public String getContextId() {
            return contextId;
        }

        public int getDemographicNo() {
            return demographicNo;
        }

        public String getProviderNo() {
            return providerNo;
        }

        Object getAttribute(String name) {
            return attributes.get(name);
        }

        Set<String> getAttributeNames() {
            return attributes.keySet();
        }

        void setAttribute(String name, Object value) {
            validatePatientState(name, value);
            if (value == null) {
                attributes.remove(name);
            } else {
                attributes.put(name, value);
            }
        }

        void removeAttribute(String name) {
            attributes.remove(name);
        }

        void lock() {
            requestLock.lock();
        }

        void unlock() {
            requestLock.unlock();
        }

        private void validatePatientState(String name, Object value) {
            if (value instanceof RxSessionBean) {
                RxSessionBean bean = (RxSessionBean) value;
                if (bean.getDemographicNo() != demographicNo
                        || (bean.getProviderNo() != null && !providerNo.equals(bean.getProviderNo()))) {
                    throw new IllegalArgumentException(name + " belongs to a different Rx workspace");
                }
            }
            if (value instanceof RxPatientData.Patient
                    && ((RxPatientData.Patient) value).getDemographicNo() != demographicNo) {
                throw new IllegalArgumentException(name + " belongs to a different demographic");
            }
            if ("demographicNo".equals(name) && value != null
                    && !Integer.toString(demographicNo).equals(value.toString())) {
                throw new IllegalArgumentException("demographicNo cannot change within an Rx workspace");
            }
        }
    }
}
