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
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.commn.model.PartialDate;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsTemporalReadService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prescript.data.RxPrescriptionData;
import io.github.carlos_emr.carlos.provider.web.CppPreferencesUIBean;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.mock.web.MockHttpServletRequest;
import java.sql.Date;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The E-Chart Medications panel prints each title as built, so the stored drug text inside the
 * colour span is encoded once there; the drug name is stored raw (#3952).
 *
 * @since 2026-10-08
 */
@DisplayName("EctDisplayRx2Action navbar title")
@Tag("unit")
@Tag("encounter")
@Tag("security")
class EctDisplayRx2ActionTitleUnitTest extends CarlosUnitTestBase {
    @Test void shouldRenderYearPrecisionInTheActualCppPreferencePath_withoutInventingAnEndDate() {
        var security = createAndRegisterMock(SecurityInfoManager.class);
        when(security.hasPrivilege(any(), eq("_rx"), eq("r"), isNull())).thenReturn(true);
        var dates = mock(PartialDateDao.class);
        when(dates.getFormat(PartialDate.DRUGS,7,PartialDate.DRUGS_STARTDATE)).thenReturn(PartialDate.YEARONLY);
        var medication = mock(RxPrescriptionData.Prescription.class);
        when(medication.getDrugId()).thenReturn(7);when(medication.getCdsCurrentness()).thenReturn("CURRENT");
        when(medication.getStartDateUnknown()).thenReturn(true);when(medication.getRxDate()).thenReturn(Date.valueOf("2024-01-01"));
        when(medication.getCustomName()).thenReturn("Imported medication");when(medication.isCurrent()).thenReturn(true);
        when(medication.getFullOutLine()).thenReturn("Imported medication half a tablet nightly");
        var request = new MockHttpServletRequest();request.setContextPath("/carlos");
        var actor = new LoggedInInfo();var provider = new Provider();provider.setProviderNo("999998");actor.setLoggedInProvider(provider);
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(),actor);
        var bean = new EctSessionBean();bean.setDemographicNo("11");bean.providerNo="999998";
        try(var servlet=mockStatic(org.apache.struts2.ServletActionContext.class);
                var prescriptions=mockConstruction(RxPrescriptionData.class,(mock,context) ->
                when(mock.getUniquePrescriptionsByPatient(11)).thenReturn(new RxPrescriptionData.Prescription[]{medication}));
                var preferences=mockConstruction(CppPreferencesUIBean.class,(mock,context) -> {
                    when(mock.getEnable()).thenReturn("on");when(mock.getMedicationStartDate()).thenReturn("on");when(mock.getMedicationEndDate()).thenReturn("on");
                })) {
            servlet.when(org.apache.struts2.ServletActionContext::getRequest).thenReturn(request);
            var action=spy(new EctDisplayRx2Action(new CdsTemporalReadService(dates)));
            doReturn("Medications").when(action).getText(anyString());
            var display=new NavBarDisplayDAO();assertThat(action.getInfo(bean,request,display)).isTrue();
            assertThat(display.getItem(0).getTitle()).contains("Start Date:2024","End Date:Unknown")
                    .doesNotContain("2024-01-01");
        }
    }

    @Test
    @DisplayName("should keep the colour span and show a quoted drug name as stored")
    void shouldWrapStoredName_inColourSpan() {
        assertThat(EctDisplayRx2Action.titleSpan("class=\"currentDrug\"", "CHILDREN'S \"JUNIOR\" SYRUP"))
                .isEqualTo("<span class=\"currentDrug\">CHILDREN'S \"JUNIOR\" SYRUP</span>");
    }

    @Test
    @DisplayName("should render markup in a stored drug name as text")
    void shouldEncodeMarkup_inStoredName() {
        assertThat(EctDisplayRx2Action.titleSpan("class=\"currentDrug\"", "<img src=x onerror=alert(1)> & co"))
                .isEqualTo("<span class=\"currentDrug\">&lt;img src=x onerror=alert(1)&gt; &amp; co</span>");
    }

    @Test
    @DisplayName("should render an absent drug name as an empty span")
    void shouldRenderEmptySpan_forNullName() {
        assertThat(EctDisplayRx2Action.titleSpan("class=\"currentDrug\"", null))
                .isEqualTo("<span class=\"currentDrug\"></span>");
    }
}
