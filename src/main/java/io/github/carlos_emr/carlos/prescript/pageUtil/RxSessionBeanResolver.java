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
import java.util.LinkedHashMap;
import java.util.Map;

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
     * Upper bound on patients kept per session. A clinic day can touch many charts; the least
     * recently opened patient's bean (and any draft left in it) is dropped beyond this.
     */
    static final int MAX_PATIENTS_PER_SESSION = 25;

    /** Returned by {@link #requestedDemographicNo} when the request names no patient. */
    public static final int NOT_REQUESTED = 0;

    /** Returned by {@link #requestedDemographicNo} when the named patient is malformed or ambiguous. */
    public static final int INVALID = -1;

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
            } else {
                bean.removePersistedStashItems();
            }
            if (providerNo != null) {
                bean.setProviderNo(providerNo);
            }
            session.setAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE, demographicNo);
        }
        return bean;
    }

    /**
     * Makes sure a bean exists for a patient without opening Rx for them: the active patient and
     * the patient's staged drafts are left alone. For pages outside the Rx window (the eChart, the
     * messenger PDF preview) that render Rx fragments for a named patient.
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
            }
            if (session.getAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE) == null) {
                session.setAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE, demographicNo);
            }
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
        if (requested > 0) {
            return find(session, requested);
        }
        Object active = session.getAttribute(ACTIVE_DEMOGRAPHIC_ATTRIBUTE);
        return active instanceof Integer activeDemographicNo ? find(session, activeDemographicNo) : null;
    }

    /**
     * The bean Rx holds for {@code demographicNo} in this session, without consulting the request.
     *
     * @return the bean, or {@code null} when Rx was not opened for that patient
     */
    public static RxSessionBean find(HttpSession session, int demographicNo) {
        if (session == null || demographicNo <= 0) {
            return null;
        }
        synchronized (lockFor(session)) {
            PatientBeans beans = beans(session, false);
            return beans == null ? null : beans.peek(demographicNo);
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
                if (raw == null || raw.isBlank()) {
                    continue;
                }
                int parsed;
                try {
                    parsed = Integer.parseInt(raw.trim());
                } catch (NumberFormatException e) {
                    return INVALID;
                }
                if (parsed <= 0 || (result > 0 && result != parsed)) {
                    return INVALID;
                }
                result = parsed;
            }
        }
        return result;
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

    private static Object lockFor(HttpSession session) {
        // Same fallback Spring's WebUtils.getSessionMutex uses: the container hands every request of
        // a session the same session facade, so it serialises map creation and bean creation.
        return session;
    }

    private static PatientBeans beans(HttpSession session, boolean create) {
        Object existing = session.getAttribute(BEANS_ATTRIBUTE);
        if (existing instanceof PatientBeans beans) {
            return beans;
        }
        if (!create) {
            return null;
        }
        PatientBeans beans = new PatientBeans();
        session.setAttribute(BEANS_ATTRIBUTE, beans);
        return beans;
    }

    /**
     * Per-patient beans in least-recently-opened order, capped at {@link #MAX_PATIENTS_PER_SESSION}.
     * Access is guarded by the session lock in {@link RxSessionBeanResolver}.
     */
    static final class PatientBeans extends LinkedHashMap<Integer, RxSessionBean> {
        @Serial
        private static final long serialVersionUID = 1L;

        PatientBeans() {
            super(16, 0.75f, true);
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

        @Override
        protected boolean removeEldestEntry(Map.Entry<Integer, RxSessionBean> eldest) {
            return size() > MAX_PATIENTS_PER_SESSION;
        }
    }
}
