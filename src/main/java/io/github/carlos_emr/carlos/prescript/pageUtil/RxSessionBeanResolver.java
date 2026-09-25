/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.prescript.pageUtil;

import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import java.io.Serial;
import java.util.Iterator;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.util.WebUtils;

/**
 * The one place prescription code finds the {@link RxSessionBean} (and the matching
 * {@link RxPatientData.Patient}) for a request.
 *
 * <h2>Why</h2>
 * <p>The Rx module used to keep a single {@code "RxSessionBean"} and a single {@code "Patient"}
 * session attribute. Opening Rx for a second patient replaced both, so a prescriber with two
 * charts open could stage, save or archive medications against the wrong patient, and reopening
 * Rx for the same patient built a fresh bean and wiped the staged drafts (#3875).</p>
 *
 * <h2>Model</h2>
 * <ul>
 *   <li>The session holds one bean per patient, keyed by demographic number
 *       ({@link #BEANS_ATTRIBUTE}); a bean never changes patient.</li>
 *   <li>A request that names a patient ({@code demographicNo} or {@code demographic_no}) gets that
 *       patient's bean, or {@code null} when Rx was never opened for that patient in this session.
 *       There is deliberately no fallback to another patient's bean.</li>
 *   <li>A request that names no patient (legacy AJAX and popups that predate this model) gets the
 *       bean of the patient whose Rx page was opened most recently ({@link #ACTIVE_DEMOGRAPHIC_ATTRIBUTE}).
 *       The Rx pages load {@code share/javascript/rx-patient-context.js}, which adds the page's
 *       demographic to every Rx request, so the fallback is a compatibility path, not the norm.</li>
 *   <li>Writes that persist or archive medications must not rely on the fallback: they call
 *       {@link #isRequestForBeanPatient} and refuse when the request does not name the bean's
 *       patient.</li>
 *   <li>Opening Rx for a patient ({@link #activate}) reuses that patient's bean, so staged drafts
 *       survive a reopen; items already saved are dropped from the reused stash.</li>
 * </ul>
 *
 * <p>Resolution never reads a bean out of another request's state, so two tabs for two patients
 * cannot race each other the way a swapped shared attribute would.</p>
 *
 * @since 2026-09-24
 */
public final class RxSessionBeanResolver {

    /** Session attribute holding the per-patient beans, keyed by demographic number. */
    public static final String BEANS_ATTRIBUTE = "RxSessionBeans";

    /** Session attribute holding the demographic number whose Rx page was opened most recently. */
    public static final String ACTIVE_DEMOGRAPHIC_ATTRIBUTE = "RxActiveDemographicNo";

    /** Request attribute caching the patient loaded for this request. */
    static final String PATIENT_REQUEST_ATTRIBUTE = RxSessionBeanResolver.class.getName() + ".patient";

    /**
     * Target number of patients kept per session. Empty beans are evicted above this number;
     * drafts, pending ReRx selections and beans leased by active requests are always retained,
     * even when the target is exceeded.
     */
    static final int MAX_PATIENTS_PER_SESSION = 25;

    /** Returned by {@link #requestedDemographicNo} when the request names no patient. */
    public static final int NOT_REQUESTED = 0;

    /** Returned by {@link #requestedDemographicNo} when the named patient is malformed or ambiguous. */
    public static final int INVALID = -1;

    private static final String LEASES_ATTRIBUTE = RxSessionBeanResolver.class.getName() + ".leases";

    private static final String[] DEMOGRAPHIC_PARAMETERS = {"demographicNo", "demographic_no"};

    private RxSessionBeanResolver() {
    }

    /**
     * Opens Rx for a patient: returns that patient's existing bean, or creates one, and makes the
     * patient the session's active Rx patient. Items already saved are dropped from a reused stash
     * so a completed prescription does not reappear; unsaved drafts are kept.
     *
     * @param request       the current request (a session is created if needed)
     * @param demographicNo the patient, must be positive
     * @param providerNo    the logged-in provider
     * @return the patient's bean, never {@code null}
     * @throws IllegalArgumentException if {@code demographicNo} is not positive
     */
    public static RxSessionBean activate(HttpServletRequest request, int demographicNo, String providerNo) {
        if (demographicNo <= 0) {
            throw new IllegalArgumentException("demographicNo must be positive");
        }
        HttpSession session = request.getSession();
        RxSessionBean bean;
        synchronized (lockFor(session)) {
            PatientBeans beans = beans(session, true);
            bean = beans.get(demographicNo);
            if (bean == null) {
                bean = new RxSessionBean();
                bean.setDemographicNo(demographicNo);
                beans.put(demographicNo, bean);
                RxReprintWorkspace.pruneInactive(session);
            } else {
                bean.removePersistedStashItems();
            }
            if (providerNo != null) {
                bean.setProviderNo(providerNo);
            }
            session.setAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE, demographicNo);
            lease(request, session, beans, bean);
        }
        return bean;
    }

    /**
     * Makes sure a bean exists for a patient without opening Rx for them: the patient's staged
     * drafts are left alone, and so is the active patient, except that a session with no active
     * patient yet takes this patient as its active one (so a later request that names no patient
     * falls back to it). For pages outside the Rx window (the eChart, the messenger PDF preview)
     * that render Rx fragments for a named patient.
     *
     * @param request       the current request (a session is created if needed)
     * @param demographicNo the patient, must be positive
     * @param providerNo    the logged-in provider, used only when a bean is created
     * @return the patient's bean, never {@code null}
     * @throws IllegalArgumentException if {@code demographicNo} is not positive
     */
    public static RxSessionBean ensure(HttpServletRequest request, int demographicNo, String providerNo) {
        if (demographicNo <= 0) {
            throw new IllegalArgumentException("demographicNo must be positive");
        }
        HttpSession session = request.getSession();
        synchronized (lockFor(session)) {
            PatientBeans beans = beans(session, true);
            RxSessionBean bean = beans.peek(demographicNo);
            if (bean == null) {
                bean = new RxSessionBean();
                bean.setDemographicNo(demographicNo);
                bean.setProviderNo(providerNo);
                beans.put(demographicNo, bean);
                RxReprintWorkspace.pruneInactive(session);
            }
            if (session.getAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE) == null) {
                session.setAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE, demographicNo);
            }
            lease(request, session, beans, bean);
            return bean;
        }
    }

    /**
     * Stores an already-built bean as its patient's bean and makes that patient active. For
     * callers that assemble a bean themselves (and for tests); the bean's demographic must be set.
     *
     * @param session the session to store into
     * @param bean    a bean with a positive demographic number
     */
    public static void register(HttpSession session, RxSessionBean bean) {
        if (bean == null || bean.getDemographicNo() <= 0) {
            throw new IllegalArgumentException("bean must carry a positive demographicNo");
        }
        synchronized (lockFor(session)) {
            beans(session, true).put(bean.getDemographicNo(), bean);
            RxReprintWorkspace.pruneInactive(session);
            session.setAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE, bean.getDemographicNo());
        }
    }

    /**
     * The bean for this request: the named patient's bean when the request names one, otherwise
     * the active patient's bean.
     *
     * @param request the current request
     * @return the bean, or {@code null} when there is no session, the named patient is malformed,
     *         or Rx was not opened for the resolved patient in this session
     */
    public static RxSessionBean resolve(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) {
            return null;
        }
        int requested = requestedDemographicNo(request);
        if (requested == INVALID) {
            return null;
        }
        synchronized (lockFor(session)) {
            Object active = session.getAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE);
            int demographicNo = requested;
            if (demographicNo <= 0 && active instanceof Integer activeDemographicNo) {
                demographicNo = activeDemographicNo;
            }
            PatientBeans beans = beans(session, false);
            RxSessionBean bean = beans.peek(demographicNo);
            if (bean != null) {
                lease(request, session, beans, bean);
            }
            return bean;
        }
    }

    /**
     * The bean for a request that changes Rx state (stages, re-prescribes, edits or removes a
     * staged item): only the bean of the patient the request explicitly names, never the
     * no-patient fallback. A write that names no patient would otherwise land in whichever
     * patient's Rx page was opened last, which with two charts open is the other patient; a
     * drug staged there is then saved for that patient by the other window.
     *
     * @param request the current request
     * @return the named patient's bean, or {@code null} when the request names no patient, names
     *         one malformed or ambiguously, or Rx is not open for the named patient
     */
    public static RxSessionBean resolveForWrite(HttpServletRequest request) {
        RxSessionBean bean = resolve(request);
        return isRequestForBeanPatient(request, bean) ? bean : null;
    }

    /**
     * The bean Rx holds for {@code demographicNo} in this session, without consulting the request.
     * This lifecycle inspection does not lease the bean; request handlers that retain or mutate
     * the result must use {@link #resolve(HttpServletRequest)} instead.
     *
     * @return the bean, or {@code null} when Rx was not opened for that patient
     */
    public static RxSessionBean find(HttpSession session, int demographicNo) {
        if (session == null || demographicNo <= 0) {
            return null;
        }
        synchronized (lockFor(session)) {
            return beans(session, false).peek(demographicNo);
        }
    }

    /**
     * The patient a request names through {@code demographicNo} / {@code demographic_no}.
     *
     * @return the demographic number, {@link #NOT_REQUESTED} when none is named, or
     *         {@link #INVALID} when a value is malformed, not positive, or two values disagree
     */
    public static int requestedDemographicNo(HttpServletRequest request) {
        int result = NOT_REQUESTED;
        for (String name : DEMOGRAPHIC_PARAMETERS) {
            String[] values = request.getParameterValues(name);
            if (values == null) {
                continue;
            }
            for (String raw : values) {
                result = mergeRequested(result, raw);
                if (result == INVALID) {
                    return INVALID;
                }
            }
        }
        return result;
    }

    /**
     * {@code current} merged with one more raw value: unchanged for a blank value, the parsed
     * value when it is positive and agrees with {@code current}, otherwise {@link #INVALID}.
     */
    private static int mergeRequested(int current, String raw) {
        if (raw == null || raw.isBlank()) {
            return current;
        }
        int parsed;
        try {
            parsed = Integer.parseInt(raw.trim());
        } catch (NumberFormatException _) {
            return INVALID;
        }
        if (parsed <= 0 || (current > 0 && current != parsed)) {
            return INVALID;
        }
        return parsed;
    }

    /**
     * Whether the request explicitly names the patient this bean belongs to. Code that persists or
     * archives medications calls this so a write can never land on the fallback patient.
     */
    public static boolean isRequestForBeanPatient(HttpServletRequest request, RxSessionBean bean) {
        return bean != null && bean.getDemographicNo() > 0
                && requestedDemographicNo(request) == bean.getDemographicNo();
    }

    /**
     * The patient record matching {@link #resolve(HttpServletRequest)}, loaded once per request.
     * Replaces the old shared {@code "Patient"} session attribute, which followed whichever chart
     * was opened last.
     *
     * @return the patient, or {@code null} when no bean resolves or nobody is logged in
     */
    public static RxPatientData.Patient resolvePatient(HttpServletRequest request) {
        RxSessionBean bean = resolve(request);
        return bean == null ? null : loadPatient(request, bean.getDemographicNo());
    }

    /**
     * The patient record for an explicitly named patient, provided Rx is open for that patient in
     * this session. Used by writes whose form carries its own demographic field.
     *
     * @return the patient, or {@code null} when Rx is not open for {@code demographicNo}
     */
    public static RxPatientData.Patient resolvePatient(HttpServletRequest request, int demographicNo) {
        return find(request.getSession(false), demographicNo) == null ? null : loadPatient(request, demographicNo);
    }

    private static RxPatientData.Patient loadPatient(HttpServletRequest request, int demographicNo) {
        Object cached = request.getAttribute(PATIENT_REQUEST_ATTRIBUTE);
        if (cached instanceof RxPatientData.Patient patient && patient.getDemographicNo() == demographicNo) {
            return patient;
        }
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            return null;
        }
        RxPatientData.Patient patient = RxPatientData.getPatient(loggedInInfo, demographicNo);
        if (patient == null || patient.getDemographic() == null) {
            return null;
        }
        request.setAttribute(PATIENT_REQUEST_ATTRIBUTE, patient);
        return patient;
    }

    /**
     * Pins every bean exposed to a request until the servlet container destroys that request.
     * Holding the bean monitor only during eviction is insufficient: a request may have resolved
     * an empty bean and not yet entered its staging critical section. Acquisition and eviction
     * share the session mutex, so that reference stays attached throughout the request.
     */
    @SuppressWarnings("unchecked")
    private static void lease(HttpServletRequest request, HttpSession session, PatientBeans beans, RxSessionBean bean) {
        List<BeanLease> leases = (List<BeanLease>) request.getAttribute(LEASES_ATTRIBUTE);
        if (leases == null) {
            leases = new ArrayList<>();
            request.setAttribute(LEASES_ATTRIBUTE, leases);
        }
        for (BeanLease existing : leases) {
            if (existing.owner() == beans && existing.bean() == bean) {
                return;
            }
        }
        beans.acquire(bean);
        leases.add(new BeanLease(beans, bean, lockFor(session)));
    }

    /** Called by the registered request listener, including failed and asynchronous requests. */
    static void releaseRequestLeases(jakarta.servlet.ServletRequest request) {
        Object value = request.getAttribute(LEASES_ATTRIBUTE);
        request.removeAttribute(LEASES_ATTRIBUTE);
        if (value instanceof List<?> leases) {
            for (Object entry : leases) {
                if (entry instanceof BeanLease lease) {
                    // Keep the original owner and mutex: logout may already have invalidated the
                    // session. Cleanup neither reads it nor creates a replacement session.
                    synchronized (lease.mutex()) {
                        lease.owner().release(lease.bean());
                    }
                }
            }
        }
    }

    private record BeanLease(PatientBeans owner, RxSessionBean bean, Object mutex) {
    }

    private static Object lockFor(HttpSession session) {
        // Use the same mutex as the reprint workspace, including when Spring installs a custom
        // session mutex, so pruning reprints and evicting patient beans share one lock order.
        return WebUtils.getSessionMutex(session);
    }

    private static PatientBeans beans(HttpSession session, boolean create) {
        Object existing = session.getAttribute(BEANS_ATTRIBUTE);
        if (existing instanceof PatientBeans beans) {
            return beans;
        }
        if (!create) {
            // Not stored: an empty view for readers, so callers never see null.
            return new PatientBeans();
        }
        PatientBeans beans = new PatientBeans();
        session.setAttribute(BEANS_ATTRIBUTE, beans);
        return beans;
    }

    /**
     * Per-patient beans in least-recently-opened order, with a soft cap of {@link #MAX_PATIENTS_PER_SESSION}.
     * Access is guarded by the session lock in {@link RxSessionBeanResolver}.
     */
    static final class PatientBeans extends LinkedHashMap<Integer, RxSessionBean> {
        @Serial
        private static final long serialVersionUID = 1L;

        // Requests do not survive session serialization; restored beans start without leases.
        private transient IdentityHashMap<RxSessionBean, Integer> leases;

        PatientBeans() {
            super(16, 0.75f, true);
        }

        void acquire(RxSessionBean bean) {
            if (leases == null) {
                leases = new IdentityHashMap<>();
            }
            leases.merge(bean, 1, Integer::sum);
        }

        void release(RxSessionBean bean) {
            if (leases != null) {
                leases.computeIfPresent(bean, (key, count) -> count > 1 ? count - 1 : null);
            }
        }

        private boolean isLeased(RxSessionBean bean) {
            return leases != null && leases.containsKey(bean);
        }

        /** Reads without refreshing the patient's recency (only opening Rx does that). */
        RxSessionBean peek(int demographicNo) {
            for (Map.Entry<Integer, RxSessionBean> entry : entrySet()) {
                if (entry.getKey() == demographicNo) {
                    return entry.getValue();
                }
            }
            return null;
        }

        /**
         * Above the target, evicts the least recently opened empty beans. Drafts and pending ReRx
         * selections must never be discarded to meet a cache limit. If all older beans hold work,
         * the map temporarily exceeds the target; later additions prune empty beans back toward
         * it after work is saved or discarded. The newest bean is retained for its caller.
         */
        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, RxSessionBean> eldest) {
            int candidates = size() - 1;
            Iterator<Map.Entry<Integer, RxSessionBean>> oldestFirst = entrySet().iterator();
            while (size() > MAX_PATIENTS_PER_SESSION && candidates-- > 0 && oldestFirst.hasNext()) {
                RxSessionBean bean = oldestFirst.next().getValue();
                if (!isLeased(bean) && !hasStagedWork(bean)) {
                    oldestFirst.remove();
                }
            }
            // LinkedHashMap permits mutation here when false is returned. Never ask it to evict
            // the eldest unconditionally: that patient may still have an unsaved prescription.
            return false;
        }

        private static boolean hasStagedWork(RxSessionBean bean) {
            return bean != null && (bean.getStashSize() > 0
                    || (bean.getReRxDrugIdList() != null && !bean.getReRxDrugIdList().isEmpty()));
        }
    }
}
