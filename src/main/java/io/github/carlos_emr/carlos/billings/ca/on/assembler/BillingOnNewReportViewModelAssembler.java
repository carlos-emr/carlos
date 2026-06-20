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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingOnNewReportBilledRow;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingOnNewReportPaidBillingRow;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnNewReportTotalsCalculator;
import io.github.carlos_emr.carlos.commn.dao.projection.BillingOnNewReportPaidRaDetailRow;
import io.github.carlos_emr.carlos.commn.dao.projection.BillingOnNewReportUnbilledRow;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingOnNewReportUnpaidRow;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnNewReportViewModel;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.dao.RaDetailDao;
import io.github.carlos_emr.carlos.commn.dao.ReportProviderDao;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.commons.lang3.StringUtils;

/**
 * Builds the {@link BillingOnNewReportViewModel} for
 * {@code billingONNewReport.jsp}. Delegates report-row data access to DAOs
 * and keeps this class focused on shaping the JSP view model.
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingOnNewReportViewModelAssembler {

    private final ReportProviderDao reportProviderDao;
    private final SiteDao siteDao;
    private final OscarAppointmentDao appointmentDao;
    private final BillingONCHeader1Dao headerDao;
    private final BillingDao billingDao;
    private final RaDetailDao raDetailDao;

    public BillingOnNewReportViewModelAssembler(ReportProviderDao reportProviderDao, SiteDao siteDao,
                                                OscarAppointmentDao appointmentDao,
                                                BillingONCHeader1Dao headerDao,
                                                BillingDao billingDao,
                                                RaDetailDao raDetailDao) {
        this.reportProviderDao = reportProviderDao;
        this.siteDao = siteDao;
        this.appointmentDao = appointmentDao;
        this.headerDao = headerDao;
        this.billingDao = billingDao;
        this.raDetailDao = raDetailDao;
    }

    public BillingOnNewReportViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        boolean multisites = IsPropertiesOn.isMultisitesEnable();
        String currentUser = loggedInInfo == null ? null : loggedInInfo.getLoggedInProviderNo();
        String contextPath = request.getContextPath();

        String providerView = nullToDefault(request.getParameter("providerview"), "all");
        String xmlVdate = nullToEmpty(request.getParameter("xml_vdate"));
        String xmlAppointmentDate = nullToEmpty(request.getParameter("xml_appointment_date"));
        String selectedSite = nullToEmpty(request.getParameter("site"));
        String reportAction = nullToEmpty(request.getParameter("reportAction"));
        String defaultBillForm = CarlosProperties.getInstance().getProperty("default_view", "");

        BillingOnNewReportViewModel.Builder builder = BillingOnNewReportViewModel.builder()
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

    private List<BillingOnNewReportViewModel.SiteOption> loadSiteOptions(String currentUser) {
        if (currentUser == null) return Collections.emptyList();
        List<Site> sites = siteDao.getActiveSitesByProviderNo(currentUser);
        Set<String> reporters = new HashSet<>();
        for (io.github.carlos_emr.carlos.commn.dao.projection.ReporterRow row :
                reportProviderDao.search_reportprovider("billingreport")) {
            reporters.add(row.providerNo());
        }
        List<BillingOnNewReportViewModel.SiteOption> out = new ArrayList<>();
        for (Site site : sites) {
            List<Provider> sortedProviders = new ArrayList<>(site.getProviders());
            sortedProviders.sort(new Provider().ComparatorName());
            List<BillingOnNewReportViewModel.SiteProviderEntry> entries = new ArrayList<>();
            for (Provider p : sortedProviders) {
                if (!reporters.contains(p.getProviderNo())) continue;
                entries.add(new BillingOnNewReportViewModel.SiteProviderEntry(
                        p.getProviderNo(), p.getLastName() + ", " + p.getFirstName()));
            }
            out.add(new BillingOnNewReportViewModel.SiteOption(site.getName(), site.getBgColor(),
                    entries));
        }
        return out;
    }

    private List<BillingOnNewReportViewModel.ProviderOption> loadProviderOptions() {
        List<BillingOnNewReportViewModel.ProviderOption> out = new ArrayList<>();
        for (io.github.carlos_emr.carlos.commn.dao.projection.ReporterRow row :
                reportProviderDao.search_reportprovider("billingreport")) {
            out.add(new BillingOnNewReportViewModel.ProviderOption(
                    row.providerNo(), row.lastName(), row.firstName()));
        }
        return out;
    }

    /** Container for the report-table results. */
    private static final class ReportRows {
        List<String> headers = Collections.emptyList();
        List<BillingOnNewReportViewModel.ReportRow> values = new ArrayList<>();
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
                    // Unrecognized report action — render empty rows but log
                    // so a typo or future report-type the switch hasn't
                    // caught up with is distinguishable from "no matches".
                    MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                            "BillingOnNewReportViewModelAssembler: unknown action [{}]; rendering empty result",
                            LogSafe.sanitize(action));
                    break;
            }
        } catch (RuntimeException e) {
            if (e instanceof BillingDataLoadException) {
                throw e;
            }
            throw new BillingDataLoadException(
                    "Failed to assemble billingONNewReport rows",
                    e,
                    BillingDataLoadException.Phase.DAO_QUERY,
                    Map.of(
                            "reportAction", action,
                            "providerview", providerView,
                            "xml_vdate", xmlVdate,
                            "xml_appointment_date", xmlAppointmentDate,
                            "site", selectedSite));
        }
        return out;
    }

    private void runUnbilled(String providerView, String xmlVdate,
                             String xmlAppointmentDate, boolean multisites,
                             String selectedSite, String defaultBillForm,
                             ReportRows out) {
        out.headers = Arrays.asList("SERVICE DATE", "TIME", "PATIENT", "DESCRIPTION", "COMMENTS");
        for (BillingOnNewReportUnbilledRow row :
                appointmentDao.findBillingOnNewReportUnbilledRows(providerView, xmlVdate, xmlAppointmentDate)) {
            if (multisites) {
                String location = row.location();
                if (StringUtils.isNotBlank(location) && !location.equals(selectedSite)) continue;
            }
            Map<String, BillingOnNewReportViewModel.ReportCell> cells = new LinkedHashMap<>();
            cells.put("SERVICE DATE", textCell(row.appointmentDate()));
            cells.put("TIME", textCell(firstFive(row.startTime())));
            cells.put("PATIENT", textCell(row.name()));
            cells.put("DESCRIPTION", textCell(row.reason()));
            cells.put("COMMENTS", buildBillLink(defaultBillForm, providerView, row));
            out.values.add(BillingOnNewReportViewModel.rowOf(cells));
        }
    }

    private static BillingOnNewReportViewModel.ReportCell buildBillLink(
            String defaultBillForm, String providerView, BillingOnNewReportUnbilledRow row) {
        String name = row.name();
        return BillingOnNewReportViewModel.ReportCell.popup(
                "Bill",
                "/billing?billForm="
                + URLEncoder.encode(defaultBillForm, StandardCharsets.UTF_8)
                + "&hotclick=&appointment_no=" + urlParam(row.appointmentNo())
                + "&demographic_name=" + URLEncoder.encode(name, StandardCharsets.UTF_8)
                + "&demographic_no=" + urlParam(row.demographicNo())
                + "&user_no=" + urlParam(row.providerNo())
                + "&apptProvider_no=" + urlParam(providerView)
                + "&appointment_date=" + urlParam(row.appointmentDate())
                + "&start_time=" + urlParam(row.startTime())
                + "&bNewForm=1",
                700,
                1000,
                "");
    }

    private void runBilled(String providerView, String xmlVdate,
                           String xmlAppointmentDate, boolean multisites,
                           String selectedSite, String contextPath,
                           ReportRows out) {
        out.headers = Arrays.asList("SERVICE DATE", "TIME", "PATIENT", "DESCRIPTION", "ACCOUNT");
        for (BillingOnNewReportBilledRow row :
                headerDao.findBillingOnNewReportBilledRows(providerView, xmlVdate, xmlAppointmentDate)) {
            if (multisites) {
                String clinic = row.clinic();
                if (StringUtils.isNotBlank(clinic) && !clinic.equals(selectedSite)) continue;
            }
            Map<String, BillingOnNewReportViewModel.ReportCell> cells = new LinkedHashMap<>();
            cells.put("SERVICE DATE", textCell(row.billingDate()));
            cells.put("TIME", textCell(firstFive(row.billingTime())));
            cells.put("PATIENT", textCell(row.demographicName()));

            String reason = describeStatus(row.status());
            String note = describeApptDoctor(row.apptProviderNo(), row.providerNo());
            cells.put("DESCRIPTION", textCell(reason + "(" + note + ")"));
            cells.put("ACCOUNT", buildCorrectionLink(contextPath, row.id(), reason));
            out.values.add(BillingOnNewReportViewModel.rowOf(cells));
        }
    }

    private static BillingOnNewReportViewModel.ReportCell buildCorrectionLink(
            String contextPath, String id, String reason) {
        return BillingOnNewReportViewModel.ReportCell.popup(
                id,
                contextPath
                + "/billing/CA/ON/ViewBillingCorrection?billing_no=" + urlParam(id)
                + "&dboperation=search_bill&hotclick=0",
                700,
                720,
                reason);
    }

    private void runPaid(String providerView, String xmlVdate,
                         String xmlAppointmentDate, String contextPath,
                         ReportRows out) {
        out.headers = Arrays.asList("No", "Billing No", "HIN", "Claim", "Paid", "Billing Date");
        BillingOnNewReportTotalsCalculator totalsCalculator = new BillingOnNewReportTotalsCalculator();

        List<Integer> billingNos = new ArrayList<>();
        Map<String, String> propTotal = new LinkedHashMap<>();
        for (BillingOnNewReportPaidBillingRow row :
                billingDao.findBillingOnNewReportPaidBillings(providerView, xmlVdate, xmlAppointmentDate)) {
            int billingNo = Integer.parseInt(row.billingNo());
            billingNos.add(billingNo);
            propTotal.put(row.billingNo(), row.total());
        }

        String prevBillingNo = "";
        String sAmountclaim = "", sAmountpay = "";
        int nNo = 0;
        for (BillingOnNewReportPaidRaDetailRow row :
                raDetailDao.findBillingOnNewReportPaidRaDetails(billingNos)) {
            String billingNo = row.billingNo();
            if (!prevBillingNo.equals(billingNo)) {
                prevBillingNo = billingNo;
                nNo++;
                sAmountclaim = row.amountClaim();
                sAmountpay = row.amountPay();
                Map<String, BillingOnNewReportViewModel.ReportCell> cells = new LinkedHashMap<>();
                cells.put("No", textCell(String.valueOf(nNo)));
                cells.put("Billing No", buildBillingNoLink(contextPath, billingNo));
                cells.put("HIN", textCell(row.hin()));
                cells.put("Claim", textCell(sAmountclaim));
                cells.put("Paid", textCell(sAmountpay));
                cells.put("Billing Date", textCell(formatDateStr(row.serviceDate())));
                out.values.add(BillingOnNewReportViewModel.rowOf(cells));
                totalsCalculator.addPaidRow(row.amountClaim(), row.amountPay());
            } else {
                sAmountpay = totalsCalculator.addPaidAmount(sAmountpay, row.amountPay());
                Map<String, BillingOnNewReportViewModel.ReportCell> cells = new LinkedHashMap<>();
                cells.put("No", textCell(String.valueOf(nNo)));
                cells.put("Billing No", buildBillingNoLink(contextPath, billingNo));
                cells.put("HIN", textCell(row.hin()));
                cells.put("Claim", textCell(propTotal.get(prevBillingNo)));
                cells.put("Paid", textCell(sAmountpay));
                cells.put("Billing Date", textCell(formatDateStr(row.serviceDate())));
                out.values.remove(out.values.size() - 1);
                out.values.add(BillingOnNewReportViewModel.rowOf(cells));
                totalsCalculator.addPaidRow(row.amountClaim(), row.amountPay());
            }
        }

        out.totals = totalsCalculator.paidTotalRow();
    }

    private void runUnpaid(String providerView, String xmlVdate,
                           String xmlAppointmentDate, String contextPath,
                           ReportRows out) {
        out.headers = Arrays.asList("No", "Billing No", "Patient", "Claim", "Description",
                "Service Date", "Time");
        BillingOnNewReportTotalsCalculator totalsCalculator = new BillingOnNewReportTotalsCalculator();

        int nNo = 0;
        for (BillingOnNewReportUnpaidRow row :
                billingDao.findBillingOnNewReportUnpaidRows(providerView, xmlVdate, xmlAppointmentDate)) {
            nNo++;
            Map<String, BillingOnNewReportViewModel.ReportCell> cells = new LinkedHashMap<>();
            cells.put("No", textCell(String.valueOf(nNo)));
            cells.put("Service Date", textCell(row.billingDate()));
            cells.put("Time", textCell(firstFive(row.billingTime())));
            cells.put("Patient", textCell(row.demographicName()));

            String reason = describeStatus(row.status());
            String note = describeApptDoctor(row.apptProviderNo(), row.providerNo());
            cells.put("Description", textCell(reason + "(" + note + ")"));
            cells.put("Billing No", buildBillingNoLinkWithTitle(contextPath, row.billingNo(), reason));
            String sAmountclaim = row.total();
            cells.put("Claim", textCell(sAmountclaim));
            totalsCalculator.addUnpaidClaim(row.total());
            out.values.add(BillingOnNewReportViewModel.rowOf(cells));
        }

        out.totals = totalsCalculator.unpaidTotalRow();
    }

    private static BillingOnNewReportViewModel.ReportCell buildBillingNoLink(
            String contextPath, String billingNo) {
        return BillingOnNewReportViewModel.ReportCell.popup(
                billingNo,
                contextPath
                + "/billing/CA/ON/ViewBillingOB2?billing_no=" + urlParam(billingNo)
                + "&dboperation=search_bill&hotclick=0",
                700,
                720,
                "");
    }

    static BillingOnNewReportViewModel.ReportCell buildBillingNoLinkWithTitle(
            String contextPath, String billingNo, String reason) {
        return BillingOnNewReportViewModel.ReportCell.popup(
                billingNo,
                contextPath
                + "/billing/CA/ON/ViewBillingOB2?billing_no=" + urlParam(billingNo)
                + "&dboperation=search_bill&hotclick=0",
                700,
                720,
                reason);
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

    static BillingOnNewReportViewModel.ReportCell textCell(String value) {
        return BillingOnNewReportViewModel.ReportCell.text(value);
    }
    private static String urlParam(String value) { return URLEncoder.encode(nullToEmpty(value), StandardCharsets.UTF_8); }
    private static String firstFive(String value) {
        String v = nullToEmpty(value);
        return v.length() <= 5 ? v : v.substring(0, 5);
    }
    private static String nullToEmpty(String v) { return v == null ? "" : v; }
    private static String nullToDefault(String v, String d) { return v == null ? d : v; }
}
