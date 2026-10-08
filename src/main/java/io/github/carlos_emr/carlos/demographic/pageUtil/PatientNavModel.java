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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

import org.apache.commons.lang3.StringUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.integration.patientportal.PatientPortalSettings;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.waitinglist.WaitingList;

/**
 * The patient's navigation down the left of the master record, for any page that shows it
 * ({@code /WEB-INF/jsp/demographic/patient-nav.jsp}). It holds the links' targets and the
 * conditions that are not privileges (billing region, waiting list, portal switch, clinic and
 * user settings); the fragment keeps the record's {@code security:oscarSec} checks for the
 * privileges, so a link shows on every page under the same rules.
 *
 * <p>The targets are the ones the record has always used. Values are URL-encoded here; the
 * fragment encodes the whole target again for its HTML or JavaScript context.
 *
 * <p>Build one per request, after the page has checked that the user may read the patient.
 *
 * @since 2026-10-07
 */
public final class PatientNavModel {

    /** Request attribute the fragment reads. */
    public static final String REQUEST_ATTRIBUTE = "patientNav";

    /** The page showing the navigation: the record itself, or the patient portal page. */
    public enum Page { RECORD, PORTAL }

    /**
     * Everything the links depend on, gathered by {@link #forRequest}; separate so the link
     * rules can be tested without the application's singletons.
     */
    record Inputs(String contextPath, int demographicNo, String lastName, String firstName,
                  String patientProviderNo, String currentProviderNo, String userFirstName, String userLastName,
                  String roleName, String billingRegion, String defaultBillingView, String today,
                  String telephoneNoteReason, String apptProvider, String appointment,
                  boolean waitingListShown, boolean portalSwitchedOn, boolean arFormsShown,
                  boolean documentBrowserShown, Page page) {
    }

    private final Inputs in;

    PatientNavModel(Inputs inputs) {
        this.in = inputs;
    }

    /**
     * Gathers the inputs for the current user and request.
     *
     * @param request the request (its session supplies the user and roles)
     * @param demographic the patient, already checked readable by the calling page
     * @param page the page showing the navigation
     * @return the navigation for this request
     */
    public static PatientNavModel forRequest(HttpServletRequest request, Demographic demographic, Page page) {
        HttpSession session = request.getSession();
        CarlosProperties properties = CarlosProperties.getInstance();
        String currentProviderNo = (String) session.getAttribute("user");
        UserPropertyDAO userProperties = SpringUtils.getBean(UserPropertyDAO.class);
        UserProperty documentBrowser = userProperties.getProp(currentProviderNo, UserProperty.EDOC_BROWSER_IN_MASTER_FILE);
        String noteReason;
        try {
            noteReason = ResourceBundle.getBundle("oscarResources", request.getLocale())
                    .getString("encounter.noteReason.TelProgress");
        } catch (MissingResourceException e) {
            noteReason = "";
        }
        return new PatientNavModel(new Inputs(
                request.getContextPath(),
                demographic.getDemographicNo(),
                demographic.getLastName(),
                demographic.getFirstName(),
                demographic.getProviderNo(),
                currentProviderNo,
                (String) session.getAttribute("userfirstname"),
                (String) session.getAttribute("userlastname"),
                session.getAttribute("userrole") + "," + session.getAttribute("user"),
                StringUtils.trimToEmpty(properties.getProperty("billregion", "")).toUpperCase(Locale.ROOT),
                properties.getProperty("default_view"),
                new SimpleDateFormat("yyyy-MM-dd").format(new Date()),
                noteReason,
                request.getParameter("apptProvider"),
                request.getParameter("appointment"),
                !"true".equals(properties.getProperty("DEMOGRAPHIC_WAITING_LIST"))
                        && WaitingList.getInstance().getFound(),
                PatientPortalSettings.isConfigured(),
                properties.getProperty("clinic_no", "").startsWith("1022"),
                documentBrowser != null && "yes".equals(documentBrowser.getValue()),
                page));
    }

    private static String enc(String value) {
        return URLEncoder.encode(StringUtils.defaultString(value), StandardCharsets.UTF_8);
    }

    private String no() {
        return String.valueOf(in.demographicNo());
    }

    // --- Conditions other than privileges ---

    /** The role string the record's {@code security:oscarSec} checks use. */
    public String getRoleName() {
        return in.roleName();
    }

    public boolean isOntarioBilling() {
        return "ON".equals(in.billingRegion());
    }

    public boolean isWaitingListShown() {
        return in.waitingListShown();
    }

    public boolean isPortalSwitchedOn() {
        return in.portalSwitchedOn();
    }

    public boolean isArFormsShown() {
        return in.arFormsShown();
    }

    public boolean isDocumentBrowserShown() {
        return in.documentBrowserShown();
    }

    public boolean isOnPortalPage() {
        return in.page() == Page.PORTAL;
    }

    // --- Link targets ---

    public String getRecordUrl() {
        return in.contextPath() + "/demographic/DemographicEdit?demographic_no=" + no();
    }

    public String getAppointmentHistoryUrl() {
        return in.contextPath() + "/demographic/DemographicApptHistory?demographic_no=" + no()
                + "&orderby=appttime&dboperation=appt_history&limit1=0&limit2=25";
    }

    public String getWaitingListUrl() {
        return in.contextPath() + "/waitinglist/SetupDisplayPatientWaitingList?demographic_no=" + no();
    }

    public String getBillingHistoryUrl() {
        return in.contextPath() + "/billing/CA/ON/ViewBillingONHistory?demographic_no=" + no();
    }

    public String getInvoiceListUrl() {
        return in.contextPath() + "/billing/CA/BC/reprocessBill?lastName=" + enc(in.lastName())
                + "&firstName=" + enc(in.firstName()) + "&filterPatient=true&demographicNo=" + no();
    }

    public String getEligibilityUrl() {
        return in.contextPath() + "/billing/CA/BC/ManageTeleplan";
    }

    public String getCreateInvoiceUrl() {
        return in.contextPath() + "/billing?billRegion=" + enc(in.billingRegion())
                + "&billForm=" + enc(in.defaultBillingView()) + "&hotclick=&appointment_no=0"
                + "&demographic_name=" + enc(in.lastName()) + "%2C" + enc(in.firstName())
                + "&demographic_no=" + no() + "&providerview=" + enc(in.patientProviderNo())
                + "&user_no=" + enc(in.currentProviderNo()) + "&apptProvider_no=none"
                + "&appointment_date=" + in.today() + "&start_time=00:00:00&bNewForm=1&status=t";
    }

    public String getConsultationsUrl() {
        return in.contextPath() + "/encounter/oscarConsultationRequest/ViewDisplayDemographicConsultationRequests?de="
                + no() + "&proNo=" + enc(in.patientProviderNo());
    }

    public String getPrescriptionsUrl() {
        return in.contextPath() + "/rx/choosePatient?providerNo=" + enc(in.currentProviderNo())
                + "&demographicNo=" + no();
    }

    public String getEchartUrl() {
        String userName = StringUtils.defaultString(in.userFirstName()) + " " + StringUtils.defaultString(in.userLastName());
        return in.contextPath() + "/encounter/IncomingEncounter?providerNo=" + enc(in.currentProviderNo())
                + "&appointmentNo=&demographicNo=" + no() + "&curProviderNo=&reason=" + enc(in.telephoneNoteReason())
                + "&encType=" + enc("telephone encounter with client") + "&userName=" + enc(userName)
                + "&curDate=" + in.today() + "&appointmentDate=&startTime=&status=";
    }

    public String getPreventionsUrl() {
        return in.contextPath() + "/prevention/ViewPreventionIndex?demographic_no=" + no();
    }

    public String getTicklerUrl() {
        return in.contextPath() + "/tickler/ViewTicklerMain?demoview=" + no();
    }

    public String getPortalUrl() {
        return in.contextPath() + "/demographic/portalManage?demographicNo=" + no();
    }

    public String getArFormUrl(String form) {
        return in.contextPath() + "/form/forwardshortcutname?formname=" + enc(form) + "&demographic_no=" + no();
    }

    public String getInboxManagerUrl() {
        return in.contextPath() + "/mod/docmgmtComp/DocList?method=list&&demographic_no=" + no();
    }

    public String getDocumentsUrl() {
        return in.contextPath() + "/documentManager/ViewDocumentReport?function=demographic&doctype=lab&functionid=" + no();
    }

    public String getDocumentBrowserUrl() {
        return in.contextPath() + "/documentManager/ViewDocumentBrowser?function=demographic&doctype=lab&functionid="
                + no() + "&categorykey=" + enc("Private Documents");
    }

    public String getEformsUrl() {
        return in.contextPath() + "/eform/efmpatientformlist?demographic_no=" + no()
                + "&apptProvider=" + enc(in.apptProvider()) + "&appointment=" + enc(in.appointment());
    }

    /** The patient's number, for the BC eligibility check. */
    public int getDemographicNo() {
        return in.demographicNo();
    }
}
