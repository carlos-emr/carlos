/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import io.github.carlos_emr.carlos.billings.ca.bc.MSP.MSPReconcile;
import io.github.carlos_emr.carlos.entities.MSPBill;

/** Stable column contracts for the six BC accounting CSV exports. */
public final class BcBillingCsvWriter {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.CANADA);

    private BcBillingCsvWriter() {
    }

    public static boolean supports(String type) {
        return type != null && List.of(MSPReconcile.REP_ACCOUNT_REC, MSPReconcile.REP_INVOICE, MSPReconcile.REP_REJ,
                MSPReconcile.REP_WO, MSPReconcile.REP_PAYREF, MSPReconcile.REP_PAYREF_SUM,
                MSPReconcile.REP_MSPREM, MSPReconcile.REP_MSPREMSUM).contains(type);
    }

    public static String date(String value) {
        return value == null || value.isBlank() || "00000000".equals(value) ? "" : LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).format(DATE);
    }

    public static String money(String value) {
        return value == null || value.isBlank() ? "" : new BigDecimal(value).setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static String patient(MSPBill b) {
        return text(b.getDemoName()) + "(" + text(b.getDemoNo()) + ")";
    }

    private static String invoice(MSPBill b) {
        return text(b.getBilling_no()) + "-" + text(b.getBillMasterNo());
    }

    public static void write(OutputStream output, String type, List<MSPBill> bills) throws IOException {
        if (MSPReconcile.REP_PAYREF_SUM.equals(type)) {
            writePaymentSummary(output, bills);
            return;
        }
        String[] headers = switch (type) {
            case MSPReconcile.REP_ACCOUNT_REC -> new String[]{"Insurer", "Patient", "ServDate", "Invoice#", "91 Days+",
                    "61 - 90 Days", "31 - 60 Days", "0 - 30 Days", "Status"};
            case MSPReconcile.REP_INVOICE -> new String[]{"Account", "Insurer", "ServDate", "Inv#-Line#", "Patient",
                    "Qty", "Item", "Amt", "Balance", "Status", "Adjustment Code"};
            case MSPReconcile.REP_WO -> new String[]{"ID#", "Type", "Insurer", "Account", "Patient", "Amount", "ServDate", "Item", "DX"};
            case MSPReconcile.REP_REJ -> new String[]{"Patient", "Invoice #", "ServDate", "RejDate", "ID#", "Qty", "Item",
                    "Ins", "DX", "Ref", "Balance", "DOB", "Sex", "Phone 1", "Phone 2", "Start", "End", "ToDate",
                    "MVA", "Family Doctor", "Location", "Explanation"};
            case MSPReconcile.REP_PAYREF -> new String[]{"Payment Date", "Type", "Insurer", "Account", "Patient",
                    "Amount", "Explanations", "Sequence", "ServDate", "Provider", "Adjustment Amount", "Adjustment Description"};
            default -> throw new IllegalArgumentException("Unsupported accounting export");
        };
        try (var csv = new TabularExport(output, headers)) {
            for (MSPBill b : bills) {
                switch (type) {
                    case MSPReconcile.REP_ACCOUNT_REC -> csv.row(b.getBillingtype(), patient(b), date(b.getServiceDate()), invoice(b),
                            "4".equals(ageBucket(b.getServiceDate())) ? money(b.getAmount()) : "",
                            "3".equals(ageBucket(b.getServiceDate())) ? money(b.getAmount()) : "",
                            "2".equals(ageBucket(b.getServiceDate())) ? money(b.getAmount()) : "",
                            "1".equals(ageBucket(b.getServiceDate())) ? money(b.getAmount()) : "", text(b.getReason()) + text(b.getExpString()));
                    case MSPReconcile.REP_INVOICE -> csv.row(initial(b.getProviderFirstName()) + initial(b.getProviderLastName()),
                            b.getBillingtype(), date(b.getServiceDate()), invoice(b), patient(b), b.getBillingUnit(), b.getCode(),
                            money(b.getAmount()), money(b.getAmtOwing()), b.getReason(), b.getAdjustmentCode());
                    case MSPReconcile.REP_WO -> csv.row(b.getBillMasterNo(), "WO", b.getBillingtype(), b.getAcctInit(), patient(b),
                            money(b.getAmount()), date(b.getServiceDate()), b.getCode(), b.getDx1());
                    case MSPReconcile.REP_REJ -> csv.row(patient(b), invoice(b), date(b.getServiceDate()), date(b.getRejectionDate()),
                            b.getHin(), b.getBillingUnit(), b.getCode(), b.getBillingtype(), b.getDx1(), b.getExpString(), money(b.getAmount()),
                            b.getDemoDOB(), b.getDemoSex(), b.getDemoPhone(), b.getDemoPhone2(), b.getServiceStartTime(), b.getServiceEndTime(),
                            b.getServiceToDate(), b.getMvaCode(), text(b.getProviderFirstName()) + " " + text(b.getProviderLastName()),
                            b.getServiceLocation(), b.getExpSum());
                    case MSPReconcile.REP_PAYREF -> csv.row(date(b.getPaymentDate()), b.getType(), b.getBillingtype(), b.getAcctInit(),
                            patient(b), money(b.getAmount()), text(b.getExp1()) + text(b.getExp2()), b.getSeqNum(), date(b.getServiceDate()),
                            b.getProvName(), money(b.getAdjustmentCodeAmt()), b.getAdjustmentCodeDesc());
                    default -> throw new IllegalArgumentException("Unsupported accounting export");
                }
            }
        }
    }

    private static String ageBucket(String serviceDate) {
        if (serviceDate == null || serviceDate.isBlank() || "00000000".equals(serviceDate)) return "0";
        long days = java.time.temporal.ChronoUnit.DAYS.between(LocalDate.parse(serviceDate, DateTimeFormatter.BASIC_ISO_DATE), LocalDate.now());
        return days < 0 ? "0" : days <= 30 ? "1" : days <= 60 ? "2" : days <= 90 ? "3" : "4";
    }

    public static String initial(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, 1);
    }

    private static void writePaymentSummary(OutputStream output, List<MSPBill> bills) throws IOException {
        // Payments includes refunds; Refunds is the negative subset, matching the PDF.
        BigDecimal[] payments = new BigDecimal[8];
        BigDecimal[] refunds = new BigDecimal[8];
        java.util.Arrays.fill(payments, BigDecimal.ZERO);
        java.util.Arrays.fill(refunds, BigDecimal.ZERO);
        for (MSPBill b : bills) {
            int method = switch (text(b.getPaymentMethod())) {
                case "1" -> 0; case "2" -> 1; case "3" -> 2; case "4" -> 3;
                case "5" -> 4; case "6" -> 5; case "7" -> 6; default -> 7;
            };
            BigDecimal value = new BigDecimal(b.getAmount());
            payments[method] = payments[method].add(value);
            if (value.signum() < 0) refunds[method] = refunds[method].add(value);
            if (b.getAdjustmentCodeAmt() != null && !b.getAdjustmentCodeAmt().isBlank()) {
                payments[5] = payments[5].add(new BigDecimal(b.getAdjustmentCodeAmt()));
            }
        }
        String[] methods = {"Cash", "Cheque", "Visa", "Master Card", "Amex", "Electronic", "Debit", "Other"};
        try (var csv = new TabularExport(output, "Payment Method", "Payments (CAD)", "Refunds (CAD)")) {
            BigDecimal totalPayments = BigDecimal.ZERO;
            BigDecimal totalRefunds = BigDecimal.ZERO;
            for (int i = 0; i < methods.length; i++) {
                csv.row(methods[i], money(payments[i].toPlainString()), money(refunds[i].toPlainString()));
                totalPayments = totalPayments.add(payments[i]); totalRefunds = totalRefunds.add(refunds[i]);
            }
            csv.row("Total", money(totalPayments.toPlainString()), money(totalRefunds.toPlainString()));
        }
    }
}
