/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
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

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReportFragmentViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

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
 * @since 2026-04-26
 */
public final class BillingReportFragmentDataAssembler {

    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao appointmentDao;

    public BillingReportFragmentDataAssembler() {
        this(SpringUtils.getBean(BillingDao.class),
             SpringUtils.getBean(BillingDetailDao.class),
             SpringUtils.getBean(io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao.class));
    }

    BillingReportFragmentDataAssembler(BillingDao billingDao, BillingDetailDao billingDetailDao,
                                        io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao appointmentDao) {
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.appointmentDao = appointmentDao;
    }

    /**
     * Build the fragment view model. {@code reportAction} selects which
     * fragment to populate; the others default to empty lists.
     *
     * @param request live request — supplies {@code xml_vdate},
     *                {@code xml_appointment_date}, {@code providerview}
     * @param reportAction one of "billed" / "unsettled" / "billob" / "flu"
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
        List<io.github.carlos_emr.carlos.commn.model.Appointment> bs =
                appointmentDao.search_unbill_history_daterange(providerView, dateBegin, dateEnd);
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
            String apptTime = ConversionUtils.toDateString(a.getStartTime());
            String reason = nullToEmpty(a.getReason());

            String popupUrl = "/billing?billForm="
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
        List<Billing> bs = billingDao.search_bill_history_daterange(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.BilledRow> rows = new ArrayList<>();
        if (bs == null) return rows;
        boolean bodd = false;
        for (Billing bill : bs) {
            bodd = !bodd;
            String apptDoctorNo = nullToEmpty(bill.getApptProviderNo());
            String userno = nullToEmpty(bill.getProviderNo());
            String demoName = nullToEmpty(bill.getDemographicName());
            String apptDate = ConversionUtils.toDateString(bill.getBillingDate());
            String apptTime = ConversionUtils.toTimeString(bill.getBillingTime());
            if (apptTime == null) apptTime = "00:00:00";
            String reasonCode = nullToEmpty(bill.getStatus());
            String reason = describeStatus(reasonCode);
            String note;
            if ("none".equals(apptDoctorNo)) {
                note = "No Appt / INR";
            } else if (apptDoctorNo.equals(userno)) {
                note = "With Appt. Doctor";
            } else {
                note = "Unmatched Appt. Doctor";
            }
            int billingId = bill.getId() == null ? 0 : bill.getId().intValue();
            rows.add(new BillingReportFragmentViewModel.BilledRow(
                    apptDate, apptTime, demoName, reason, note,
                    billingId, reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        return rows;
    }

    private List<BillingReportFragmentViewModel.UnsettledRow> loadUnsettledRows(
            String providerView, Date dateBegin, Date dateEnd) {
        List<Billing> bs = billingDao.search_unsettled_history_daterange(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.UnsettledRow> rows = new ArrayList<>();
        if (bs == null) return rows;
        boolean bodd = false;
        for (Billing bill : bs) {
            bodd = !bodd;
            String apptDoctorNo = nullToEmpty(bill.getApptProviderNo());
            String userno = nullToEmpty(bill.getProviderNo());
            String demoName = nullToEmpty(bill.getDemographicName());
            String apptDate = ConversionUtils.toDateString(bill.getBillingDate());
            String apptTime = ConversionUtils.toTimeString(bill.getBillingTime());
            if (apptTime == null) apptTime = "00:00:00";
            String reasonCode = nullToEmpty(bill.getStatus());
            String note;
            if ("none".equals(apptDoctorNo)) {
                note = "No Appt / INR";
            } else if (apptDoctorNo.equals(userno)) {
                note = "With Appt. Doctor";
            } else {
                note = "Unmatched Appt. Doctor";
            }
            int billingId = bill.getId() == null ? 0 : bill.getId().intValue();
            rows.add(new BillingReportFragmentViewModel.UnsettledRow(
                    apptDate, apptTime, demoName, note,
                    billingId, reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        return rows;
    }

    private void loadBillob(String providerView, Date dateBegin, Date dateEnd,
                            BillingReportFragmentViewModel.Builder b) {
        List<Object[]> bs = billingDao.search_billob(providerView, dateBegin, dateEnd);
        List<BillingReportFragmentViewModel.BillobRow> rows = new ArrayList<>();
        BigDecimal total = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        if (bs == null) {
            b.billobRows(rows).billobTotal(total.toPlainString());
            return;
        }
        boolean bodd = false;
        for (Object[] row : bs) {
            bodd = !bodd;
            Integer bId = (Integer) row[0];
            String bTotal = (String) row[1];
            String bStatus = (String) row[2];
            Date bBillingDate = (Date) row[3];
            String bDemographicName = (String) row[4];
            String demoName = nullToEmpty(bDemographicName);
            String apptDate = ConversionUtils.toDateString(bBillingDate);
            String reasonCode = nullToEmpty(bStatus);
            // Format: ensure decimal point — legacy scriptlet:
            //   bTotal.indexOf(".")>0 ? bTotal : bTotal[..len-2] + "." + bTotal[len-2..]
            String formattedTotal = formatBillobTotal(bTotal);
            // Sum the billob "Settled" totals to drive the "Total ... Paid" footer.
            if ("S".equals(reasonCode)) {
                try {
                    BigDecimal fee = new BigDecimal(formattedTotal).setScale(2, RoundingMode.HALF_UP);
                    total = total.add(fee);
                } catch (NumberFormatException ignored) {
                    // legacy script swallowed parse errors; preserve that.
                }
            }
            // Service codes: up to 10 non-D-status detail entries.
            List<String> codes = new ArrayList<>(10);
            int count = 0;
            int billingNo = bId == null ? 0 : bId.intValue();
            List<BillingDetail> bds = billingDetailDao.findByBillingNo(billingNo);
            if (bds != null) {
                for (BillingDetail bd : bds) {
                    if (count >= 10) break;
                    if (bd.getStatus() != null && !"D".equals(bd.getStatus())) {
                        codes.add(nullToEmpty(bd.getServiceCode()));
                        count++;
                    }
                }
            }
            // Pad to 10 to mirror the original {@code for(int x=0; x<10; x++)} loop
            // which printed every slot regardless of count (empty string for unset).
            while (codes.size() < 10) codes.add("");
            rows.add(new BillingReportFragmentViewModel.BillobRow(
                    billingNo, demoName, apptDate, codes, formattedTotal,
                    reasonCode, bodd ? "#EEEEFF" : "white"));
        }
        b.billobRows(rows).billobTotal(total.toPlainString());
    }

    private void loadFlu(String providerView, Date dateBegin, Date dateEnd,
                         BillingReportFragmentViewModel.Builder b) {
        List<Object[]> bs = billingDao.search_billflu(providerView, dateBegin, dateEnd);
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
        for (Object[] row : bs) {
            bodd = !bodd;
            String bContent = (String) row[0];
            Integer bId = (Integer) row[1];
            String bTotal = (String) row[2];
            String bStatus = (String) row[3];
            Date bBillingDate = (Date) row[4];
            String bDemographicName = (String) row[5];
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
         .fluClinicCount(clinicCount).fluWalkinCount(walkinCount);
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
