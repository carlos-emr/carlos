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

import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.data.RxPatientData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Per-patient Rx session state (issue #3875): two patients in one HTTP session stay isolated,
 * reopening Rx for a patient keeps that patient's drafts, a request naming a patient never falls
 * back to another patient's bean, and requests that name no patient use the active patient.
 *
 * @since 2026-09-24
 */
@DisplayName("RxSessionBeanResolver per-patient isolation")
@Tag("unit")
@Tag("prescript")
@Tag("security")
class RxSessionBeanResolverUnitTest {

    private static final int PATIENT_A = 1001;
    private static final int PATIENT_B = 2002;
    private static final String PROVIDER = "999998";

    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        session = new MockHttpSession();
    }

    private MockHttpServletRequest request(String... params) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setSession(session);
        for (int i = 0; i < params.length; i += 2) {
            request.addParameter(params[i], params[i + 1]);
        }
        return request;
    }

    private RxSessionBean activateCompleted(int demographicNo, String providerNo) {
        MockHttpServletRequest request = request();
        RxSessionBean bean = RxSessionBeanResolver.activate(request, demographicNo, providerNo);
        RxSessionBeanResolver.releaseRequestLeases(request);
        return bean;
    }

    private RxSessionBean ensureCompleted(int demographicNo, String providerNo) {
        MockHttpServletRequest request = request();
        RxSessionBean bean = RxSessionBeanResolver.ensure(request, demographicNo, providerNo);
        RxSessionBeanResolver.releaseRequestLeases(request);
        return bean;
    }

    private static RxPrescriptionData.Prescription draft(long randomId) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(0, PROVIDER, 0);
        rx.setRandomId(randomId);
        return rx;
    }

    private static RxPrescriptionData.Prescription saved(long randomId, int drugId) {
        return withDrugId(draft(randomId), drugId);
    }

    private static RxPrescriptionData.Prescription withDrugId(RxPrescriptionData.Prescription rx, int drugId) {
        RxPrescriptionData.Prescription copy = new RxPrescriptionData.Prescription(drugId, PROVIDER, 0);
        copy.setRandomId(rx.getRandomId());
        return copy;
    }

    @Test
    @DisplayName("should keep two patients' stashes apart within one HTTP session")
    void shouldIsolateStashes_forTwoPatientsInOneSession() {
        RxSessionBean beanA = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        beanA.getStashList().add(draft(11));
        RxSessionBean beanB = RxSessionBeanResolver.activate(request(), PATIENT_B, PROVIDER);
        beanB.getStashList().add(draft(22));

        RxSessionBean resolvedA = RxSessionBeanResolver.resolve(request("demographicNo", String.valueOf(PATIENT_A)));
        RxSessionBean resolvedB = RxSessionBeanResolver.resolve(request("demographic_no", String.valueOf(PATIENT_B)));

        assertThat(resolvedA).isSameAs(beanA);
        assertThat(resolvedB).isSameAs(beanB);
        assertThat(resolvedA.getDemographicNo()).isEqualTo(PATIENT_A);
        assertThat(resolvedA.getStashList()).extracting(RxPrescriptionData.Prescription::getRandomId).containsExactly(11L);
        assertThat(resolvedB.getStashList()).extracting(RxPrescriptionData.Prescription::getRandomId).containsExactly(22L);
    }

    @Test
    @DisplayName("should keep unsaved drafts when Rx is reopened for the same patient")
    void shouldKeepDrafts_whenRxReopenedForSamePatient() {
        RxSessionBean first = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        first.getStashList().add(draft(11));
        first.getStashList().add(saved(12, 555));
        first.setStashIndex(0);
        RxSessionBeanResolver.activate(request(), PATIENT_B, PROVIDER);

        RxSessionBean reopened = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);

        assertThat(reopened).isSameAs(first);
        // The draft survives; the item a completed save already persisted does not reappear.
        assertThat(reopened.getStashList()).extracting(RxPrescriptionData.Prescription::getRandomId).containsExactly(11L);
        assertThat(reopened.getStashIndex()).isZero();
    }

    @Test
    @DisplayName("should return null rather than another patient's bean for a patient Rx was never opened for")
    void shouldReturnNull_whenNamedPatientHasNoBean() {
        RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);

        assertThat(RxSessionBeanResolver.resolve(request("demographicNo", String.valueOf(PATIENT_B)))).isNull();
    }

    @Test
    @DisplayName("should fall back to the active patient for an AJAX request that names no patient")
    void shouldUseActivePatient_whenRequestNamesNoPatient() {
        RxSessionBean beanA = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        RxSessionBean beanB = RxSessionBeanResolver.activate(request(), PATIENT_B, PROVIDER);

        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanB);

        RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        assertThat(RxSessionBeanResolver.resolve(request("parameterValue", "iterateStash"))).isSameAs(beanA);
    }

    @Test
    @DisplayName("an unnamed request keeps its bean and patient when another window becomes active")
    void shouldPinFallbackPatient_whenActivePatientChangesWithinRequest() {
        RxSessionBean beanA = activateCompleted(PATIENT_A, PROVIDER);
        MockHttpServletRequest rendering = request();
        RxPatientData.Patient patientA = mock(RxPatientData.Patient.class);
        when(patientA.getDemographicNo()).thenReturn(PATIENT_A);
        rendering.setAttribute(RxSessionBeanResolver.PATIENT_REQUEST_ATTRIBUTE, patientA);
        assertThat(RxSessionBeanResolver.resolve(rendering)).isSameAs(beanA);
        assertThat(RxSessionBeanResolver.resolvePatient(rendering)).isSameAs(patientA);

        RxSessionBean beanB = activateCompleted(PATIENT_B, PROVIDER);

        assertThat(RxSessionBeanResolver.resolve(rendering)).isSameAs(beanA);
        assertThat(RxSessionBeanResolver.resolvePatient(rendering)).isSameAs(patientA);
        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanB);
        // Pinning a legacy read never supplies the explicit patient required by writes.
        assertThat(RxSessionBeanResolver.resolveForWrite(rendering)).isNull();
    }

    @Test
    @DisplayName("an unnamed request with no active patient stays empty if another window opens Rx")
    void shouldPinMissingFallback_whenPatientActivatedDuringRequest() {
        MockHttpServletRequest rendering = request();
        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();

        RxSessionBean beanA = activateCompleted(PATIENT_A, PROVIDER);

        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();
        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanA);
    }

    @Test
    @DisplayName("an active id without a bean stays unresolved if its patient is later reopened")
    void shouldPinMissingBean_whenPatientReopenedDuringRequest() {
        session.setAttribute(RxSessionBeanResolver.ACTIVE_DEMOGRAPHIC_ATTRIBUTE, PATIENT_A);
        MockHttpServletRequest rendering = request();
        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();

        RxSessionBean beanA = activateCompleted(PATIENT_A, PROVIDER);

        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();
        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanA);
    }

    @Test
    @DisplayName("explicit and invalid patient parameters take precedence over a captured fallback")
    void shouldValidateExplicitPatient_whenFallbackWasAlreadyCaptured() {
        RxSessionBean beanA = activateCompleted(PATIENT_A, PROVIDER);
        MockHttpServletRequest rendering = request();
        assertThat(RxSessionBeanResolver.resolve(rendering)).isSameAs(beanA);
        RxSessionBean beanB = activateCompleted(PATIENT_B, PROVIDER);

        rendering.setParameter("demographicNo", String.valueOf(PATIENT_B));
        assertThat(RxSessionBeanResolver.resolve(rendering)).isSameAs(beanB);
        rendering.setParameter("demographicNo", "malformed");
        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();
        rendering.setParameter("demographicNo", String.valueOf(PATIENT_B));
        rendering.setParameter("demographic_no", String.valueOf(PATIENT_A));
        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();
        rendering.removeParameter("demographic_no");
        rendering.setParameter("demographicNo", "9999");
        assertThat(RxSessionBeanResolver.resolve(rendering)).isNull();
    }

    @Test
    @DisplayName("resolving a request without a session does not create one")
    void shouldKeepFallbackEmpty_whenSessionCreatedAfterFirstResolution() {
        MockHttpServletRequest noSession = new MockHttpServletRequest();

        assertThat(RxSessionBeanResolver.resolve(noSession)).isNull();
        assertThat(RxSessionBeanResolver.resolveForWrite(noSession)).isNull();
        assertThat(noSession.getSession(false)).isNull();

        RxSessionBean beanA = activateCompleted(PATIENT_A, PROVIDER);
        noSession.setSession(session);
        assertThat(RxSessionBeanResolver.resolve(noSession)).isNull();
        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanA);
    }

    @Test
    @DisplayName("should not change the active patient when a bean is only ensured")
    void shouldKeepActivePatient_whenBeanOnlyEnsured() {
        RxSessionBean beanA = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        beanA.getStashList().add(draft(11));

        RxSessionBean beanB = RxSessionBeanResolver.ensure(request(), PATIENT_B, PROVIDER);

        assertThat(beanB.getDemographicNo()).isEqualTo(PATIENT_B);
        assertThat(RxSessionBeanResolver.resolve(request())).isSameAs(beanA);
        assertThat(beanA.getStashSize()).isEqualTo(1);
    }

    @Test
    @DisplayName("should reject a request whose two patient parameters disagree")
    void shouldReturnNull_whenPatientParametersConflict() {
        RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        RxSessionBeanResolver.activate(request(), PATIENT_B, PROVIDER);

        MockHttpServletRequest conflicting = request(
                "demographicNo", String.valueOf(PATIENT_A), "demographic_no", String.valueOf(PATIENT_B));

        assertThat(RxSessionBeanResolver.requestedDemographicNo(conflicting)).isEqualTo(RxSessionBeanResolver.INVALID);
        assertThat(RxSessionBeanResolver.resolve(conflicting)).isNull();
        assertThat(RxSessionBeanResolver.resolve(request("demographicNo", "12abc"))).isNull();
    }

    @Test
    @DisplayName("should only accept a write that names the bean's own patient")
    void shouldRejectWrite_whenRequestPatientMismatchesBean() {
        RxSessionBean beanA = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);

        assertThat(RxSessionBeanResolver.isRequestForBeanPatient(
                request("demographicNo", String.valueOf(PATIENT_A)), beanA)).isTrue();
        assertThat(RxSessionBeanResolver.isRequestForBeanPatient(
                request("demographicNo", String.valueOf(PATIENT_B)), beanA)).isFalse();
        // The fallback is for reads: a write that names no patient is not trusted.
        assertThat(RxSessionBeanResolver.isRequestForBeanPatient(request(), beanA)).isFalse();
    }

    @Test
    @DisplayName("should cap the number of patients kept in one session")
    void shouldEvictLeastRecentlyOpenedPatient_whenCapExceeded() {
        for (int demo = 1; demo <= RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 1; demo++) {
            activateCompleted(demo, PROVIDER);
        }

        assertThat(RxSessionBeanResolver.find(session, 1)).isNull();
        assertThat(RxSessionBeanResolver.find(session, 2)).isNotNull();
        assertThat(RxSessionBeanResolver.find(session, RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 1)).isNotNull();
    }

    @Test
    @DisplayName("should keep a patient's staged drafts over empty beans when the cap is exceeded")
    void shouldEvictPatientWithoutStagedWork_whenCapExceeded() {
        RxSessionBean withDraft = activateCompleted(1, PROVIDER);
        withDraft.getStashList().add(draft(11));
        RxSessionBean withReRx = activateCompleted(2, PROVIDER);
        withReRx.getReRxDrugIdList().add("77");
        // eChart Prescriptions tabs and messenger previews for many other patients.
        for (int demo = 3; demo <= RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 1; demo++) {
            ensureCompleted(demo, PROVIDER);
        }

        assertThat(RxSessionBeanResolver.find(session, 1)).isSameAs(withDraft);
        assertThat(RxSessionBeanResolver.find(session, 2)).isSameAs(withReRx);
        // The least recently opened patient with nothing staged went instead.
        assertThat(RxSessionBeanResolver.find(session, 3)).isNull();
        assertThat(RxSessionBeanResolver.find(session, RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 1)).isNotNull();
    }

    @Test
    @DisplayName("should preserve every draft and the new patient when all older beans hold work")
    void shouldPreserveAllPatients_whenEveryOlderBeanHasDrafts() {
        for (int demo = 1; demo <= RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION; demo++) {
            activateCompleted(demo, PROVIDER).getStashList().add(draft(demo));
        }
        int newest = RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 1;

        RxSessionBean opened = activateCompleted(newest, PROVIDER);

        assertThat(RxSessionBeanResolver.find(session, newest)).isSameAs(opened);
        for (int demo = 1; demo < newest; demo++) {
            assertThat(RxSessionBeanResolver.find(session, demo)).isNotNull();
            assertThat(RxSessionBeanResolver.find(session, demo).getStash())
                    .extracting(RxPrescriptionData.Prescription::getRandomId).containsExactly((long) demo);
        }
    }

    @Test
    @DisplayName("should preserve pending ReRx selections when every older patient holds work")
    void shouldPreserveReRxSelections_whenTargetExceeded() {
        int count = RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 3;
        for (int demo = 1; demo <= count; demo++) {
            ensureCompleted(demo, PROVIDER).addReRxDrugIdList("77");
        }

        for (int demo = 1; demo <= count; demo++) {
            assertThat(RxSessionBeanResolver.find(session, demo)).isNotNull();
            assertThat(RxSessionBeanResolver.find(session, demo).getReRxDrugIdList()).containsExactly("77");
        }
    }

    @Test
    @DisplayName("should shrink back to the target after formerly dirty beans become empty")
    void shouldPruneEmptyBeans_afterDraftsWereDiscarded() {
        int count = RxSessionBeanResolver.MAX_PATIENTS_PER_SESSION + 3;
        for (int demo = 1; demo <= count; demo++) {
            activateCompleted(demo, PROVIDER).getStashList().add(draft(demo));
        }
        for (int demo = 1; demo <= 4; demo++) {
            RxSessionBeanResolver.find(session, demo).clearStash();
        }

        activateCompleted(count + 1, PROVIDER);

        for (int demo = 1; demo <= 4; demo++) {
            assertThat(RxSessionBeanResolver.find(session, demo)).isNull();
        }
        for (int demo = 5; demo <= count + 1; demo++) {
            assertThat(RxSessionBeanResolver.find(session, demo)).isNotNull();
        }
    }

    @Test
    @DisplayName("should resolve a write only for an explicitly named patient, never the fallback")
    void shouldRefuseFallback_forWriteResolution() {
        RxSessionBean beanA = RxSessionBeanResolver.activate(request(), PATIENT_A, PROVIDER);
        RxSessionBeanResolver.activate(request(), PATIENT_B, PROVIDER);

        assertThat(RxSessionBeanResolver.resolveForWrite(request("demographicNo", String.valueOf(PATIENT_A))))
                .isSameAs(beanA);
        // No patient named: a read would get B, the most recently opened; a write gets nothing.
        assertThat(RxSessionBeanResolver.resolveForWrite(request())).isNull();
        assertThat(RxSessionBeanResolver.resolveForWrite(request("demographicNo", "3003"))).isNull();
        assertThat(RxSessionBeanResolver.resolveForWrite(request("demographicNo", "abc"))).isNull();
    }

    @Test
    @DisplayName("should refuse to open Rx without a valid patient")
    void shouldThrow_whenDemographicNotPositive() {
        MockHttpServletRequest patientlessRequest = request();

        assertThatThrownBy(() -> RxSessionBeanResolver.activate(patientlessRequest, 0, PROVIDER))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
