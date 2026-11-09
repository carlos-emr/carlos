/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import io.github.carlos_emr.carlos.billing.CA.BC.model.BcBillingConstants;
import io.github.carlos_emr.carlos.entities.MSPBill;

/**
 * Stable column contracts for the six BC accounting CSV exports.
 *
 * @since 2026-11-02
 */
public final class BcBillingCsv {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd-MMM-yy", Locale.CANADA);

    private BcBillingCsv() {
    }

    /**
     * Identifies the eight supported accounting and remittance CSV report types.
     *
     * @param type String report identifier; may be null
     * @return boolean true for a supported CSV report identifier
     */
    public static boolean supports(String type) {
        return type != null && List.of(BcBillingConstants.REP_ACCOUNT_REC, BcBillingConstants.REP_INVOICE, BcBillingConstants.REP_REJ,
                BcBillingConstants.REP_WO, BcBillingConstants.REP_PAYREF, BcBillingConstants.REP_PAYREF_SUM,
                BcBillingConstants.REP_MSPREM, BcBillingConstants.REP_MSPREMSUM).contains(type);
    }

    /**
     * Formats a basic ISO date with Canadian English month names.
     *
     * @param value String date in yyyyMMdd form; null, blank and 00000000 mean missing
     * @return String formatted date, empty for a missing date, or the original malformed text
     */
    public static String date(String value) {
        if (value == null || value.isBlank() || "00000000".equals(value)) return "";
        try {
            return LocalDate.parse(value, DateTimeFormatter.BASIC_ISO_DATE).format(DATE);
        } catch (DateTimeParseException invalid) {
            return value;
        }
    }

    /**
     * Formats a decimal CAD amount with two places and half-up rounding.
     *
     * @param value String decimal amount without currency symbols or grouping; may be null or blank
     * @return String formatted amount, empty if missing, or source text for malformed/out-of-range values
     */
    public static String money(String value) {
        if (value == null || value.isBlank()) return "";
        try {
            BigDecimal amount = new BigDecimal(value);
            return Double.isFinite(amount.doubleValue())
                    ? amount.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString() : value;
        } catch (NumberFormatException invalid) {
            return value;
        }
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    private static boolean isKnownMoney(String source) {
        try { return source != null && !source.isBlank() && Double.isFinite(new BigDecimal(source).doubleValue()); }
        catch (NumberFormatException invalid) { return false; }
    }

    private static String arStatus(MSPBill bill) {
        String status = text(bill.getReason()) + text(bill.getExpString());
        if (bill.getAmtOwing() == null || bill.getAmtOwing().isBlank()) {
            status += isKnownMoney(bill.getAmount()) ? " [Balance unavailable]"
                    : " [Unparsed billed amount: " + (bill.getAmount() == null || bill.getAmount().isBlank()
                            ? "missing" : bill.getAmount()) + "]";
        }
        return status;
    }

    private static String patient(MSPBill b) {
        return text(b.getDemoName()) + "(" + text(b.getDemoNo()) + ")";
    }

    private static String invoice(MSPBill b) {
        return text(b.getBilling_no()) + "-" + text(b.getBillMasterNo());
    }

    /**
     * Writes one accounting CSV header and its data using a single server-local report date.
     * Future service dates are current for A/R aging; unusable dates use the appended Unknown age column.
     * Malformed legacy fields retain their text in detail rows; summary calculations treat them as zero.
     * The remittance types recognized by {@link #supports} use {@link BcRemittanceExportDao} instead.
     *
     * @param output OutputStream receiving UTF-8 CSV; closed when generation finishes
     * @param type String identifier for one of the six accounting report types
     * @param bills List of MSPBill records in the required report order
     * @throws IOException if CSV output fails
     * @throws IllegalArgumentException if the accounting report type is unsupported
     */
    public static void write(OutputStream output, String type, List<MSPBill> bills) throws IOException {
        write(output, type, bills, LocalDate.now(java.time.ZoneId.systemDefault()));
    }

    public static void write(OutputStream output, String type, List<MSPBill> bills, int skippedExplanations) throws IOException {
        write(output, type, bills, LocalDate.now(java.time.ZoneId.systemDefault()), skippedExplanations);
    }

    static void write(OutputStream output, String type, List<MSPBill> bills, LocalDate reportDate) throws IOException {
        write(output, type, bills, reportDate, 0);
    }

    private static void write(OutputStream output, String type, List<MSPBill> bills, LocalDate reportDate, int skippedExplanations) throws IOException {
        if (BcBillingConstants.REP_PAYREF_SUM.equals(type)) {
            writePaymentSummary(output, bills);
            return;
        }
        String[] headers = switch (type) {
            case BcBillingConstants.REP_ACCOUNT_REC -> new String[]{"Insurer", "Patient", "ServDate", "Invoice#", "91 Days+",
                    "61 - 90 Days", "31 - 60 Days", "0 - 30 Days", "Status", "Unknown age"};
            case BcBillingConstants.REP_INVOICE -> new String[]{"Account", "Insurer", "ServDate", "Inv#-Line#", "Patient",
                    "Qty", "Item", "Amt", "Balance", "Status", "Adjustment Code"};
            case BcBillingConstants.REP_WO -> new String[]{"ID#", "Type", "Insurer", "Account", "Patient", "Amount", "ServDate", "Item", "DX"};
            case BcBillingConstants.REP_REJ -> new String[]{"Patient", "Invoice #", "ServDate", "RejDate", "ID#", "Qty", "Item",
                    "Ins", "DX", "Ref", "Balance", "DOB", "Sex", "Phone 1", "Phone 2", "Start", "End", "ToDate",
                    "MVA", "Family Doctor", "Location", "Explanation"};
            case BcBillingConstants.REP_PAYREF -> new String[]{"Payment Date", "Type", "Insurer", "Account", "Patient",
                    "Amount", "Explanations", "Sequence", "ServDate", "Provider", "Adjustment Amount", "Adjustment Description"};
            default -> throw new IllegalArgumentException("Unsupported accounting export");
        };
        try (var csv = new TabularExport(output, headers)) {
            for (MSPBill b : bills) {
                String bucket = BcBillingConstants.REP_ACCOUNT_REC.equals(type) ? ageBucket(b.getServiceDate(), reportDate) : "";
                switch (type) {
                    case BcBillingConstants.REP_ACCOUNT_REC -> csv.row(b.getBillingtype(), patient(b), date(b.getServiceDate()), invoice(b),
                            "4".equals(bucket) ? money(b.getAmtOwing()) : "",
                            "3".equals(bucket) ? money(b.getAmtOwing()) : "",
                            "2".equals(bucket) ? money(b.getAmtOwing()) : "",
                            "1".equals(bucket) ? money(b.getAmtOwing()) : "", arStatus(b),
                            "unknown".equals(bucket) ? money(b.getAmtOwing()) : "");
                    case BcBillingConstants.REP_INVOICE -> csv.row(initial(b.getProviderFirstName()) + initial(b.getProviderLastName()),
                            b.getBillingtype(), date(b.getServiceDate()), invoice(b), patient(b), b.getBillingUnit(), b.getCode(),
                            money(b.getAmount()), money(b.getAmtOwing()), b.getReason(), b.getAdjustmentCode());
                    case BcBillingConstants.REP_WO -> csv.row(b.getBillMasterNo(), "WO", b.getBillingtype(), b.getAcctInit(), patient(b),
                            money(b.getAmount()), date(b.getServiceDate()), b.getCode(), b.getDx1());
                    case BcBillingConstants.REP_REJ -> csv.row(patient(b), invoice(b), date(b.getServiceDate()), date(b.getRejectionDate()),
                            b.getHin(), b.getBillingUnit(), b.getCode(), b.getBillingtype(), b.getDx1(), b.getExpString(), money(b.getAmount()),
                            b.getDemoDOB(), b.getDemoSex(), b.getDemoPhone(), b.getDemoPhone2(), b.getServiceStartTime(), b.getServiceEndTime(),
                            b.getServiceToDate(), b.getMvaCode(), text(b.getProviderFirstName()) + " " + text(b.getProviderLastName()),
                            b.getServiceLocation(), b.getExpSum());
                    case BcBillingConstants.REP_PAYREF -> csv.row(date(b.getPaymentDate()), b.getType(), b.getBillingtype(), b.getAcctInit(),
                            patient(b), money(b.getAmount()), text(b.getExp1()) + text(b.getExp2()), b.getSeqNum(), date(b.getServiceDate()),
                            b.getProvName(), money(b.getAdjustmentCodeAmt()), b.getAdjustmentCodeDesc());
                    default -> throw new IllegalArgumentException("Unsupported accounting export");
                }
            }
            if (skippedExplanations > 0 && BcBillingConstants.REP_INVOICE.equals(type)) {
                Object[] warning = new Object[headers.length];
                java.util.Arrays.fill(warning, "");
                warning[0] = "Warning: " + skippedExplanations + " C12 explanation records skipped (invalid claim identifiers)";
                csv.row(warning);
            }
        }
    }

    /**
     * Keeps unageable claims separate from the four known service-date ranges.
     *
     * @param serviceDate String compact service date, possibly missing or malformed
     * @param reportDate LocalDate fixed server-local date for this export
     * @return String legacy bucket 1 through 4, or unknown when no usable service date exists
     */
    private static String ageBucket(String serviceDate, LocalDate reportDate) {
        String range = MSPBill.ageRange(serviceDate, reportDate);
        return "0".equals(range) ? "unknown" : range;
    }

    /**
     * Extracts the first character used by the legacy provider-initial columns.
     *
     * @param value String provider name; may be null or empty
     * @return String first character, or an empty string for a missing name
     */
    public static String initial(String value) {
        return value == null || value.isEmpty() ? "" : value.substring(0, 1);
    }

    /**
     * Writes net payments and the refund subset, with adjustments in the Electronic category.
     *
     * @param output OutputStream destination owned by the CSV writer
     * @param bills List of MSPBill payment rows, including any malformed legacy fields
     * @throws IOException if writing or closing the CSV fails
     */
    private static void writePaymentSummary(OutputStream output, List<MSPBill> bills) throws IOException {
        // Payments includes refunds; Refunds is the negative subset, matching the PDF.
        BigDecimal[] payments = new BigDecimal[8];
        BigDecimal[] refunds = new BigDecimal[8];
        java.util.Arrays.fill(payments, BigDecimal.ZERO);
        java.util.Arrays.fill(refunds, BigDecimal.ZERO);
        int unparsed = 0;
        for (MSPBill b : bills) {
            unparsed += b.getUnparsedAmounts();
            if (b.getUnparsedAmounts() == 0) {
                if (b.getAmount() == null || b.getAmount().isBlank() || !isKnownMoney(b.getAmount())) unparsed++;
                if (b.getAdjustmentCodeAmt() != null && !b.getAdjustmentCodeAmt().isBlank()
                        && !isKnownMoney(b.getAdjustmentCodeAmt())) unparsed++;
            }
            int method = switch (text(b.getPaymentMethod())) {
                case "1" -> 0; case "2" -> 1; case "3" -> 2; case "4" -> 3;
                case "5" -> 4; case "6" -> 5; case "7" -> 6; default -> 7;
            };
            BigDecimal value = summaryAmount(b.getAmount());
            payments[method] = payments[method].add(value);
            if (value.signum() < 0) refunds[method] = refunds[method].add(value);
            payments[5] = payments[5].add(summaryAmount(b.getAdjustmentCodeAmt()));
        }
        String[] methods = {"Cash", "Cheque", "Visa", "Master Card", "Amex", "Electronic", "Debit", "Other"};
        try (var csv = new TabularExport(output, "Payment Method", "Payments (CAD)", "Refunds (CAD)")) {
            BigDecimal totalPayments = BigDecimal.ZERO;
            BigDecimal totalRefunds = BigDecimal.ZERO;
            for (int i = 0; i < methods.length; i++) {
                csv.row(methods[i], money(payments[i].toPlainString()), money(refunds[i].toPlainString()));
                totalPayments = totalPayments.add(payments[i]); totalRefunds = totalRefunds.add(refunds[i]);
            }
            csv.row(unparsed == 0 ? "Total" : "Total (known amounts only)", money(totalPayments.toPlainString()), money(totalRefunds.toPlainString()));
            if (unparsed > 0) csv.row("Unparsed amount fields excluded: " + unparsed, "", "");
        }
    }

    /**
     * Applies the summary's zero fallback without changing detail-export source text.
     *
     * @param value String decimal dollars, possibly missing or malformed
     * @return BigDecimal parsed amount, or zero when no usable amount exists
     */
    private static BigDecimal summaryAmount(String value) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            BigDecimal amount = new BigDecimal(value);
            return Double.isFinite(amount.doubleValue()) ? amount : BigDecimal.ZERO;
        } catch (NumberFormatException invalid) {
            // Keep the remaining valid payments/refunds exportable, as for missing amounts.
            return BigDecimal.ZERO;
        }
    }
}
