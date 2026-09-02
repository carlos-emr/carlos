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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.commn.model.DigitalSignature;
import io.github.carlos_emr.carlos.commn.model.Facility;
import io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PrescriptionSignatureStampService}: the stamp is persisted and linked to the
 * script and the stash when eligible, and every guard leaves the script untouched.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("Prescription signature stamp service")
@Tag("unit")
@Tag("prescript")
class PrescriptionSignatureStampServiceUnitTest {

    private static final String PROVIDER_NO = "999998";
    private static final int DEMOGRAPHIC_NO = 42;
    private static final String SCRIPT_ID = "1234";
    private static final int SIGNATURE_ID = 77;

    @Mock
    private DigitalSignatureManager digitalSignatureManager;

    @Mock
    private PrescriptionManager prescriptionManager;

    @Mock
    private PrescriptionDao prescriptionDao;

    @Mock
    private LoggedInInfo loggedInInfo;

    private PrescriptionSignatureStampService service;
    private RxSessionBean bean;
    private RxPrescriptionData.Prescription stashItem;
    private Facility facility;
    private boolean hadRxSignatureEnabled;
    private Object originalRxSignatureEnabled;
    private boolean hadRxFaxEnabled;
    private Object originalRxFaxEnabled;

    @BeforeEach
    void setUp() {
        CarlosProperties props = CarlosProperties.getInstance();
        hadRxSignatureEnabled = props.containsKey("rx_signature_enabled");
        originalRxSignatureEnabled = props.get("rx_signature_enabled");
        hadRxFaxEnabled = props.containsKey("rx_fax_enabled");
        originalRxFaxEnabled = props.get("rx_fax_enabled");
        props.setProperty("rx_signature_enabled", "true");
        props.setProperty("rx_fax_enabled", "false");

        facility = new Facility();
        facility.setId(11);
        facility.setEnableDigitalSignatures(true);
        when(loggedInInfo.getCurrentFacility()).thenReturn(facility);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);

        bean = new RxSessionBean();
        bean.setProviderNo(PROVIDER_NO);
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        stashItem = new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO);
        // addStashItem preloads interactions/allergies through DAOs; seed the list directly.
        bean.getStashList().add(stashItem);

        DigitalSignature saved = new DigitalSignature();
        ReflectionTestUtils.setField(saved, "id", SIGNATURE_ID);
        when(digitalSignatureManager.saveStampSignature(any(), anyString(), anyInt(), any())).thenReturn(saved);
        // By default the signature links to the prescription successfully.
        when(prescriptionManager.setPrescriptionSignature(any(), anyInt(), any())).thenReturn(true);

        // By default the persisted prescription exists and is unsigned, so a stamp applies.
        when(prescriptionDao.find(anyInt())).thenReturn(unsignedPrescription());

        service = new PrescriptionSignatureStampService(digitalSignatureManager, prescriptionManager, prescriptionDao);
    }

    @AfterEach
    void tearDown() {
        restore("rx_signature_enabled", hadRxSignatureEnabled, originalRxSignatureEnabled);
        restore("rx_fax_enabled", hadRxFaxEnabled, originalRxFaxEnabled);
    }

    private static io.github.carlos_emr.carlos.commn.model.Prescription unsignedPrescription() {
        return unsignedPrescriptionFor(DEMOGRAPHIC_NO);
    }

    private static io.github.carlos_emr.carlos.commn.model.Prescription unsignedPrescriptionFor(Integer demographicId) {
        return unsignedPrescriptionFor(demographicId, PROVIDER_NO);
    }

    /** An unsigned persisted row for {@code demographicId} written by {@code prescriberNo}. */
    private static io.github.carlos_emr.carlos.commn.model.Prescription unsignedPrescriptionFor(Integer demographicId, String prescriberNo) {
        io.github.carlos_emr.carlos.commn.model.Prescription p = new io.github.carlos_emr.carlos.commn.model.Prescription();
        p.setDemographicId(demographicId);
        p.setProviderNo(prescriberNo);
        return p;
    }

    /**
     * A FULLY populated persisted row (patient present, written by the logged-in prescriber) that
     * already carries a signature, so the only guard able to skip it is the already-signed guard.
     * A bare row with only the signature id would also trip the null-patient and null-prescriber
     * guards, and the test could not tell which one fired.
     */
    private static io.github.carlos_emr.carlos.commn.model.Prescription signedPrescription() {
        io.github.carlos_emr.carlos.commn.model.Prescription p = unsignedPrescription();
        p.setDigitalSignatureId(999);
        return p;
    }

    private static void restore(String key, boolean had, Object original) {
        CarlosProperties props = CarlosProperties.getInstance();
        if (had) {
            props.put(key, original);
        } else {
            props.remove(key);
        }
    }

    @Test
    @DisplayName("should persist the stamp and link it to the script and every stash item when eligible")
    void shouldApplyStamp_whenScriptIsNewAndStampExists() {
        Integer applied = service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID);

        assertThat(applied).isEqualTo(SIGNATURE_ID);
        verify(digitalSignatureManager).saveStampSignature(loggedInInfo, PROVIDER_NO, DEMOGRAPHIC_NO, ModuleType.PRESCRIPTION);
        verify(prescriptionManager).setPrescriptionSignature(loggedInInfo, Integer.parseInt(SCRIPT_ID), SIGNATURE_ID);
        assertThat(stashItem.getDigitalSignatureId()).isEqualTo(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should leave a script alone when the persisted row already carries a stored signature")
    void shouldSkipStamp_whenScriptAlreadySigned() {
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(signedPrescription());

        Integer applied = service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID);

        assertThat(applied).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should stamp a freshly written row even when the stash item carries a stale signature id")
    void shouldStamp_whenStashItemHasStaleSignatureButRowIsUnsigned() {
        // The double-save case: a prior save stamped the shared stash item, but the row now shown
        // and faxed is a new, unsigned prescription — it must still be stamped.
        stashItem.setDigitalSignatureId(5);
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescription());

        Integer applied = service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID);

        assertThat(applied).isEqualTo(SIGNATURE_ID);
        verify(prescriptionManager).setPrescriptionSignature(loggedInInfo, Integer.parseInt(SCRIPT_ID), SIGNATURE_ID);
        assertThat(stashItem.getDigitalSignatureId()).isEqualTo(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should skip when the persisted prescription row does not exist")
    void shouldSkipStamp_whenPrescriptionRowMissing() {
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(null);

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should return null without propagating when the persisted-row lookup throws")
    void shouldReturnNull_whenPersistedLookupThrows() {
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID)))
                .thenThrow(new RuntimeException("db down"));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should stamp with the persisted prescription's patient, not the session bean's")
    void shouldStampWithPersistedPatient_whenBeanDemographicDiffers() {
        int persistedPatient = 7777;
        bean.setDemographicNo(DEMOGRAPHIC_NO); // stale/other patient on the bean
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescriptionFor(persistedPatient));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isEqualTo(SIGNATURE_ID);
        verify(digitalSignatureManager).saveStampSignature(loggedInInfo, PROVIDER_NO, persistedPatient, ModuleType.PRESCRIPTION);
    }

    @Test
    @DisplayName("should skip when the persisted prescription has no patient")
    void shouldSkipStamp_whenPersistedPatientMissing() {
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescriptionFor(null));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should not stamp when Rx signatures are disabled in configuration")
    void shouldSkipStamp_whenRxSignatureDisabled() {
        CarlosProperties.getInstance().setProperty("rx_signature_enabled", "false");

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should treat rx_fax_enabled as enabling Rx signatures")
    void shouldApplyStamp_whenOnlyRxFaxEnabled() {
        CarlosProperties.getInstance().setProperty("rx_signature_enabled", "false");
        CarlosProperties.getInstance().setProperty("rx_fax_enabled", "true");

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isEqualTo(SIGNATURE_ID);
    }

    @Test
    @DisplayName("should not stamp when the facility disallows digital signatures")
    void shouldSkipStamp_whenFacilityDigitalSignaturesDisabled() {
        facility.setEnableDigitalSignatures(false);

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should not stamp when the session has no facility")
    void shouldSkipStamp_whenNoFacilityInSession() {
        when(loggedInInfo.getCurrentFacility()).thenReturn(null);

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should not stamp a script whose persisted row was written by another provider")
    void shouldSkipStamp_whenPersistedPrescriberDiffersFromSession() {
        // The re-prescribe case: the session bean's provider IS the logged-in user, but the script
        // number on the stash belongs to a row another provider wrote. The persisted row decides.
        bean.setProviderNo(PROVIDER_NO);
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescriptionFor(DEMOGRAPHIC_NO, "111111"));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
        assertThat(stashItem.getDigitalSignatureId()).isNull();
    }

    @Test
    @DisplayName("should not stamp when the persisted row records no prescriber")
    void shouldSkipStamp_whenPersistedPrescriberMissing() {
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescriptionFor(DEMOGRAPHIC_NO, null));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should decide the prescriber from the persisted row, not the session bean")
    void shouldStampWithPersistedPrescriber_whenBeanProviderDiffers() {
        // A stale bean provider must neither block nor be trusted: the row's provider_no (which is
        // the logged-in user here) is what the stamp is saved under.
        bean.setProviderNo("111111");
        when(prescriptionDao.find(Integer.parseInt(SCRIPT_ID))).thenReturn(unsignedPrescriptionFor(DEMOGRAPHIC_NO, PROVIDER_NO));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isEqualTo(SIGNATURE_ID);
        verify(digitalSignatureManager).saveStampSignature(loggedInInfo, PROVIDER_NO, DEMOGRAPHIC_NO, ModuleType.PRESCRIPTION);
    }

    @Test
    @DisplayName("should not stamp through applyStamp when the given provider is not the logged-in provider")
    void shouldSkipStamp_whenApplyStampProviderDiffersFromSession() {
        assertThat(service.applyStamp(loggedInInfo, "111111", DEMOGRAPHIC_NO, Integer.parseInt(SCRIPT_ID))).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should leave the script unsigned when the provider has no stamp on file")
    void shouldSkipStamp_whenNoStampOnFile() {
        when(digitalSignatureManager.saveStampSignature(any(), anyString(), anyInt(), any())).thenReturn(null);

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        verify(prescriptionManager, never()).setPrescriptionSignature(any(), anyInt(), any());
        assertThat(stashItem.getDigitalSignatureId()).isNull();
    }

    @Test
    @DisplayName("should reject a malformed, zero, or over-int script id without touching the managers")
    void shouldSkipStamp_whenScriptIdMalformed() {
        assertThat(service.applyStampToScript(loggedInInfo, bean, "12; drop")).isNull();
        assertThat(service.applyStampToScript(loggedInInfo, bean, null)).isNull();
        assertThat(service.applyStampToScript(loggedInInfo, bean, "0")).isNull();
        assertThat(service.applyStampToScript(loggedInInfo, bean, "99999999999")).isNull(); // 11 digits
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }

    @Test
    @DisplayName("should accept a ten-digit script id")
    void shouldApplyStamp_whenScriptIdIsTenDigits() {
        // 2000000000 is a valid positive int (< Integer.MAX_VALUE) with ten digits.
        assertThat(service.applyStampToScript(loggedInInfo, bean, "2000000000")).isEqualTo(SIGNATURE_ID);
        verify(prescriptionManager).setPrescriptionSignature(loggedInInfo, 2000000000, SIGNATURE_ID);
    }

    @Test
    @DisplayName("should swallow a persistence failure so the print page still renders")
    void shouldReturnNull_whenPersistenceFails() {
        when(prescriptionManager.setPrescriptionSignature(any(), anyInt(), eq(SIGNATURE_ID)))
                .thenThrow(new IllegalStateException("db down"));

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        assertThat(stashItem.getDigitalSignatureId()).isNull();
    }

    @Test
    @DisplayName("should report failure and not sign the stash when the signature link does not persist")
    void shouldReturnNull_whenLinkNotPersisted() {
        when(prescriptionManager.setPrescriptionSignature(any(), anyInt(), eq(SIGNATURE_ID))).thenReturn(false);

        assertThat(service.applyStampToScript(loggedInInfo, bean, SCRIPT_ID)).isNull();
        assertThat(stashItem.getDigitalSignatureId()).isNull();
    }

    @Test
    @DisplayName("should do nothing for an empty stash")
    void shouldSkipStamp_whenStashEmpty() {
        RxSessionBean empty = new RxSessionBean();
        empty.setProviderNo(PROVIDER_NO);
        empty.setDemographicNo(DEMOGRAPHIC_NO);

        assertThat(service.applyStampToScript(loggedInInfo, empty, SCRIPT_ID)).isNull();
        verifyNoInteractions(digitalSignatureManager, prescriptionManager);
    }
}
