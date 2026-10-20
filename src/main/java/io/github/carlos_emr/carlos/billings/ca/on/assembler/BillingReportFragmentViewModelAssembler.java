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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReportFragmentViewModel;
import io.github.carlos_emr.carlos.billings.ca.report.UnbilledReportStatusParameters;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONItemDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Assembles {@link BillingReportFragmentViewModel} for the four ON billing
 * report JSPF fragments (billed / unsettled / billob / flu). Owns the inline
 * DAO calls and date-range / status / row-color logic the fragments used to
 * compute in scriptlet bodies.
 *
 * <p>Pure read: privilege gating is performed by the parent JSP's
 * {@code ViewBillingReportControl2Action}; this assembler runs after the
 * gate to populate the row lists. It chooses which row list(s) to populate
 * based on {@code reportAction}, leaving the others as empty lists so the
 * single view-model object covers all four templates.</p>
 *
 * <p>Billed, Unsettled and OB list Ontario claims ({@code billing_on_cheader1} and its items), where
 * the Ontario bill form stores them. They used to read the retired {@code billing} table, so they
 * listed only old rows carried over from OSCAR (#5061). Flu still reads it: how its walk-in / clinic
 * split maps to Ontario claims is an open question on #5061.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingReportFragmentViewModelAssembler {

    /** The obstetric service codes the OB report lists, as it always has. */
    static final List<String> OB_SERVICE_CODES = List.of("P006A", "P011A", "P009A", "P020A", "P022A", "P028A",
            "P023A", "P007A", "P008B", "P018B", "E502A", "C989A", "E409A", "E410A", "E411A", "H001A");

    private final BillingDao billingDao;
    private final BillingONCHeader1Dao claimDao;
    private final BillingONItemDao claimItemDao;
    private final io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao appointmentDao;

    public BillingReportFragmentViewModelAssembler(BillingDao billingDao, BillingONCHeader1Dao claimDao,
                                        BillingONItemDao claimItemDao,
                                        io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao appointmentDao) {
        this.billingDao = billingDao;
        this.claimDao = claimDao;
        this.claimItemDao = claimItemDao;
        this.appointmentDao = appointmentDao;
    }

    /**
     * Build the fragment view model. {@code reportAction} selects which
     * fragment to populate; the others default to empty lists.
     *
     * @param request live request — supplies {@code xml_vdate},
     *                {@code xml_appointment_date}, {@code providerview}, and
     *                for "unbilled" the {@code includeNoShow} /
     *                {@code includeCancelled} checkboxes
     * @param reportAction one of "unbilled" / "billed" / "unsettled" / "billob" / "flu"
     * @return populated view model
     */
    public BillingReportFragmentViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo, String reportAction) {
        BillingReportFragmentViewModel.Builder b = BillingReportFragmentViewModel.builder();
        if (reportAction == null) return b.build();

        String providerView = request.getParameter("providerview");
        // Date defaults match the legacy scriptlet contract: end → today,
        // begin → "1950-01-01" for billed/unsettled/billob and "2001-01-01"
        // for flu (per the original script).
        String dateBeginStr = request.getParameter("xml_vdate");
        String dateEndStr = request.getParameter("xml_appointment_date");

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = (now.get(Calendar.MONTH) + 1);
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        if (dateEndStr == null || dateEndStr.isEmpty()) {
            dateEndStr = MyDateFormat.getMysqlStandardDate(curYear, curMonth, curDay);
        }
        if (dateBeginStr == null || dateBeginStr.isEmpty()) {
            dateBeginStr = "flu".equals(reportAction) ? "2001-01-01" : "1950-01-01";
        }
        Date dateBegin = ConversionUtils.fromDateString(dateBeginStr);
        Date dateEnd = ConversionUtils.fromDateString(dateEndStr);

        switch (reportAction) {
            case "billed":
                b.billedRows(loadBilledRows(providerView, dateBegin, dateEnd));
                break;
            case "unsettled":
                b.unsettledRows(loadUnsettledRows(providerView, dateBegin, dateEnd));
                break;
            case "billob":
                loadBillob(providerView, dateBegin, dateEnd, b);
                break;
            case "flu":
                loadFlu(providerView, dateBegin, dateEnd, b);
                break;
            case "unbilled":
                b.unbilledRows(loadUnbilledRows(providerView, dateBegin, dateEnd, request));
                break;
            default:
                // unknown reportAction → empty model; parent JSP renders nothing
                break;
        }
        return b.build();
    }

    private List<BillingReportFragmentViewModel.UnbilledRow> loadUnbilledRows(
            String providerView, Date dateBegin, Date dateEnd, HttpServletRequest request) {
        // No-Show and Cancelled visits are excluded unless the user opts in, so
        // this screen agrees with the ON new-report unbilled list (issue #3960).
        List<io.github.carlos_emr.carlos.commn.model.Appointment> bs =
                appointmentDao.findUnbilledAppointments(providerView, dateBegin, dateEnd,
                        UnbilledReportStatusParameters.includeNoShow(request),
                        UnbilledReportStatusParameters.includeCancelled(request));
        List<BillingReportFragmentViewModel.UnbilledRow> rows = new ArrayList<>();
        if (bs == null) return rows;
        String defaultView = io.github.carlos_emr.CarlosProperties.getInstance()
                .getProperty("default_view", "");
        boolean bodd = false;
        for (io.github.carlos_emr.carlos.commn.model.Appointment a : bs) {
            bodd = !bodd;
            String apptNo = a.getId() == null ? "" : a.getId().toString();
            String demoNo = String.valueOf(a.getDemographicNo());
            String demoName = nullToEmpty(a.getName());
            String userNo = nullToEmpty(a.getProviderNo());
            String apptDate = ConversionUtils.toDateString(a.getAppointmentDate());
            String apptTime = ConversionUtils.toTimeString(a.getStartTime());
            String reason = nullToEmpty(a.getReason());

            // The .jspf hands popupUrl straight to popupPage() without adding the
            // context path, so it has to be baked in here — CARLOS deploys under
            // /carlos and a root-relative URL 404s at the host root.
            // billRegion pins the cross-province router (Billing2Action) to the
            // Ontario branch. Without it the router falls back to the
            // deployment-wide `billregion` property, and an install that never
            // set it lands on billingBC.jsp — which queries BC-only tables the
            // Ontario schema does not have, producing "CARLOS Error: 500".
            String popupUrl = request.getContextPath() + "/billing?billRegion=ON&billForm="
                    + java.net.URLEncoder.encode(defaultView, java.nio.charset.StandardCharsets.UTF_8)
                    + "&hotclick=&appointment_no=" + apptNo
                    + "&demographic_name="
                    + java.net.URLEncoder.encode(demoName, java.nio.charset.StandardCharsets.UTF_8)
                    + "&demographic_no=" + demoNo
                    + "&user_no=" + userNo
                    + "&apptProvider_no=" + nullToEmpty(providerView)
                    + "&appointment_date=" + apptDate
                    + "&start_time=" + apptTime
                    + "&bNewForm=1";

            rows.add(new BillingReportFragmentViewModel.UnbilledRow(
                    apptNo, demoNo, demoName, userNo, apptDate, apptTime, reason,
                    bodd ? "#EEEEFF" : "white", popupUrl));
        }
        return rows;
    }

    private List<BillingReportFragmentViewModel.BilledRow> loadBilledRows(
            String providerView, Date dateBegin, Date dateEnd) {
        List<BillingONCHeader1> claims = claimDao.findReportCenterOpenClaims(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.BilledRow> rows = new ArrayList<>();
        if (claims == null) return rows;
        boolean bodd = false;
        for (BillingONCHeader1 claim : claims) {
            bodd = !bodd;
            String demoName = nullToEmpty(claim.getDemographicName());
            String apptDate = ConversionUtils.toDateString(claim.getBillingDate());
            String apptTime = ConversionUtils.toTimeString(claim.getBillingTime());
            if (apptTime == null) apptTime = "00:00:00";
            String reasonCode = nullToEmpty(claim.getStatus());
            String reason = describeStatus(reasonCode);
            String note = appointmentNote(claim);
            int billingId = claim.getId() == null ? 0 : claim.getId().intValue();
            rows.add(new BillingReportFragmentViewModel.BilledRow(
                    apptDate, apptTime, demoName, reason, note,
                    billingId, reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        return rows;
    }

    private List<BillingReportFragmentViewModel.UnsettledRow> loadUnsettledRows(
            String providerView, Date dateBegin, Date dateEnd) {
        List<BillingONCHeader1> claims = claimDao.findReportCenterSubmittedClaims(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.UnsettledRow> rows = new ArrayList<>();
        if (claims == null) return rows;
        boolean bodd = false;
        for (BillingONCHeader1 claim : claims) {
            bodd = !bodd;
            String demoName = nullToEmpty(claim.getDemographicName());
            String apptDate = ConversionUtils.toDateString(claim.getBillingDate());
            String apptTime = ConversionUtils.toTimeString(claim.getBillingTime());
            if (apptTime == null) apptTime = "00:00:00";
            String reasonCode = nullToEmpty(claim.getStatus());
            String note = appointmentNote(claim);
            int billingId = claim.getId() == null ? 0 : claim.getId().intValue();
            rows.add(new BillingReportFragmentViewModel.UnsettledRow(
                    apptDate, apptTime, demoName, note,
                    billingId, reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        return rows;
    }

    private void loadBillob(String providerView, Date dateBegin, Date dateEnd,
                            BillingReportFragmentViewModel.Builder b) {
        List<BillingONCHeader1> claims = claimDao.findReportCenterClaimsWithServiceCodes(
                providerView, dateBegin, dateEnd, OB_SERVICE_CODES);
        List<BillingReportFragmentViewModel.BillobRow> rows = new ArrayList<>();
        BigDecimal total = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        if (claims == null || claims.isEmpty()) {
            b.billobRows(rows).billobTotal(total.toPlainString());
            return;
        }
        List<Integer> claimIds = claims.stream()
                .map(BillingONCHeader1::getId)
                .filter(id -> id != null)
                .toList();
        // The service-code columns list every live item of each claim, read in one query.
        Map<Integer, List<BillingONItem>> itemsByClaim =
                claimItemDao.findByCh1IdsExcludingDeleted(claimIds).stream()
                        .collect(Collectors.groupingBy(BillingONItem::getCh1Id));
        boolean bodd = false;
        for (BillingONCHeader1 claim : claims) {
            bodd = !bodd;
            String reasonCode = nullToEmpty(claim.getStatus());
            BigDecimal claimTotal = (claim.getTotal() == null ? BigDecimal.ZERO : claim.getTotal())
                    .setScale(2, RoundingMode.HALF_UP);
            // Sum the settled claims to drive the "Total ... Paid" footer.
            if ("S".equals(reasonCode)) {
                total = total.add(claimTotal);
            }
            int billingNo = claim.getId() == null ? 0 : claim.getId().intValue();
            // Up to 10 service codes, padded to the 10 columns the fragment always prints.
            List<String> codes = new ArrayList<>(10);
            for (BillingONItem item : itemsByClaim.getOrDefault(billingNo, List.of())) {
                if (codes.size() >= 10) break;
                codes.add(nullToEmpty(item.getServiceCode()));
            }
            while (codes.size() < 10) codes.add("");
            rows.add(new BillingReportFragmentViewModel.BillobRow(
                    billingNo, nullToEmpty(claim.getDemographicName()),
                    ConversionUtils.toDateString(claim.getBillingDate()), codes, claimTotal.toPlainString(),
                    reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        b.billobRows(rows).billobTotal(total.toPlainString());
    }

    private void loadFlu(String providerView, Date dateBegin, Date dateEnd,
                         BillingReportFragmentViewModel.Builder b) {
        List<io.github.carlos_emr.carlos.billings.ca.on.dto.BillFluRow> bs =
                billingDao.search_billflu(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.FluClinicRow> clinicRows = new ArrayList<>();
        List<BillingReportFragmentViewModel.FluWalkinRow> walkinRows = new ArrayList<>();
        BigDecimal total1 = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP); // walk-in (specialty == flu)
        BigDecimal total2 = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP); // clinic (specialty != flu)
        if (bs == null) {
            b.fluClinicRows(clinicRows).fluWalkinRows(walkinRows)
             .fluTotal1(total1.toPlainString()).fluTotal2(total2.toPlainString());
            return;
        }
        boolean bodd = false;
        int walkinCount = 0;
        int clinicCount = 0;
        int unreadableTotalCount = 0;
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.BillFluRow row : bs) {
            bodd = !bodd;
            String bContent = row.content();
            Integer bId = row.id();
            String bTotal = row.total();
            String bStatus = row.status();
            Date bBillingDate = row.billingDate();
            String bDemographicName = row.demographicName();
            String demoName = nullToEmpty(bDemographicName);
            String apptDate = ConversionUtils.toDateString(bBillingDate);
            String specialty = SxmlMisc.getXmlContent(bContent, "specialty");
            if (specialty == null) specialty = "";
            String reasonCode = nullToEmpty(bStatus);
            String formattedTotal = formatBillobTotal(bTotal);
            int billingNo = bId == null ? 0 : bId.intValue();
            String reasonLabel = fluReasonLabel(reasonCode);
            boolean reasonIsAnchor = reasonLabel.isEmpty();
            BigDecimal fee;
            try {
                fee = new BigDecimal(formattedTotal).setScale(2, RoundingMode.HALF_UP);
            } catch (NumberFormatException nfe) {
                unreadableTotalCount++;
                MiscUtils.getLogger().warn(
                        "BillingReportFragment: Flu bill {} has unparseable total [{}]; using 0",
                        billingNo, formattedTotal, nfe);
                fee = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
            }
            if ("flu".equals(specialty)) {
                walkinCount++;
                total1 = total1.add(fee);
                walkinRows.add(new BillingReportFragmentViewModel.FluWalkinRow(
                        billingNo, demoName, apptDate, formattedTotal,
                        reasonCode, reasonLabel, reasonIsAnchor,
                        bodd ? "#EEEEFF" : "white"));
            } else {
                clinicCount++;
                total2 = total2.add(fee);
                clinicRows.add(new BillingReportFragmentViewModel.FluClinicRow(
                        billingNo, demoName, apptDate, formattedTotal,
                        reasonCode, reasonLabel, reasonIsAnchor,
                        bodd ? "#EEEEFF" : "white"));
            }
        }
        b.fluClinicRows(clinicRows).fluWalkinRows(walkinRows)
         .fluTotal1(total1.toPlainString()).fluTotal2(total2.toPlainString())
         .fluClinicCount(clinicCount).fluWalkinCount(walkinCount)
         .fluUnreadableTotalCount(unreadableTotalCount);
    }

    /** How a claim relates to its appointment, as the Billed and Unsettled lists show it. */
    private static String appointmentNote(BillingONCHeader1 claim) {
        String apptDoctorNo = nullToEmpty(claim.getApptProviderNo());
        if ("none".equals(apptDoctorNo)) return "No Appt / INR";
        if (apptDoctorNo.equals(nullToEmpty(claim.getProviderNo()))) return "With Appt. Doctor";
        return "Unmatched Appt. Doctor";
    }

    /**
     * Returns the static label for X/B/S status codes, or an empty string for
     * everything else (the JSP renders the -B anchor instead in that case).
     * Mirrors the legacy scriptlet's reason→label ternary.
     */
    private static String fluReasonLabel(String reason) {
        if ("X".equals(reason)) return "Bad Debt";
        if ("B".equals(reason)) return "Submitted to OHIP";
        if ("S".equals(reason)) return "Settled";
        return "";
    }

    private static String describeStatus(String code) {
        switch (code) {
            case "N": return "Do Not Bill ";
            case "O": return "Bill OHIP ";
            case "W": return "Bill WSIB ";
            case "H": return "Capitated Bill ";
            case "P": return "Bill Patient";
            default: return code;
        }
    }

    /** Format a raw "12345" string into "123.45" if no decimal already present. */
    private static String formatBillobTotal(String raw) {
        if (raw == null || raw.isEmpty()) return "0.00";
        if (raw.indexOf('.') > 0) return raw;
        if (raw.length() < 3) {
            // Legacy script would have thrown StringIndexOutOfBounds on length<2;
            // be conservative and just append ".00".
            return raw + ".00";
        }
        return raw.substring(0, raw.length() - 2) + "." + raw.substring(raw.length() - 2);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
