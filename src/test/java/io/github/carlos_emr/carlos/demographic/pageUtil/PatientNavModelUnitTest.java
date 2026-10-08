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
package io.github.carlos_emr.carlos.demographic.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The master record's navigation targets, which the record and the patient portal page share.
 * Each expected target is the one the record built inline before the navigation was shared.
 */
@Tag("unit")
@Tag("fast")
@DisplayName("Patient navigation targets and conditions")
class PatientNavModelUnitTest {

    private static PatientNavModel.Inputs inputs(String billingRegion, String lastName, String firstName,
            PatientNavModel.Page page) {
        return new PatientNavModel.Inputs("/ctx", 123, lastName, firstName, "999998", "100001",
                "FAKE Jo", "FAKE-Provider", "doctor,100001", billingRegion, "MFP", "2026-10-07",
                "Tel-Progress Note", "100002", "55", true, true, false, false, page);
    }

    private static PatientNavModel model() {
        return new PatientNavModel(inputs("ON", "FAKE-Jones", "FAKE-Jacky", PatientNavModel.Page.RECORD));
    }

    @Test
    @DisplayName("should build the record's same-window targets")
    void shouldBuildSameWindowTargets_asTheRecordDid() {
        PatientNavModel nav = model();
        assertThat(nav.getRecordUrl()).isEqualTo("/ctx/demographic/DemographicEdit?demographic_no=123");
        assertThat(nav.getAppointmentHistoryUrl()).isEqualTo(
                "/ctx/demographic/DemographicApptHistory?demographic_no=123&orderby=appttime&dboperation=appt_history&limit1=0&limit2=25");
        assertThat(nav.getWaitingListUrl()).isEqualTo("/ctx/waitinglist/SetupDisplayPatientWaitingList?demographic_no=123");
        assertThat(nav.getPortalUrl()).isEqualTo("/ctx/demographic/portalManage?demographicNo=123");
        assertThat(nav.getEformsUrl()).isEqualTo("/ctx/eform/efmpatientformlist?demographic_no=123&apptProvider=100002&appointment=55");
    }

    @Test
    @DisplayName("should build the record's popup targets")
    void shouldBuildPopupTargets_asTheRecordDid() {
        PatientNavModel nav = model();
        assertThat(nav.getBillingHistoryUrl()).isEqualTo("/ctx/billing/CA/ON/ViewBillingONHistory?demographic_no=123");
        assertThat(nav.getConsultationsUrl()).isEqualTo(
                "/ctx/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de=123&proNo=999998");
        assertThat(nav.getPrescriptionsUrl()).isEqualTo("/ctx/rx/choosePatient?providerNo=100001&demographicNo=123");
        assertThat(nav.getPreventionsUrl()).isEqualTo("/ctx/prevention/ViewPreventionIndex?demographic_no=123");
        assertThat(nav.getTicklerUrl()).isEqualTo("/ctx/tickler/ViewTicklerMain?demoview=123");
        assertThat(nav.getArFormUrl("AR1")).isEqualTo("/ctx/form/forwardshortcutname?formname=AR1&demographic_no=123");
        assertThat(nav.getInboxManagerUrl()).isEqualTo("/ctx/mod/docmgmtComp/DocList?method=list&&demographic_no=123");
        assertThat(nav.getDocumentsUrl()).isEqualTo(
                "/ctx/documentManager/ViewDocumentReport?function=demographic&doctype=lab&functionid=123");
        assertThat(nav.getDocumentBrowserUrl()).isEqualTo(
                "/ctx/documentManager/ViewDocumentBrowser?function=demographic&doctype=lab&functionid=123&categorykey=Private+Documents");
    }

    @Test
    @DisplayName("should build the eChart target the record's encURL used, with encoded text")
    void shouldBuildEchartTarget_withEncodedReasonAndUserName() {
        assertThat(model().getEchartUrl()).isEqualTo("/ctx/encounter/IncomingEncounter?providerNo=100001"
                + "&appointmentNo=&demographicNo=123&curProviderNo=&reason=Tel-Progress+Note"
                + "&encType=telephone+encounter+with+client&userName=FAKE+Jo+FAKE-Provider&curDate=2026-10-07"
                + "&appointmentDate=&startTime=&status=");
    }

    @Test
    @DisplayName("should encode the patient's name and the billing values in the invoice targets")
    void shouldEncodeNamesAndBillingValues_inInvoiceTargets() {
        PatientNavModel nav = new PatientNavModel(inputs("BC", "FAKE O'Neil & Co", "FAKE Ann-Marie", PatientNavModel.Page.RECORD));
        assertThat(nav.isOntarioBilling()).isFalse();
        assertThat(nav.getInvoiceListUrl()).isEqualTo("/ctx/billing/CA/BC/reprocessBill?lastName=FAKE+O%27Neil+%26+Co"
                + "&firstName=FAKE+Ann-Marie&filterPatient=true&demographicNo=123");
        assertThat(nav.getCreateInvoiceUrl()).isEqualTo("/ctx/billing?billRegion=BC&billForm=MFP&hotclick=&appointment_no=0"
                + "&demographic_name=FAKE+O%27Neil+%26+Co%2CFAKE+Ann-Marie&demographic_no=123&providerview=999998"
                + "&user_no=100001&apptProvider_no=none&appointment_date=2026-10-07&start_time=00:00:00&bNewForm=1&status=t");
        assertThat(nav.getEligibilityUrl()).isEqualTo("/ctx/billing/CA/BC/ManageTeleplan");
    }

    @Test
    @DisplayName("should leave missing optional values empty rather than writing null")
    void shouldLeaveMissingValuesEmpty_inTargets() {
        PatientNavModel nav = new PatientNavModel(new PatientNavModel.Inputs("", 7, null, null, null, "100001",
                null, null, "doctor,100001", "ON", null, "2026-10-07", "", null, null,
                false, false, false, false, PatientNavModel.Page.RECORD));
        assertThat(nav.getEformsUrl()).isEqualTo("/eform/efmpatientformlist?demographic_no=7&apptProvider=&appointment=");
        assertThat(nav.getCreateInvoiceUrl()).contains("billForm=&").contains("demographic_name=%2C&").contains("providerview=&");
        assertThat(nav.getEchartUrl()).contains("&userName=+&");
    }

    @Test
    @DisplayName("should expose the conditions the fragment checks, and which page it is on")
    void shouldExposeConditions_andThePage() {
        PatientNavModel record = model();
        PatientNavModel portal = new PatientNavModel(inputs("ON", "FAKE-Jones", "FAKE-Jacky", PatientNavModel.Page.PORTAL));
        assertThat(record.getRoleName()).isEqualTo("doctor,100001");
        assertThat(record.isOntarioBilling()).isTrue();
        assertThat(record.isWaitingListShown()).isTrue();
        assertThat(record.isPortalSwitchedOn()).isTrue();
        assertThat(record.isArFormsShown()).isFalse();
        assertThat(record.isDocumentBrowserShown()).isFalse();
        assertThat(record.isOnPortalPage()).isFalse();
        assertThat(portal.isOnPortalPage()).isTrue();
        assertThat(record.getDemographicNo()).isEqualTo(123);
    }
}
