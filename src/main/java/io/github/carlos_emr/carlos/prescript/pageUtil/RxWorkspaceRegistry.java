/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.Serializable;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/** Stores independent prescription workspaces in a user's HTTP session. */
public final class RxWorkspaceRegistry implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MAX_WORKSPACES = 20;
    private static final String DEMOGRAPHIC_ATTRIBUTE = "demographicNo";

    static final String SESSION_KEY = RxWorkspaceRegistry.class.getName();

    static final Set<String> SCOPED_SESSION_KEYS = Collections.unmodifiableSet(Set.of(
            "RxSessionBean", "Patient", "tmpBeanRX", "rePrint", "comment", "RX_ADDR",
            "rxPageSize", "profileViewSpec", DEMOGRAPHIC_ATTRIBUTE, "hideResources"));

    /*
     * RxSessionBean's legacy object graph is not safely serializable. Prescription
     * drafts therefore deliberately expire if the container passivates a login
     * session; the filter will fail a stale URL closed after activation.
     */
    private transient Map<String, RxWorkspace> workspaces = new ConcurrentHashMap<>();

    private RxWorkspaceRegistry() {
        // Created through getOrCreate(HttpSession).
    }

    public static RxWorkspaceRegistry getOrCreate(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_KEY);
            if (existing instanceof RxWorkspaceRegistry registry) {
                return registry;
            }
            RxWorkspaceRegistry registry = new RxWorkspaceRegistry();
            session.setAttribute(SESSION_KEY, registry);
            return registry;
        }
    }

    public static RxWorkspaceRegistry get(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        return existing instanceof RxWorkspaceRegistry registry ? registry : null;
    }

    public RxWorkspace create(int demographicNo, String providerNo) {
        return create(demographicNo, providerNo, null, null);
    }

    public synchronized RxWorkspace create(
            int demographicNo, String providerNo, Integer appointmentNo, String programId) {
        if (demographicNo <= 0) {
            throw new IllegalArgumentException("demographicNo must be positive");
        }
        if (providerNo == null || providerNo.isBlank()) {
            throw new IllegalArgumentException("providerNo is required");
        }
        if (workspaces.size() >= MAX_WORKSPACES) {
            throw new WorkspaceLimitException();
        }

        String contextId;
        do {
            contextId = UUID.randomUUID().toString();
        } while (workspaces.containsKey(contextId));

        RxWorkspace workspace = new RxWorkspace(
                contextId, demographicNo, providerNo, appointmentNo, programId);
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

    int size() {
        return workspaces.size();
    }

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        workspaces = new ConcurrentHashMap<>();
    }

    static final class WorkspaceLimitException extends IllegalStateException {
        private static final long serialVersionUID = 1L;

        private WorkspaceLimitException() {
            super("Prescription workspace limit reached");
        }
    }

    /** Mutable state belonging to exactly one browser prescription flow. */
    public static final class RxWorkspace {
        private final String contextId;
        private final int demographicNo;
        private final String providerNo;
        private final Integer appointmentNo;
        private final String programId;
        private final Map<String, Object> attributes = new ConcurrentHashMap<>();
        private final ReentrantLock requestLock = new ReentrantLock();

        private RxWorkspace(
                String contextId,
                int demographicNo,
                String providerNo,
                Integer appointmentNo,
                String programId) {
            this.contextId = contextId;
            this.demographicNo = demographicNo;
            this.providerNo = providerNo;
            this.appointmentNo = appointmentNo;
            this.programId = programId;

            RxSessionBean bean = new RxSessionBean();
            bean.setDemographicNo(demographicNo);
            bean.setProviderNo(providerNo);
            attributes.put("RxSessionBean", bean);
            attributes.put(DEMOGRAPHIC_ATTRIBUTE, Integer.toString(demographicNo));
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

        public Integer getAppointmentNo() {
            return appointmentNo;
        }

        public String getProgramId() {
            return programId;
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
            if (value instanceof RxSessionBean bean) {
                if (bean.getDemographicNo() != demographicNo
                        || (bean.getProviderNo() != null && !providerNo.equals(bean.getProviderNo()))) {
                    throw new IllegalArgumentException(name + " belongs to a different Rx workspace");
                }
            }
            if (value instanceof RxPatientData.Patient patient
                    && patient.getDemographicNo() != demographicNo) {
                throw new IllegalArgumentException(name + " belongs to a different demographic");
            }
            if (DEMOGRAPHIC_ATTRIBUTE.equals(name) && value != null
                    && !Integer.toString(demographicNo).equals(value.toString())) {
                throw new IllegalArgumentException("demographicNo cannot change within an Rx workspace");
            }
        }
    }
}
