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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsMedicationCompatibilityException;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.prescript.gate.RxRequestedPatientAccess;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxRePrescribe2Action;
import io.github.carlos_emr.carlos.prescript.pageUtil.RxSessionBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class CdsMedicationRenewalActionUnitTest {
    @ParameterizedTest
    @ValueSource(strings = {"represcribe", "represcribeMultiple", "repcbAllLongTerm",
            "saveReRxDrugIdToStash", "represcribe2"})
    void shouldRefuseClinicalUncertaintyInAllForms_withoutChangingExistingDraft(String operation) throws Exception {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setParameter("demographicNo", "1");
        request.setParameter("demoNo", "1");
        request.setParameter("drugIds", "6,7");
        request.setParameter("drugId", "7");
        request.setParameter("showall", "true");
        RxSessionBean bean = new RxSessionBean();
        bean.setDemographicNo(1);
        bean.setProviderNo("999998");
        var draft = new RxPrescriptionData.Prescription(0, "999998", 1);
        bean.getStashList().add(draft);
        bean.addReRxDrugIdList("99");
        var nativeSource = new RxPrescriptionData.Prescription(6, "999998", 1);
        var importedSource = new RxPrescriptionData.Prescription(7, "", 1);
        importedSource.setCdsCurrentness("UNKNOWN");
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), anyString(), anyString(), isNull())).thenReturn(true);
        CaseManagementManager cases = mock(CaseManagementManager.class);
        Drug nativeRow = new Drug(); nativeRow.setId(6); nativeRow.setLongTerm(true);
        Drug importedRow = new Drug(); importedRow.setId(7); importedRow.setLongTerm(true);
        when(cases.getPrescriptions(any(), eq(1), eq(true))).thenReturn(List.of(nativeRow, importedRow));
        try (var spring = mockStatic(SpringUtils.class);
             var servlet = mockStatic(ServletActionContext.class);
             var login = mockStatic(LoggedInInfo.class);
             var access = mockStatic(RxRequestedPatientAccess.class);
             var data = mockConstruction(RxPrescriptionData.class, (mock, context) -> {
                 when(mock.getPrescription(6)).thenReturn(nativeSource);
                 when(mock.getPrescription(7)).thenReturn(importedSource);
                 when(mock.getPrescriptionIfPresent(6)).thenReturn(nativeSource);
                 when(mock.getPrescriptionIfPresent(7)).thenReturn(importedSource);
                 when(mock.newPrescription(anyString(), anyInt(), any(RxPrescriptionData.Prescription.class)))
                         .thenCallRealMethod();
             })) {
            spring.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(security);
            spring.when(() -> SpringUtils.getBean(CaseManagementManager.class)).thenReturn(cases);
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(new LoggedInInfo());
            access.when(() -> RxRequestedPatientAccess.resolveForWrite(security, request, "_rx", "w"))
                    .thenReturn(bean);
            var action = new RxRePrescribe2Action();
            action.setDrugList("6,7");
            String outcome = switch (operation) {
                case "represcribe" -> action.represcribe();
                case "represcribeMultiple" -> action.represcribeMultiple();
                case "repcbAllLongTerm" -> action.repcbAllLongTerm();
                case "represcribe2" -> action.represcribe2();
                default -> action.saveReRxDrugIdToStash();
            };
            assertEquals(ActionSupport.NONE, outcome);
            assertEquals(409, response.getStatus());
            assertTrue(response.getErrorMessage().contains(CdsMedicationCompatibilityException.RENEWAL_CODE));
            assertEquals(List.of(draft), bean.getStashList());
            assertEquals(List.of("99"), bean.getReRxDrugIdList());
            assertNull(request.getAttribute("listRxDrugs"));
            if (operation.equals("represcribe") || operation.equals("represcribeMultiple")
                    || operation.equals("repcbAllLongTerm")) {
                verify(data.constructed().get(0), never())
                        .newPrescription(anyString(), anyInt(), any(RxPrescriptionData.Prescription.class));
            }
        }
    }
}
