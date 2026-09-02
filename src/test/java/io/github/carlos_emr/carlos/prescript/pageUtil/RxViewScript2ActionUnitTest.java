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

import io.github.carlos_emr.carlos.commn.dao.PrescriptionDao;
import io.github.carlos_emr.carlos.managers.PrescriptionSignatureStampService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RxViewScript2Action}: which stash (live or reprinted) the view is built
 * for, when a stash counts as already persisted, and when the stamp is applied. The real
 * {@code saveScript} path needs patient/provider data and is covered by the Playwright check; here
 * every scenario either skips the save or is asserted on the persistence decision directly.
 */
@DisplayName("RxViewScript2Action persistence and stamp decisions")
@Tag("unit")
@Tag("prescript")
class RxViewScript2ActionUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "999998";
    private static final int DEMOGRAPHIC_NO = 42;

    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private SecurityInfoManager securityInfoManager;
    private PrescriptionSignatureStampService stampService;
    private PrescriptionDao prescriptionDao;
    private LoggedInInfo loggedInInfo;
    private RxSessionBean liveBean;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("GET", "/rx/viewScript");
        response = new MockHttpServletResponse();
        securityInfoManager = mock(SecurityInfoManager.class);
        stampService = mock(PrescriptionSignatureStampService.class);
        prescriptionDao = mock(PrescriptionDao.class);
        loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(PrescriptionSignatureStampService.class, stampService);
        registerMock(PrescriptionDao.class, prescriptionDao);
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), anyString(), isNull())).thenReturn(true);

        liveBean = new RxSessionBean();
        liveBean.setProviderNo(PROVIDER_NO);
        liveBean.setDemographicNo(DEMOGRAPHIC_NO);
        request.getSession().setAttribute("RxSessionBean", liveBean);

        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
    }

    @AfterEach
    void tearDown() {
        servletActionContextMock.close();
        loggedInInfoMock.close();
    }

    /** A stash item as {@code Prescription.Save} leaves it: a drugs row id and its script number. */
    private static RxPrescriptionData.Prescription savedItem(int drugId, String scriptNo) {
        RxPrescriptionData.Prescription rx = new RxPrescriptionData.Prescription(drugId, PROVIDER_NO, DEMOGRAPHIC_NO);
        rx.setScript_no(scriptNo);
        return rx;
    }

    /** A re-prescribed item as {@code newPrescription(.., oldRx)} builds it: no drugs row, old script number. */
    private static RxPrescriptionData.Prescription rePrescribedItem(String oldScriptNo) {
        return savedItem(0, oldScriptNo);
    }

    private RxViewScript2Action newAction() {
        return new RxViewScript2Action(stampService);
    }

    @Test
    @DisplayName("should reject a caller without _rx read before touching the stash")
    void shouldThrow_whenCallerLacksRxRead() {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        assertThatThrownBy(() -> newAction().execute())
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("missing required sec object (_rx)");
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should redirect to the error page when there is no Rx session")
    void shouldRedirect_whenRxSessionMissing() throws Exception {
        request.getSession().removeAttribute("RxSessionBean");

        assertThat(newAction().execute()).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should reuse a fully persisted stash and stamp that script without saving again")
    void shouldReusePersistedScript_whenEveryStashItemIsSaved() throws Exception {
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(savedItem(6, "789"));
        when(stampService.applyStampToScript(loggedInInfo, liveBean, "789")).thenReturn(77);

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isEqualTo(Boolean.TRUE);
        verify(stampService).applyStampToScript(loggedInInfo, liveBean, "789");
        verifyNoInteractions(prescriptionDao); // no second saveScript
    }

    @Test
    @DisplayName("should not stamp when the caller has only _rx read")
    void shouldSkipStamp_whenCallerLacksRxWrite() throws Exception {
        when(securityInfoManager.hasPrivilege(any(), eq("_rx"), eq("w"), isNull())).thenReturn(false);
        liveBean.getStashList().add(savedItem(5, "789"));

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        assertThat(request.getAttribute("scriptId")).isEqualTo("789");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(stampService);
    }

    @Test
    @DisplayName("should neither save nor stamp the live stash while the session is in reprint mode")
    void shouldSkipSaveAndStamp_whenSessionIsInReprintMode() throws Exception {
        // reprint2 leaves the reprinted script in tmpBeanRX and rePrint=true; the live stash still
        // holds an UNSAVED re-prescription carrying the historical script number 123.
        liveBean.getStashList().add(rePrescribedItem("123"));
        RxSessionBean reprinted = new RxSessionBean();
        reprinted.setProviderNo(PROVIDER_NO);
        reprinted.setDemographicNo(DEMOGRAPHIC_NO);
        reprinted.getStashList().add(savedItem(9, "456"));
        request.getSession().setAttribute("tmpBeanRX", reprinted);
        request.getSession().setAttribute("rePrint", "true");

        String result = newAction().execute();

        assertThat(result).isEqualTo("viewScript");
        // The view is built for the REPRINTED script, and script 123 is left exactly as it was.
        assertThat(request.getAttribute("scriptId")).isEqualTo("456");
        assertThat(request.getAttribute(PrescriptionSignatureStampService.RX_STAMP_SIGNATURE_APPLIED)).isNull();
        verifyNoInteractions(stampService, prescriptionDao);
        assertThat(liveBean.getStashItem(0).getDrugId()).isZero();
    }

    @Test
    @DisplayName("should clear a stale reprint marker and bail out when the reprinted stash is missing")
    void shouldRedirectAndClearMarker_whenReprintModeHasNoReprintedStash() throws Exception {
        // rePrint=true without tmpBeanRX: the view would dereference the missing bean, and the
        // live (unsaved) stash must still not be saved or stamped on the way out.
        liveBean.getStashList().add(rePrescribedItem("123"));
        request.getSession().setAttribute("rePrint", "true");

        String result = newAction().execute();

        assertThat(result).isNull();
        assertThat(response.getRedirectedUrl()).isEqualTo("error.html");
        assertThat(request.getSession().getAttribute("rePrint")).isNull();
        assertThat(request.getAttribute("scriptId")).isNull();
        verifyNoInteractions(stampService, prescriptionDao);
    }

    @Test
    @DisplayName("should treat a cleared rePrint flag as a normal view")
    void shouldNotEnterReprintMode_whenFlagCleared() {
        request.getSession().setAttribute("rePrint", null);
        assertThat(RxViewScript2Action.isReprintMode(request.getSession())).isFalse();

        request.getSession().setAttribute("rePrint", "false");
        assertThat(RxViewScript2Action.isReprintMode(request.getSession())).isFalse();

        request.getSession().setAttribute("rePrint", "true");
        assertThat(RxViewScript2Action.isReprintMode(request.getSession())).isTrue();
    }

    @Test
    @DisplayName("should not mistake an unsaved re-prescribed stash for its original script")
    void shouldNotTreatStashAsPersisted_whenItemsCarryOldScriptNoButNoDrugRow() {
        // Every item shares a positive script_no (copied from the re-prescribed script), yet none
        // has a drugs row: this stash must be SAVED, never reused as script 123.
        liveBean.getStashList().add(rePrescribedItem("123"));
        liveBean.getStashList().add(rePrescribedItem("123"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should not treat a stash as persisted when any item is unsaved")
    void shouldNotTreatStashAsPersisted_whenOneItemLacksDrugRow() {
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(rePrescribedItem("789"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should not treat a stash split across scripts as persisted")
    void shouldNotTreatStashAsPersisted_whenItemsSpanScripts() {
        liveBean.getStashList().add(savedItem(5, "789"));
        liveBean.getStashList().add(savedItem(6, "790"));

        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }

    @Test
    @DisplayName("should treat a stash as persisted only for a positive int script number")
    void shouldNotTreatStashAsPersisted_whenScriptNoIsNotPositiveInt() {
        for (String bad : new String[] {null, "", "0", "-1", "abc", "99999999999", "4294967296"}) {
            RxSessionBean bean = new RxSessionBean();
            bean.getStashList().add(savedItem(5, bad));
            assertThat(RxViewScript2Action.persistedScriptId(bean)).as("script_no %s", bad).isNull();
        }
        RxSessionBean tenDigits = new RxSessionBean();
        tenDigits.getStashList().add(savedItem(5, "2000000000"));
        assertThat(RxViewScript2Action.persistedScriptId(tenDigits)).isEqualTo("2000000000");
    }

    @Test
    @DisplayName("should return null for an empty stash")
    void shouldNotTreatStashAsPersisted_whenEmpty() {
        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
        // and a stash item whose fields were never set is unsaved too
        RxPrescriptionData.Prescription blank = new RxPrescriptionData.Prescription(0, PROVIDER_NO, DEMOGRAPHIC_NO);
        ReflectionTestUtils.setField(blank, "script_no", null);
        liveBean.getStashList().add(blank);
        assertThat(RxViewScript2Action.persistedScriptId(liveBean)).isNull();
    }
}
