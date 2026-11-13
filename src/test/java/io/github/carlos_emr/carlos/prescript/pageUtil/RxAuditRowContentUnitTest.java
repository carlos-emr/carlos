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

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.dao.DrugReasonDao;
import io.github.carlos_emr.carlos.commn.dao.Icd9Dao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.commn.model.DrugReason;
import io.github.carlos_emr.carlos.commn.model.Icd9;
import io.github.carlos_emr.carlos.log.LogAction;
import io.github.carlos_emr.carlos.log.LogConst;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * What the Rx module writes into the audit log's {@code data} column: ids that let the row be tied
 * to the records it is about, never the drug name, the instructions, a diagnostic code or a free
 * text comment. The audit log is read by privacy officers and administrators who have no need of,
 * and often no right to, the clinical content of the chart.
 *
 * @since 2026-11-12
 */
@Tag("unit")
@Tag("security")
@DisplayName("Rx audit rows carry ids, not clinical text")
class RxAuditRowContentUnitTest {

    private static final int DEMOGRAPHIC_NO = 1001;
    private static final String PATIENT = String.valueOf(DEMOGRAPHIC_NO);
    private static final String PROVIDER_NO = "999998";
    private static final String DRUG_TEXT = "FAKE-DRUG 500mg 1 tab PO BID";

    private MockedStatic<SpringUtils> springUtilsMock;
    private MockedStatic<LogAction> logActionMock;
    private MockedStatic<ServletActionContext> servletActionContextMock;
    private MockedStatic<LoggedInInfo> loggedInInfoMock;
    private final Map<Class<?>, Object> dependencies = new HashMap<>();

    private MockHttpServletRequest request;
    private MockHttpServletResponse response;

    @BeforeEach
    void setUp() {
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        when(loggedInInfo.getLoggedInProviderNo()).thenReturn(PROVIDER_NO);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), nullable(String.class))).thenReturn(true);
        when(securityInfoManager.hasPrivilege(any(), anyString(), anyString(), anyInt())).thenReturn(true);
        when(securityInfoManager.isAllowedAccessToPatientRecord(any(), any())).thenReturn(true);

        springUtilsMock = mockStatic(SpringUtils.class);
        springUtilsMock.when(() -> SpringUtils.getBean(any(Class.class))).thenAnswer(invocation -> {
            Class<?> type = invocation.getArgument(0);
            return type.equals(SecurityInfoManager.class)
                    ? securityInfoManager
                    : dependencies.computeIfAbsent(type, Mockito::mock);
        });
        logActionMock = mockStatic(LogAction.class);

        request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRemoteAddr("192.0.2.7");
        request.setParameter("demographicNo", PATIENT);
        response = new MockHttpServletResponse();
        servletActionContextMock = mockStatic(ServletActionContext.class);
        servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoMock = mockStatic(LoggedInInfo.class);
        loggedInInfoMock.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                .thenReturn(loggedInInfo);

        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(DEMOGRAPHIC_NO);
        bean.setProviderNo(PROVIDER_NO);
        RxSessionBeanResolver.register(request.getSession(), bean);
    }

    @AfterEach
    void tearDown() {
        loggedInInfoMock.close();
        servletActionContextMock.close();
        logActionMock.close();
        springUtilsMock.close();
    }

    @SuppressWarnings("unchecked")
    private <T> T dependency(Class<T> type) {
        return (T) dependencies.computeIfAbsent(type, Mockito::mock);
    }

    /** A stored medication of the open patient whose audit string would be its full outline. */
    private Drug storedDrug(int id) {
        Drug drug = mock(Drug.class);
        when(drug.getId()).thenReturn(id);
        when(drug.getDemographicId()).thenReturn(DEMOGRAPHIC_NO);
        when(drug.getAuditString()).thenReturn(DRUG_TEXT);
        when(dependency(DrugDao.class).find(id)).thenReturn(drug);
        return drug;
    }

    /** No audit call of the seven-argument overload may carry anything that looks like chart text. */
    private void assertNoClinicalTextIsAudited() {
        logActionMock.verify(() -> LogAction.addLog(any(String.class), anyString(), anyString(), anyString(),
                nullable(String.class), nullable(String.class),
                org.mockito.ArgumentMatchers.argThat((String data) -> data != null
                        && (data.contains("FAKE-DRUG") || data.contains("tab PO") || data.contains("Code:")
                        || data.contains("FAKE-COMMENT")))), never());
    }

    @Test
    void shouldAuditDeleteByDrugIdOnly_whenTheDrugListIsDeleted() throws Exception {
        Drug drug = storedDrug(77);
        RxDeleteRx2Action action = new RxDeleteRx2Action();
        action.setDrugList("77");

        action.execute();

        logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.DELETE, LogConst.CON_PRESCRIPTION, "77",
                "192.0.2.7", PATIENT, null));
        Mockito.verify(drug, never()).getAuditString();
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldAuditDeleteByDrugIdOnly_whenOneDrugIsDeletedFromTheProfile() throws Exception {
        Drug drug = storedDrug(77);
        request.setParameter("deleteRxId", "rxRow_77");

        new RxDeleteRx2Action().Delete2();

        logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.DELETE, LogConst.CON_PRESCRIPTION, "77",
                "192.0.2.7", PATIENT, null));
        Mockito.verify(drug, never()).getAuditString();
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldAuditANewReasonByItsMedicationRow_whenADrugReasonIsAdded() throws Exception {
        Drug drug = new Drug();
        drug.setDemographicId(DEMOGRAPHIC_NO);
        when(dependency(DrugDao.class).find(5)).thenReturn(drug);
        when(dependency(Icd9Dao.class).getIcd9Code("250")).thenReturn(List.of(new Icd9()));
        request.setParameter("method", "addDrugReason");
        request.setParameter("drugId", "5");
        request.setParameter("codingSystem", "icd9");
        request.setParameter("jsonDxSearch", "250");
        request.setParameter("comments", "FAKE-COMMENT poorly controlled");

        String result = new RxReason2Action().execute();

        assertThat(result).isEqualTo("close");
        // The diagnostic code (250) and the comment are clinical content: the row names the medication row.
        logActionMock.verify(() -> LogAction.addLog(eq(PROVIDER_NO), eq(LogConst.ADD), eq(LogConst.CON_DRUGREASON),
                anyString(), eq("192.0.2.7"), eq(PATIENT), eq("drugId=5")));
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldAuditAnArchivedReasonByItsMedicationRow_whenADrugReasonIsArchived() throws Exception {
        DrugReason reason = new DrugReason();
        reason.setId(3);
        reason.setDrugId(5);
        reason.setDemographicNo(DEMOGRAPHIC_NO);
        reason.setCodingSystem("icd9");
        reason.setCode("250");
        reason.setComments("FAKE-COMMENT poorly controlled");
        when(dependency(DrugReasonDao.class).find(3)).thenReturn(reason);
        request.setParameter("method", "archiveReason");
        request.setParameter("reasonId", "3");
        request.setParameter("archiveReason", "FAKE-COMMENT entered in error");

        // The action ends by looking up a localized message, which needs a Struts container this
        // unit test does not start; the audit row under test is written before that lookup.
        org.assertj.core.api.Assertions.catchThrowable(() -> new RxReason2Action().execute());

        Mockito.verify(dependency(DrugReasonDao.class)).merge(reason);
        logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.ARCHIVE, LogConst.CON_DRUGREASON, "3",
                "192.0.2.7", PATIENT, "drugId=5"));
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldAuditAReprintByScriptAndDrugIds_whenAScriptIsReprinted() throws Exception {
        RxPrescriptionData.Prescription first = mock(RxPrescriptionData.Prescription.class);
        RxPrescriptionData.Prescription second = mock(RxPrescriptionData.Prescription.class);
        when(first.getDrugId()).thenReturn(501);
        when(second.getDrugId()).thenReturn(502);
        when(first.getAuditString()).thenReturn(DRUG_TEXT);
        when(second.getAuditString()).thenReturn(DRUG_TEXT);
        RxRePrescribe2Action action = new RxRePrescribe2Action();
        action.setDrugList("9");

        String result;
        try (MockedConstruction<RxPrescriptionData> ignored = mockConstruction(RxPrescriptionData.class,
                (mock, context) -> when(mock.getPrescriptionsByScriptNo(9, DEMOGRAPHIC_NO))
                        .thenReturn(List.of(first, second)))) {
            result = action.reprint();
        }

        assertThat(result).isEqualTo("reprint");
        logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.REPRINT, LogConst.CON_PRESCRIPTION, "9",
                "192.0.2.7", PATIENT, "drugIds=501,502"));
        Mockito.verify(first, never()).getAuditString();
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldAuditAReprintByScriptAndDrugIds_whenAScriptIsReprintedFromTheProfile() throws Exception {
        RxPrescriptionData.Prescription only = mock(RxPrescriptionData.Prescription.class);
        when(only.getDrugId()).thenReturn(501);
        when(only.getAuditString()).thenReturn(DRUG_TEXT);
        request.setParameter("scriptNo", "9");

        try (MockedConstruction<RxPrescriptionData> ignored = mockConstruction(RxPrescriptionData.class,
                (mock, context) -> when(mock.getPrescriptionsByScriptNo(9, DEMOGRAPHIC_NO)).thenReturn(List.of(only)))) {
            new RxRePrescribe2Action().reprint2();
        }

        logActionMock.verify(() -> LogAction.addLog(PROVIDER_NO, LogConst.REPRINT, LogConst.CON_PRESCRIPTION, "9",
                "192.0.2.7", PATIENT, "drugIds=501"));
        assertNoClinicalTextIsAudited();
    }

    @Test
    void shouldNotAuditADelete_whenTheDrugBelongsToAnotherPatient() throws Exception {
        Drug foreign = mock(Drug.class);
        when(foreign.getDemographicId()).thenReturn(DEMOGRAPHIC_NO + 1);
        when(dependency(DrugDao.class).find(78)).thenReturn(foreign);
        RxDeleteRx2Action action = new RxDeleteRx2Action();
        action.setDrugList("78");

        action.execute();

        assertThat(response.getStatus()).isEqualTo(403);
        logActionMock.verify(() -> LogAction.addLog(any(String.class), anyString(), anyString(), anyString(),
                nullable(String.class), nullable(String.class), isNull()), never());
    }
}
