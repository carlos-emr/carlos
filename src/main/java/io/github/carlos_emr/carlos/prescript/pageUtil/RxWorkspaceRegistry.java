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
import java.util.function.LongSupplier;

/** Stores independent prescription workspaces in a user's HTTP session. */
public final class RxWorkspaceRegistry implements Serializable {

    private static final long serialVersionUID = 1L;
    public static final int MAX_WORKSPACES = 20;
    static final long CLOSING_GRACE_MILLIS = 2 * 60 * 1000L;
    static final long UNCLAIMED_IDLE_MILLIS = 10 * 60 * 1000L;
    static final long ABANDONED_IDLE_MILLIS = 2 * 60 * 60 * 1000L;
    private static final String DEMOGRAPHIC_ATTRIBUTE = "demographicNo";
    private static final Object REGISTRY_CREATION_LOCK = new Object();

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
    private transient LongSupplier clock = System::currentTimeMillis;

    private RxWorkspaceRegistry() {
        // Created through getOrCreate(HttpSession).
    }

    RxWorkspaceRegistry(LongSupplier clock) {
        this.clock = clock;
    }

    public static RxWorkspaceRegistry getOrCreate(HttpSession session) {
        Object existing = session.getAttribute(SESSION_KEY);
        if (existing instanceof RxWorkspaceRegistry registry) {
            return registry;
        }
        synchronized (REGISTRY_CREATION_LOCK) {
            existing = session.getAttribute(SESSION_KEY);
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
        purgeExpired(clock.getAsLong());
        if (workspaces.size() >= MAX_WORKSPACES) {
            throw new WorkspaceLimitException();
        }

        String contextId;
        do {
            contextId = UUID.randomUUID().toString();
        } while (workspaces.containsKey(contextId));

        RxWorkspace workspace = new RxWorkspace(
                contextId, demographicNo, providerNo, appointmentNo, programId, clock.getAsLong());
        workspaces.put(contextId, workspace);
        return workspace;
    }

    public RxWorkspace find(String contextId) {
        return contextId == null ? null : workspaces.get(contextId);
    }

    /** Pins a workspace while a request validates and uses it. */
    public synchronized RxWorkspace acquire(String contextId) {
        RxWorkspace workspace = find(contextId);
        if (workspace != null) {
            workspace.activeRequests++;
        }
        return workspace;
    }

    public synchronized void release(RxWorkspace workspace) {
        if (workspace != null && workspace.activeRequests > 0) {
            workspace.activeRequests--;
        }
    }

    public synchronized void touch(RxWorkspace workspace) {
        if (workspace != null) {
            workspace.lastAccessedAt = clock.getAsLong();
            workspace.closingAt = 0L;
        }
    }

    public synchronized void heartbeat(RxWorkspace workspace) {
        if (workspace != null) {
            long now = clock.getAsLong();
            workspace.lastAccessedAt = now;
            workspace.lastHeartbeatAt = now;
            workspace.closingAt = 0L;
        }
    }

    public synchronized void markClosing(RxWorkspace workspace) {
        if (workspace != null) {
            workspace.closingAt = clock.getAsLong();
        }
    }

    public synchronized void remove(String contextId) {
        if (contextId != null) {
            workspaces.remove(contextId);
        }
    }

    synchronized int size() {
        return workspaces.size();
    }

    synchronized int purgeExpired() {
        return purgeExpired(clock.getAsLong());
    }

    private int purgeExpired(long now) {
        int sizeBefore = workspaces.size();
        workspaces.entrySet().removeIf(entry -> entry.getValue().isExpired(now));
        return sizeBefore - workspaces.size();
    }

    private void readObject(ObjectInputStream input) throws IOException, ClassNotFoundException {
        input.defaultReadObject();
        workspaces = new ConcurrentHashMap<>();
        clock = System::currentTimeMillis;
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
        private final long createdAt;
        private long lastAccessedAt;
        private long lastHeartbeatAt;
        private long closingAt;
        private int activeRequests;

        private RxWorkspace(
                String contextId,
                int demographicNo,
                String providerNo,
                Integer appointmentNo,
                String programId,
                long createdAt) {
            this.contextId = contextId;
            this.demographicNo = demographicNo;
            this.providerNo = providerNo;
            this.appointmentNo = appointmentNo;
            this.programId = programId;
            this.createdAt = createdAt;
            this.lastAccessedAt = createdAt;

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

        private boolean isExpired(long now) {
            if (activeRequests > 0) {
                return false;
            }
            if (closingAt > 0) {
                return now - closingAt >= CLOSING_GRACE_MILLIS;
            }
            long idleSince = Math.max(createdAt, lastAccessedAt);
            long idleLimit = lastHeartbeatAt > 0 ? ABANDONED_IDLE_MILLIS : UNCLAIMED_IDLE_MILLIS;
            return now - idleSince >= idleLimit;
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
