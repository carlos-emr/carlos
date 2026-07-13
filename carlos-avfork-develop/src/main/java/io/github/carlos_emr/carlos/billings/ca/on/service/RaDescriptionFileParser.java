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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaDescriptionViewModel;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Parses fixed-width OHIP RA description files and builds the XML fragments
 * persisted on {@code RaHeader.content}.
 */
@Service
public class RaDescriptionFileParser {

    public enum ParseFailureReason {
        NONE,
        MISSING_FILENAME,
        SECURITY_REJECTED,
        IO_ERROR,
        MALFORMED_RECORD,
        INCOMPLETE_HEADER
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path validated for directory containment via PathValidationUtils before use
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path validated for directory containment via PathValidationUtils before use")
    public ParsedFile parse(String filename) {
        ParsedFile out = new ParsedFile();
        if (filename == null || filename.isEmpty()) {
            out.parseFailureReason = ParseFailureReason.MISSING_FILENAME;
            return out;
        }

        String docDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "").trim();
        StringBuilder messages = new StringBuilder();

        File raFile;
        try {
            raFile = PathValidationUtils.validatePath(filename, new File(docDir));
        } catch (SecurityException se) {
            MiscUtils.getLogger().error("Rejected RA filename outside DOCUMENT_DIR: {}",
                    io.github.carlos_emr.carlos.utility.LogSafe.sanitize(filename), se);
            out.parseFailureReason = ParseFailureReason.SECURITY_REJECTED;
            return out;
        }

        try (FileInputStream file = new FileInputStream(raFile);
             InputStreamReader reader = new InputStreamReader(file, StandardCharsets.ISO_8859_1);
             BufferedReader input = new BufferedReader(reader)) {
            String nextline;
            while ((nextline = input.readLine()) != null) {
                if (nextline.length() < 3 || !"H".equals(nextline.substring(0, 1))) {
                    continue;
                }
                String headerCount = nextline.substring(2, 3);
                switch (headerCount) {
                    case "1" -> parseH1(nextline, out);
                    case "4" -> out.recordCount++;
                    case "5" -> out.claimCount++;
                    case "6" -> parseH6(nextline, out);
                    case "7" -> parseH7(nextline, out);
                    case "8" -> parseH8(nextline, messages);
                    default -> {
                        // unhandled header byte — skip
                    }
                }
            }
            out.fileReadComplete = true;
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to parse RA file '{}'", filename, e);
            out.parseFailureReason = ParseFailureReason.IO_ERROR;
        }

        if (out.fileReadComplete && !out.h1Parsed) {
            out.parseFailureReason = ParseFailureReason.INCOMPLETE_HEADER;
        }
        out.messageTxt = messages.toString();
        return out;
    }

    public String mergedContent(ParsedFile parsed, String existingContent) {
        String safeExisting = nullToEmpty(existingContent);
        // Preserve non-file totals already stored on RaHeader.content; the RA
        // description file only owns cheque/balance-forward/transaction data
        // and must not blank local/other/OB/colposcopy totals on refresh.
        String localTotal = nullToEmpty(SxmlMisc.getXmlContent(safeExisting, "<xml_local>", "</xml_local>"));
        String otherTotal = nullToEmpty(SxmlMisc.getXmlContent(safeExisting, "<xml_other_total>", "</xml_other_total>"));
        String obTotal = nullToEmpty(SxmlMisc.getXmlContent(safeExisting, "<xml_ob_total>", "</xml_ob_total>"));
        String coTotal = nullToEmpty(SxmlMisc.getXmlContent(safeExisting, "<xml_co_total>", "</xml_co_total>"));
        String newTotal = nullToEmpty(SxmlMisc.getXmlContent(safeExisting, "<xml_total>", "</xml_total>"));

        return "<xml_transaction>" + transactionRowsXml(parsed.transactionRows()) + "</xml_transaction>"
                + "<xml_balancefwd>" + balanceForwardXml(parsed.balanceForwardRow()) + "</xml_balancefwd>"
                + "<xml_local>" + localTotal + "</xml_local>"
                + "<xml_cheque>" + parsed.cheque() + "</xml_cheque>"
                + "<xml_total>" + newTotal + "</xml_total>"
                + "<xml_other_total>" + otherTotal + "</xml_other_total>"
                + "<xml_ob_total>" + obTotal + "</xml_ob_total>"
                + "<xml_co_total>" + coTotal + "</xml_co_total>";
    }

    private static void parseH1(String line, ParsedFile out) {
        if (line.length() < 77) {
            MiscUtils.getLogger().warn("H1 record too short ({} chars, need 77) — cheque total cannot be decoded", line.length());
            return;
        }
        out.paymentDate = line.substring(21, 29);
        String total = line.substring(59, 68);
        String totalStatus = line.substring(68, 69);
        try {
            out.cheque = formatSignedAmount(total, totalStatus, 2, true);
            out.h1Parsed = true;
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("H1 cheque total not numeric (raw='{}') — leaving cheque unparsed", total, e);
        }
    }

    private static void parseH6(String line, ParsedFile out) {
        if (line.length() < 39) {
            out.markMalformedRecord("H6 record too short (" + line.length() + " chars, need 39)");
            return;
        }
        SignedField abfCa;
        SignedField abfAd;
        SignedField abfRe;
        SignedField abfDe;
        try {
            abfCa = signedField(line, 3, 7, 2);
            abfAd = signedField(line, abfCa.nextIndex(), 7, 2);
            abfRe = signedField(line, abfAd.nextIndex(), 7, 2);
            abfDe = signedField(line, abfRe.nextIndex(), 7, 2);
        } catch (RuntimeException e) {
            out.markMalformedRecord("H6 balance-forward amount malformed");
            MiscUtils.getLogger().warn("Malformed H6 balance-forward record; RA header merge blocked", e);
            return;
        }
        out.balanceForwardRow = new GenerateRaDescriptionViewModel.BalanceForwardRow(
                abfCa.value(), abfAd.value(), abfRe.value(), abfDe.value());
    }

    private static void parseH7(String line, ParsedFile out) {
        if (line.length() < 72) {
            out.markMalformedRecord("H7 record too short (" + line.length() + " chars, need 72)");
            return;
        }
        String transCode = decodeTransCode(line.substring(3, 5));
        String chequeIndicator = decodeChequeIndicator(line.substring(5, 6));
        String transDate = line.substring(6, 14);
        String sign = "";
        int messageStart = 23;
        if (line.length() > 22 && isSignChar(line.charAt(22))) {
            sign = line.substring(22, 23);
        }
        String transAmount;
        try {
            transAmount = formatSignedAmount(line.substring(14, 22), sign, 2, false);
        } catch (RuntimeException e) {
            out.markMalformedRecord("H7 transaction amount malformed");
            MiscUtils.getLogger().warn("Malformed H7 transaction amount; RA header merge blocked", e);
            return;
        }
        String transMessage = line.substring(messageStart, Math.min(line.length(), messageStart + 50));
        out.transactionRows.add(new GenerateRaDescriptionViewModel.TransactionRow(
                transCode, transDate, chequeIndicator, transAmount, transMessage));
    }

    private static SignedField signedField(String line, int start, int wholeDigits, int fractionDigits) {
        int end = start + wholeDigits + fractionDigits;
        String rawAmount = line.substring(start, end);
        String sign = "";
        if (line.length() > end && isSignChar(line.charAt(end))) {
            sign = line.substring(end, end + 1);
            end++;
        }
        return new SignedField(formatSignedAmount(rawAmount, sign, fractionDigits, false), end);
    }

    private static String formatSignedAmount(String rawAmount, String sign, int fractionDigits,
                                             boolean stripWholeLeadingZeros) {
        String digits = rawAmount == null ? "" : rawAmount.trim();
        if (digits.isEmpty()) {
            digits = "0";
        }
        if (!digits.matches("\\d+")) {
            throw new NumberFormatException("RA amount is not numeric");
        }
        while (digits.length() <= fractionDigits) {
            digits = "0" + digits;
        }
        String whole = digits.substring(0, digits.length() - fractionDigits);
        String fraction = digits.substring(digits.length() - fractionDigits);
        if (stripWholeLeadingZeros) {
            whole = new java.math.BigInteger(whole).toString();
        }
        return ("-".equals(sign) ? "-" : "") + whole + "." + fraction;
    }

    private static boolean isSignChar(char ch) {
        return ch == '-' || ch == '+' || ch == ' ';
    }

    private record SignedField(String value, int nextIndex) { }

    private static void parseH8(String line, StringBuilder messages) {
        if (line.length() >= 73) {
            messages.append(line.substring(3, 73)).append("\r\n");
        }
    }

    private static String decodeTransCode(String code) {
        return switch (code) {
            case "10" -> "Advance";
            case "20" -> "Reduction";
            case "30" -> "Unused";
            case "40" -> "Advance repayment";
            case "50" -> "Accounting adjustment";
            case "70" -> "Attachments";
            default -> code;
        };
    }

    private static String decodeChequeIndicator(String code) {
        return switch (code) {
            case "M" -> "Manual Cheque issued";
            case "C" -> "Computer Cheque issued";
            case "I" -> "Interim payment Cheque/Direct Bank Deposit issued";
            case " ", "N" -> "No Cheque issued";
            default -> code;
        };
    }

    private static String balanceForwardXml(GenerateRaDescriptionViewModel.BalanceForwardRow row) {
        GenerateRaDescriptionViewModel.BalanceForwardRow safe = row == null
                ? new GenerateRaDescriptionViewModel.BalanceForwardRow("0.000", "0.000", "0.000", "0.000") : row;
        return "<claimsAdjustment>" + xmlText(safe.claimsAdjustment()) + "</claimsAdjustment>"
                + "<advances>" + xmlText(safe.advances()) + "</advances>"
                + "<reductions>" + xmlText(safe.reductions()) + "</reductions>"
                + "<deductions>" + xmlText(safe.deductions()) + "</deductions>";
    }

    private static String transactionRowsXml(List<GenerateRaDescriptionViewModel.TransactionRow> rows) {
        StringBuilder out = new StringBuilder();
        for (GenerateRaDescriptionViewModel.TransactionRow row : rows) {
            out.append("<row>")
                    .append("<transaction>").append(xmlText(row.transaction())).append("</transaction>")
                    .append("<transactionDate>").append(xmlText(row.transactionDate())).append("</transactionDate>")
                    .append("<chequeIssued>").append(xmlText(row.chequeIssued())).append("</chequeIssued>")
                    .append("<amount>").append(xmlText(row.amount())).append("</amount>")
                    .append("<message>").append(xmlText(row.message())).append("</message>")
                    .append("</row>");
        }
        return out.toString();
    }

    private static String xmlText(String value) {
        return nullToEmpty(value)
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    public static final class ParsedFile {
        private String paymentDate = "";
        private String cheque = "0.00";
        private int recordCount = 0;
        private int claimCount = 0;
        private GenerateRaDescriptionViewModel.BalanceForwardRow balanceForwardRow =
                new GenerateRaDescriptionViewModel.BalanceForwardRow("0.000", "0.000", "0.000", "0.000");
        private final List<GenerateRaDescriptionViewModel.TransactionRow> transactionRows = new ArrayList<>();
        private String messageTxt = "";
        private boolean h1Parsed = false;
        private boolean fileReadComplete = false;
        private ParseFailureReason parseFailureReason = ParseFailureReason.NONE;

        public String paymentDate() { return paymentDate; }
        public String cheque() { return cheque; }
        public int recordCount() { return recordCount; }
        public int claimCount() { return claimCount; }
        public GenerateRaDescriptionViewModel.BalanceForwardRow balanceForwardRow() { return balanceForwardRow; }
        public List<GenerateRaDescriptionViewModel.TransactionRow> transactionRows() { return List.copyOf(transactionRows); }
        public String messageTxt() { return messageTxt; }
        public boolean h1Parsed() { return h1Parsed; }
        public boolean fileReadComplete() { return fileReadComplete; }
        public ParseFailureReason parseFailureReason() { return parseFailureReason; }
        public boolean isCompleteForHeaderMerge() {
            return fileReadComplete && h1Parsed && parseFailureReason != ParseFailureReason.MALFORMED_RECORD;
        }

        private void markMalformedRecord(String reason) {
            MiscUtils.getLogger().warn("Malformed RA description record: {}", reason);
            parseFailureReason = ParseFailureReason.MALFORMED_RECORD;
        }
    }
}
