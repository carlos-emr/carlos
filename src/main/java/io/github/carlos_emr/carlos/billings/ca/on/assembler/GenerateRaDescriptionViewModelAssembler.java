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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GenerateRaDescriptionViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

/**
 * Assembles {@link GenerateRaDescriptionViewModel} for {@code genRADesc.jsp}, the OHIP RA
 * (Remittance Advice) reconciliation report. Owns the 3 inline
 * {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (RaHeaderDao, BillingONPremiumDao, ProviderDao) plus the RA-file parsing
 * and the per-RA-header DB merge.
 *
 * <p>This is a <strong>mutation-on-render</strong> assembler — the JSP-era
 * scriptlet ran the same writes inline. Specifically:</p>
 * <ul>
 *   <li>Re-parses the OHIP RA file pointed at by {@code RaHeader.filename},
 *       building structured model rows from H1/H6/H7/H8 records.</li>
 *   <li>{@code RaHeaderDao.merge}: updates the RA header row with parsed
 *       totalAmount / records / claims / content.</li>
 *   <li>{@code BillingONPremiumDao.parseAndSaveRAPremiums}: idempotent —
 *       only triggers when the premium list is empty for this RA.</li>
 * </ul>
 *
 * <p>The class is package-private; the action class wires it up.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class GenerateRaDescriptionViewModelAssembler {

    private final RaHeaderDao raHeaderDao;
    private final BillingONPremiumDao billingONPremiumDao;
    private final ProviderDao providerDao;

    public GenerateRaDescriptionViewModelAssembler(RaHeaderDao raHeaderDao,
                           BillingONPremiumDao billingONPremiumDao,
                           ProviderDao providerDao) {
        this.raHeaderDao = raHeaderDao;
        this.billingONPremiumDao = billingONPremiumDao;
        this.providerDao = providerDao;
    }

    /**
     * Build the RA reconciliation view model.
     *
     * @param request in-flight request — supplies the {@code rano} parameter
     *                and the locale for date formatting
     * @param loggedInInfo session principal — needed by
     *                     {@link BillingONPremiumDao#parseAndSaveRAPremiums}
     *                     for audit fields
     * @return populated view model. Returns an empty stub when the RA header
     *         is missing or marked deleted ({@code status="D"}); the JSP
     *         renders a near-empty page in that case.
     */
    public GenerateRaDescriptionViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String raNoStr = request.getParameter("rano");
        Integer raNo = parseInt(raNoStr);

        GenerateRaDescriptionViewModel.Builder b = GenerateRaDescriptionViewModel.builder().raNo(nullToEmpty(raNoStr));
        if (raNo == null) {
            return b.build();
        }

        RaHeader rh = raHeaderDao.find(raNo);
        if (rh == null || "D".equals(rh.getStatus())) {
            return b.build();
        }

        ParsedFile parsed = parseRaFile(rh.getFilename());

        // Existing RaHeader content carries non-file totals (xml_ob_total,
        // xml_co_total) populated by upstream pipelines. Preserve them in
        // the merged content blob so downstream consumers don't lose them.
        String existingContent = nullToEmpty(rh.getContent());
        String localTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_local>", "</xml_local>"));
        String otherTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_other_total>", "</xml_other_total>"));
        String obTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_ob_total>", "</xml_ob_total>"));
        String coTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_co_total>", "</xml_co_total>"));
        String newTotal = nullToEmpty(SxmlMisc.getXmlContent(existingContent, "<xml_total>", "</xml_total>"));

        String mergedContent = "<xml_transaction>" + transactionRowsXml(parsed.transactionRows) + "</xml_transaction>"
                + "<xml_balancefwd>" + balanceForwardXml(parsed.balanceForwardRow) + "</xml_balancefwd>"
                + "<xml_local>" + localTotal + "</xml_local>"
                + "<xml_cheque>" + parsed.cheque + "</xml_cheque>"
                + "<xml_total>" + newTotal + "</xml_total>"
                + "<xml_other_total>" + otherTotal + "</xml_other_total>"
                + "<xml_ob_total>" + obTotal + "</xml_ob_total>"
                + "<xml_co_total>" + coTotal + "</xml_co_total>";

        // Mutation: persist parsed totals + merged content blob onto every
        // RaHeader sharing this filename + payment date — but ONLY when the
        // parse fully completed AND H1 totals decoded cleanly. Otherwise the
        // merge would stamp partial/zero state onto persisted rows, masking
        // upstream pipeline data with fake "0.00" cheques and zero counts.
        if (parsed.fileReadComplete && parsed.h1Parsed) {
            for (RaHeader r : raHeaderDao.findByFilenamePaymentDate(rh.getFilename(), parsed.paymentDate)) {
                r.setTotalAmount(parsed.cheque);
                r.setRecords(String.valueOf(parsed.recordCount));
                r.setClaims(String.valueOf(parsed.claimCount));
                r.setContent(mergedContent);
                raHeaderDao.merge(r);
            }
        } else {
            MiscUtils.getLogger().warn(
                    "Skipping RaHeader merge for raNo={} — parse incomplete (fileReadComplete={}, h1Parsed={})",
                    raNo, parsed.fileReadComplete, parsed.h1Parsed);
        }

        b.chequeTotal(parsed.cheque)
                .localTotal(localTotal)
                .otherTotal(otherTotal)
                .obTotal(obTotal)
                .coTotal(coTotal)
                .balanceForwardRow(parsed.balanceForwardRow)
                .transactionRows(parsed.transactionRows)
                .messageTxt(parsed.messageTxt);

        // Practitioner premiums: lazy-populate then load the rows + each
        // row's OHIP-mapped provider dropdown options.
        if (loggedInInfo != null) {
            ensurePremiumsParsed(loggedInInfo, raNo, request.getLocale());
        }
        b.premiumRows(loadPremiumRows(raNo, request.getLocale()));

        return b.build();
    }

    private void ensurePremiumsParsed(LoggedInInfo loggedInInfo, Integer raNo, Locale locale) {
        List<BillingONPremium> existing = billingONPremiumDao.getRAPremiumsByRaHeaderNo(raNo);
        if (existing.isEmpty()) {
            billingONPremiumDao.parseAndSaveRAPremiums(loggedInInfo, raNo, locale);
        }
    }

    private List<GenerateRaDescriptionViewModel.PremiumRow> loadPremiumRows(Integer raNo, Locale locale) {
        List<GenerateRaDescriptionViewModel.PremiumRow> rows = new ArrayList<>();
        for (BillingONPremium premium : billingONPremiumDao.getRAPremiumsByRaHeaderNo(raNo)) {
            List<Provider> providers = providerDao.getBillableProvidersByOHIPNo(premium.getProviderOHIPNo());
            if (providers == null || providers.isEmpty()) {
                continue;
            }
            List<GenerateRaDescriptionViewModel.ProviderOption> options = new ArrayList<>();
            String premiumProviderNo = premium.getProviderNo();
            for (Provider p : providers) {
                boolean selected = premiumProviderNo != null
                        && premiumProviderNo.equals(p.getProviderNo());
                options.add(new GenerateRaDescriptionViewModel.ProviderOption(
                        nullToEmpty(p.getProviderNo()),
                        nullToEmpty(p.getFormattedName()),
                        selected));
            }
            rows.add(new GenerateRaDescriptionViewModel.PremiumRow(
                    premium.getId(),
                    nullToEmpty(premium.getProviderOHIPNo()),
                    nullToEmpty(premium.getAmountPay()),
                    DateUtils.formatDate(premium.getPayDate(), locale),
                    Boolean.TRUE.equals(premium.getStatus()),
                    options));
        }
        return rows;
    }

    /**
     * Parse the OHIP RA fixed-width file at the absolute path given by
     * {@code DOCUMENT_DIR + filename}. Walks each line, dispatching on
     * the H{n} header byte. Layout positions are fixed by OHIP spec.
     */
    private ParsedFile parseRaFile(String filename) {
        ParsedFile out = new ParsedFile();
        if (filename == null || filename.isEmpty()) {
            return out;
        }

        String docDir = CarlosProperties.getInstance().getProperty("DOCUMENT_DIR", "").trim();
        StringBuilder messages = new StringBuilder();

        File raFile;
        try {
            raFile = PathValidationUtils.validatePath(filename, new File(docDir));
        } catch (SecurityException se) {
            MiscUtils.getLogger().error("Rejected RA filename outside DOCUMENT_DIR", se);
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
            // Set only after the loop completes without IOException so the
            // assemble() merge can distinguish a clean read from a partial one.
            out.fileReadComplete = true;
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to parse RA file '{}'", filename, e);
        }

        out.messageTxt = messages.toString();
        return out;
    }

    private static void parseH1(String line, ParsedFile out) {
        if (line.length() < 77) {
            MiscUtils.getLogger().warn("H1 record too short ({} chars, need 77) — cheque total cannot be decoded", line.length());
            return;
        }
        out.paymentDate = line.substring(21, 29);
        // payable substring 29..59 unused in render; preserved here for parity
        String total = line.substring(59, 68);
        String totalStatus = line.substring(68, 69);
        try {
            int totalSum = Integer.parseInt(total);
            if (totalSum == 0) {
                out.cheque = "0.00";
            } else {
                String s = String.valueOf(totalSum);
                out.cheque = s.substring(0, s.length() - 2) + "." + s.substring(s.length() - 2) + totalStatus;
            }
            out.h1Parsed = true;
        } catch (NumberFormatException e) {
            // Don't silently fall back to "0.00" — leaving h1Parsed=false makes
            // the assemble() merge skip this RA so a parse failure can't stamp
            // a fake zero cheque onto persisted RaHeader rows.
            MiscUtils.getLogger().warn("H1 cheque total not numeric (raw='{}') — leaving cheque unparsed", total, e);
        }
    }

    private static void parseH6(String line, ParsedFile out) {
        if (line.length() < 43) return;
        out.abfCa = line.substring(3, 10) + "." + line.substring(10, 13);
        out.abfAd = line.substring(13, 20) + "." + line.substring(20, 23);
        out.abfRe = line.substring(23, 30) + "." + line.substring(30, 33);
        out.abfDe = line.substring(33, 40) + "." + line.substring(40, 43);
        out.balanceForwardRow = new GenerateRaDescriptionViewModel.BalanceForwardRow(
                out.abfCa, out.abfAd, out.abfRe, out.abfDe);
    }

    private static void parseH7(String line, ParsedFile out) {
        if (line.length() < 73) return;
        String transCode = decodeTransCode(line.substring(3, 5));
        String chequeIndicator = decodeChequeIndicator(line.substring(5, 6));
        String transDate = line.substring(6, 14);
        String transAmount = line.substring(14, 20) + "." + line.substring(20, 23);
        String transMessage = line.substring(23, 73);
        out.transactionRows.add(new GenerateRaDescriptionViewModel.TransactionRow(
                transCode, transDate, chequeIndicator, transAmount, transMessage));
    }

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

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

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

    private static class ParsedFile {
        String paymentDate = "";
        String cheque = "0.00";
        String abfCa = "0.000";
        String abfAd = "0.000";
        String abfRe = "0.000";
        String abfDe = "0.000";
        int recordCount = 0;
        int claimCount = 0;
        GenerateRaDescriptionViewModel.BalanceForwardRow balanceForwardRow =
                new GenerateRaDescriptionViewModel.BalanceForwardRow("0.000", "0.000", "0.000", "0.000");
        List<GenerateRaDescriptionViewModel.TransactionRow> transactionRows = new ArrayList<>();
        String messageTxt = "";
        // True only after parseH1 successfully decoded the cheque total.
        // False if the H1 line was short or the embedded total wasn't numeric —
        // the merge must NOT run in that state, otherwise a "0.00" zero-fallback
        // gets persisted onto every RaHeader sharing this filename.
        boolean h1Parsed = false;
        // True only after the file read loop reached EOF without IOException.
        // False if the stream blew up mid-parse — partial transaction/balance
        // state would otherwise be merged as if it were a complete parse.
        boolean fileReadComplete = false;
    }
}
