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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingONNewReportViewModel;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.login.DBHelp;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

import org.apache.commons.lang3.StringUtils;

/**
 * Builds the {@link BillingONNewReportViewModel} for
 * {@code billingONNewReport.jsp}. Hoists the four inline JDBC queries the
 * legacy JSP body performed (unbilled / billed / paid / unpaid) plus the
 * provider-list and multisite dropdown lookups.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingONNewReportViewModelAssembler {

    /*
     * SQL queries — all parameterized via JDBC '?' placeholders. The IN-clause
     * for the radetail lookup is built from validated billing-no integers, not
     * user input, so embedding them is safe.
     */
    private static final String SQL_UNBILLED =
            "select * from appointment where provider_no=? and appointment_date >=? and appointment_date<=? and (BINARY status NOT LIKE 'B%' AND BINARY status NOT LIKE 'C%' AND BINARY status NOT LIKE 'N%') and demographic_no != 0 order by appointment_date , start_time";
    private static final String SQL_BILLED =
            "select * from billing_on_cheader1 where provider_no=? and billing_date >=? and billing_date<=? and (status<>'D' and status<>'S' and status<>'B') order by billing_date , billing_time";
    private static final String SQL_PAID_BILLINGS =
            "select billing_no,total from billing where provider_no=? and billing_date>=? and billing_date<=? and status ='S' order by billing_date, billing_time";
    private static final String SQL_PAID_RADETAIL =
            "select billing_no, amountclaim, amountpay, hin, service_date from radetail where billing_no in (%s) and raheader_no !=0 order by billing_no, radetail_no";
    private static final String SQL_UNPAID =
            "select * from billing where provider_no=? and billing_date >=? and billing_date<=? and (status<>'D' and status<>'S') order by billing_date , billing_time";

    private final ReportProviderDao reportProviderDao;
    private final SiteDao siteDao;

    public BillingONNewReportViewModelAssembler(ReportProviderDao reportProviderDao, SiteDao siteDao) {
        this.reportProviderDao = reportProviderDao;
        this.siteDao = siteDao;
    }

    public BillingONNewReportViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        boolean multisites = IsPropertiesOn.isMultisitesEnable();
        String currentUser = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        String contextPath = request.getContextPath();

        String providerView = nullToDefault(request.getParameter("providerview"), "all");
        String xmlVdate = nullToEmpty(request.getParameter("xml_vdate"));
        String xmlAppointmentDate = nullToEmpty(request.getParameter("xml_appointment_date"));
        String selectedSite = nullToEmpty(request.getParameter("site"));
        String reportAction = nullToEmpty(request.getParameter("reportAction"));
        String defaultBillForm = CarlosProperties.getInstance().getProperty("default_view", "");

        BillingONNewReportViewModel.Builder builder = BillingONNewReportViewModel.builder()
                .reportAction(reportAction)
                .providerView(providerView)
                .xmlVdate(xmlVdate)
                .xmlAppointmentDate(xmlAppointmentDate)
                .selectedSite(selectedSite)
                .defaultBillForm(defaultBillForm)
                .multisitesEnabled(multisites);

        if (multisites) {
            builder.siteOptions(loadSiteOptions(currentUser));
        } else {
            builder.providerOptions(loadProviderOptions());
        }

        ReportRows rows = runReport(reportAction, providerView, xmlVdate, xmlAppointmentDate,
                multisites, selectedSite, contextPath, defaultBillForm);
        builder.columnHeaders(rows.headers).rows(rows.values).totalRow(rows.totals);

        return builder.build();
    }

    private List<BillingONNewReportViewModel.SiteOption> loadSiteOptions(String currentUser) {
        if (currentUser == null) return Collections.emptyList();
        List<Site> sites = siteDao.getActiveSitesByProviderNo(currentUser);
        Set<String> reporters = new HashSet<>();
        for (Object[] res : reportProviderDao.search_reportprovider("billingreport")) {
            Provider p = (Provider) res[1];
            reporters.add(p.getProviderNo());
        }
        List<BillingONNewReportViewModel.SiteOption> out = new ArrayList<>();
        for (Site site : sites) {
            List<Provider> sortedProviders = new ArrayList<>(site.getProviders());
            sortedProviders.sort(new Provider().ComparatorName());
            List<BillingONNewReportViewModel.SiteProviderEntry> entries = new ArrayList<>();
            for (Provider p : sortedProviders) {
                if (!reporters.contains(p.getProviderNo())) continue;
                entries.add(new BillingONNewReportViewModel.SiteProviderEntry(
                        p.getProviderNo(), p.getLastName() + ", " + p.getFirstName()));
            }
            out.add(new BillingONNewReportViewModel.SiteOption(site.getName(), site.getBgColor(),
                    entries));
        }
        return out;
    }

    private List<BillingONNewReportViewModel.ProviderOption> loadProviderOptions() {
        List<BillingONNewReportViewModel.ProviderOption> out = new ArrayList<>();
        for (Object[] res : reportProviderDao.search_reportprovider("billingreport")) {
            Provider p = (Provider) res[1];
            out.add(new BillingONNewReportViewModel.ProviderOption(
                    p.getProviderNo(), p.getLastName(), p.getFirstName()));
        }
        return out;
    }

    /** Container for the report-table results. */
    private static final class ReportRows {
        List<String> headers = Collections.emptyList();
        List<Properties> values = new ArrayList<>();
        List<String> totals = Collections.emptyList();
    }

    private ReportRows runReport(String action, String providerView, String xmlVdate,
                                  String xmlAppointmentDate, boolean multisites,
                                  String selectedSite, String contextPath,
                                  String defaultBillForm) {
        ReportRows out = new ReportRows();
        if (action.isEmpty() || providerView.equals("all")) return out;

        try {
            switch (action) {
                case "unbilled":
                    runUnbilled(providerView, xmlVdate, xmlAppointmentDate, multisites,
                            selectedSite, defaultBillForm, out);
                    break;
                case "billed":
                    runBilled(providerView, xmlVdate, xmlAppointmentDate, multisites,
                            selectedSite, contextPath, out);
                    break;
                case "paid":
                    runPaid(providerView, xmlVdate, xmlAppointmentDate, contextPath, out);
                    break;
                case "unpaid":
                    runUnpaid(providerView, xmlVdate, xmlAppointmentDate, contextPath, out);
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to assemble billingONNewReport rows", e);
        }
        return out;
    }

    @SuppressWarnings("deprecation")
    private static void runUnbilled(String providerView, String xmlVdate,
                                     String xmlAppointmentDate, boolean multisites,
                                     String selectedSite, String defaultBillForm,
                                     ReportRows out) throws Exception {
        out.headers = Arrays.asList("SERVICE DATE", "TIME", "PATIENT", "DESCRIPTION", "COMMENTS");
        ResultSet rs = DBHelp.searchDBRecord(SQL_UNBILLED, providerView, xmlVdate, xmlAppointmentDate);
        while (rs.next()) {
            if (multisites) {
                String location = rs.getString("location");
                if (StringUtils.isNotBlank(location) && !location.equals(selectedSite)) continue;
            }
            Properties prop = new Properties();
            prop.setProperty("SERVICE DATE", rs.getString("appointment_date"));
            prop.setProperty("TIME", rs.getString("start_time").substring(0, 5));
            prop.setProperty("PATIENT", htmlCell(rs.getString("name")));
            prop.setProperty("DESCRIPTION", htmlCell(rs.getString("reason")));
            prop.setProperty("COMMENTS", buildBillLink(defaultBillForm, providerView, rs));
            out.values.add(prop);
        }
        rs.close();
    }

    private static String buildBillLink(String defaultBillForm, String providerView, ResultSet rs) throws Exception {
        String name = rs.getString("name");
        return "<a href=# onClick='popupPage(700,1000, \"/billing?billForm="
                + URLEncoder.encode(defaultBillForm, StandardCharsets.UTF_8)
                + "&hotclick=&appointment_no=" + urlParam(rs.getString("appointment_no"))
                + "&demographic_name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&demographic_no=" + urlParam(rs.getString("demographic_no"))
                + "&user_no=" + urlParam(rs.getString("provider_no"))
                + "&apptProvider_no=" + urlParam(providerView)
                + "&appointment_date=" + urlParam(rs.getString("appointment_date"))
                + "&start_time=" + urlParam(rs.getString("start_time"))
                + "&bNewForm=1\"); return false;'>Bill ";
    }

    @SuppressWarnings("deprecation")
    private static void runBilled(String providerView, String xmlVdate,
                                   String xmlAppointmentDate, boolean multisites,
                                   String selectedSite, String contextPath,
                                   ReportRows out) throws Exception {
        out.headers = Arrays.asList("SERVICE DATE", "TIME", "PATIENT", "DESCRIPTION", "ACCOUNT");
        ResultSet rs = DBHelp.searchDBRecord(SQL_BILLED, providerView, xmlVdate, xmlAppointmentDate);
        while (rs.next()) {
            if (multisites) {
                String clinic = rs.getString("clinic");
                if (StringUtils.isNotBlank(clinic) && !clinic.equals(selectedSite)) continue;
            }
            Properties prop = new Properties();
            prop.setProperty("SERVICE DATE", rs.getString("billing_date"));
            prop.setProperty("TIME", rs.getString("billing_time").substring(0, 5));
            prop.setProperty("PATIENT", htmlCell(rs.getString("demographic_name")));

            String reason = describeStatus(rs.getString("status"));
            String note = describeApptDoctor(rs.getString("apptProvider_no"),
                    rs.getString("provider_no"));
            prop.setProperty("DESCRIPTION", htmlCell(reason + "(" + note + ")"));
            prop.setProperty("ACCOUNT", buildCorrectionLink(contextPath, rs.getString("id"), reason));
            out.values.add(prop);
        }
        rs.close();
    }

    private static String buildCorrectionLink(String contextPath, String id, String reason) {
        return "<a href=# onClick='popupPage(700,720, \"" + contextPath
                + "/billing/CA/ON/ViewBillingCorrection?billing_no=" + urlParam(id)
                + "&dboperation=search_bill&hotclick=0\"); return false;' title='"
                + SafeEncode.forHtmlAttribute(reason) + "'>" + htmlCell(id) + "</a>";
    }

    @SuppressWarnings("deprecation")
    private static void runPaid(String providerView, String xmlVdate,
                                 String xmlAppointmentDate, String contextPath,
                                 ReportRows out) throws Exception {
        out.headers = Arrays.asList("No", "Billing No", "HIN", "Claim", "Paid", "Billing Date");
        float fTotalClaim = 0.00f;
        float fTotalPaid = 0.00f;

        // Step 1: collect billing_no integers in the date range. Use Integer
        // (parsed) so the IN-clause stays purely numeric — no user input is
        // ever interpolated into the radetail query.
        List<Integer> billingNos = new ArrayList<>();
        Properties propTotal = new Properties();
        ResultSet rs = DBHelp.searchDBRecord(SQL_PAID_BILLINGS, providerView, xmlVdate, xmlAppointmentDate);
        while (rs.next()) {
            int billingNo = rs.getInt("billing_no");
            billingNos.add(billingNo);
            propTotal.setProperty(String.valueOf(billingNo), rs.getString("total"));
        }
        rs.close();

        String billingNoIn = billingNos.isEmpty() ? "-1"
                : billingNos.stream().map(String::valueOf).reduce((a, b) -> a + "," + b).orElse("-1");

        // Step 2: pull ra-detail rows for the captured billing nos.
        rs = DBHelp.searchDBRecord(String.format(SQL_PAID_RADETAIL, billingNoIn));
        String prevBillingNo = "";
        String sAmountclaim = "", sAmountpay = "";
        int nNo = 0;
        while (rs.next()) {
            String billingNo = String.valueOf(rs.getInt("billing_no"));
            if (!prevBillingNo.equals(billingNo)) {
                prevBillingNo = billingNo;
                nNo++;
                sAmountclaim = rs.getString("amountclaim");
                sAmountpay = rs.getString("amountpay");
                Properties prop = new Properties();
                prop.setProperty("No", String.valueOf(nNo));
                prop.setProperty("Billing No", buildBillingNoLink(contextPath, billingNo));
                prop.setProperty("HIN", htmlCell(rs.getString("hin")));
                prop.setProperty("Claim", sAmountclaim);
                prop.setProperty("Paid", sAmountpay);
                prop.setProperty("Billing Date", formatDateStr(rs.getString("service_date")));
                out.values.add(prop);
                fTotalClaim += Float.parseFloat(rs.getString("amountclaim"));
                fTotalPaid += Float.parseFloat(rs.getString("amountpay"));
            } else {
                float fAmountpay = Float.parseFloat(sAmountpay);
                fAmountpay = fAmountpay + Float.parseFloat(rs.getString("amountpay"));
                sAmountpay = String.valueOf(Math.round(fAmountpay * 100) / 100.00);
                Properties prop = new Properties();
                prop.setProperty("No", String.valueOf(nNo));
                prop.setProperty("Billing No", buildBillingNoLink(contextPath, billingNo));
                prop.setProperty("HIN", htmlCell(rs.getString("hin")));
                prop.setProperty("Claim", propTotal.getProperty(prevBillingNo));
                prop.setProperty("Paid", sAmountpay);
                prop.setProperty("Billing Date", formatDateStr(rs.getString("service_date")));
                out.values.remove(out.values.size() - 1);
                out.values.add(prop);
                fTotalClaim += Float.parseFloat(rs.getString("amountclaim"));
                fTotalPaid += Float.parseFloat(rs.getString("amountpay"));
            }
        }
        rs.close();

        out.totals = Arrays.asList("Total", "", "",
                String.valueOf(Math.round(fTotalClaim * 100) / 100.00),
                String.valueOf(Math.round(fTotalPaid * 100) / 100.00),
                "");
    }

    @SuppressWarnings("deprecation")
    private static void runUnpaid(String providerView, String xmlVdate,
                                   String xmlAppointmentDate, String contextPath,
                                   ReportRows out) throws Exception {
        out.headers = Arrays.asList("No", "Billing No", "Patient", "Claim", "Description",
                "Service Date", "Time");
        float fTotalClaim = 0.00f;

        int nNo = 0;
        ResultSet rs = DBHelp.searchDBRecord(SQL_UNPAID, providerView, xmlVdate, xmlAppointmentDate);
        while (rs.next()) {
            Properties prop = new Properties();
            nNo++;
            prop.setProperty("No", String.valueOf(nNo));
            prop.setProperty("Service Date", rs.getString("billing_date"));
            prop.setProperty("Time", rs.getString("billing_time").substring(0, 5));
            prop.setProperty("Patient", htmlCell(rs.getString("demographic_name")));

            String reason = describeStatus(rs.getString("status"));
            String note = describeApptDoctor(rs.getString("apptProvider_no"),
                    rs.getString("provider_no"));
            prop.setProperty("Description", htmlCell(reason + "(" + note + ")"));
            prop.setProperty("Billing No", buildBillingNoLinkWithTitle(contextPath,
                    rs.getString("billing_no"), reason));
            String sAmountclaim = rs.getString("total");
            prop.setProperty("Claim", sAmountclaim);
            fTotalClaim += Float.parseFloat(rs.getString("total"));
            out.values.add(prop);
        }
        rs.close();

        out.totals = Arrays.asList("Total", "", "",
                String.valueOf(Math.round(fTotalClaim * 100) / 100.00),
                "", "", "");
    }

    private static String buildBillingNoLink(String contextPath, String billingNo) {
        return "<a href=# onClick='popupPage(700,720, \"" + contextPath
                + "/billing/CA/ON/ViewBillingOB2?billing_no=" + urlParam(billingNo)
                + "&dboperation=search_bill&hotclick=0\"); return false;' >"
                + htmlCell(billingNo) + "</a>";
    }

    static String buildBillingNoLinkWithTitle(String contextPath, String billingNo,
                                              String reason) {
        return "<a href=# onClick='popupPage(700,720, \"" + contextPath
                + "/billing/CA/ON/ViewBillingOB2?billing_no=" + urlParam(billingNo)
                + "&dboperation=search_bill&hotclick=0\"); return false;' title='"
                + SafeEncode.forHtmlAttribute(reason) + "'>" + htmlCell(billingNo) + "</a>";
    }

    private static String describeStatus(String status) {
        if (status == null) return "";
        return switch (status) {
            case "N" -> "Do Not Bill ";
            case "O" -> "Bill OHIP ";
            case "W" -> "Bill WSIB ";
            case "H" -> "Capitated Bill ";
            case "P" -> "Bill Patient";
            case "B" -> "Sent OHIP";
            default -> status;
        };
    }

    private static String describeApptDoctor(String apptDoctorNo, String userNo) {
        if (apptDoctorNo == null) return "";
        if ("none".equals(apptDoctorNo)) return "No Appt / INR";
        return apptDoctorNo.equals(userNo) ? "With Appt. Doctor" : "Unmatched Appt. Doctor";
    }

    private static String formatDateStr(String str) {
        if (str == null) return "";
        if (str.length() == 8) {
            return str.substring(0, 4) + "/" + str.substring(4, 6) + "/" + str.substring(6);
        }
        return str;
    }

    static String htmlCell(String value) { return SafeEncode.forHtml(value); }
    private static String urlParam(String value) { return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8); }
    private static String nullToEmpty(String v) { return v == null ? "" : v; }
    private static String nullToDefault(String v, String d) { return v == null ? d : v; }
}
