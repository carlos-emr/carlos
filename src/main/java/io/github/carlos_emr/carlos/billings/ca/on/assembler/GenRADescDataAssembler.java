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

import java.io.BufferedReader;
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
import io.github.carlos_emr.carlos.billings.ca.on.data.GenRADescViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingONPremiumDao;
import io.github.carlos_emr.carlos.commn.dao.RaHeaderDao;
import io.github.carlos_emr.carlos.commn.model.BillingONPremium;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.RaHeader;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles {@link GenRADescViewModel} for {@code genRADesc.jsp}, the OHIP RA
 * (Remittance Advice) reconciliation report. Owns the 3 inline
 * {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (RaHeaderDao, BillingONPremiumDao, ProviderDao) plus the RA-file parsing
 * and the per-RA-header DB merge.
 *
 * <p>This is a <strong>mutation-on-render</strong> assembler — the JSP-era
 * scriptlet ran the same writes inline. Specifically:</p>
 * <ul>
 *   <li>Re-parses the OHIP RA file pointed at by {@code RaHeader.filename},
 *       building the {@code xml_ra} content blob from H1/H6/H7/H8 records.</li>
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
public final class GenRADescDataAssembler {

    private final RaHeaderDao raHeaderDao;
    private final BillingONPremiumDao billingONPremiumDao;
    private final ProviderDao providerDao;

    public GenRADescDataAssembler() {
        this(SpringUtils.getBean(RaHeaderDao.class),
             SpringUtils.getBean(BillingONPremiumDao.class),
             SpringUtils.getBean(ProviderDao.class));
    }

    GenRADescDataAssembler(RaHeaderDao raHeaderDao,
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
    public GenRADescViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String raNoStr = request.getParameter("rano");
        Integer raNo = parseInt(raNoStr);

        GenRADescViewModel.Builder b = GenRADescViewModel.builder().raNo(nullToEmpty(raNoStr));
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

        String transactionHtml = parsed.transactionsEmpty()
                ? ""
                : "<xml_transaction><table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                  + "<tr><td colspan='5'>Accounting Transaction Record</td></tr>"
                  + "<tr><td width='14%'>Transaction</td><td width='12%'>Transaction Date</td>"
                  + "<td width='17%'>Cheque Issued</td><td width='13%'>Amount</td>"
                  + "<td width='44%'>Message</td></tr>"
                  + parsed.transactionRows + "</table></xml_transaction>";

        String balanceFwdHtml = "<xml_balancefwd><table width='100%' border='0' cellspacing='0' cellpadding='0'>"
                + "<tr><td colspan='4'>Balance Forward Record - Amount Brought Forward (ABF)</td></tr>"
                + "<tr><td>Claims Adjustment</td><td>Advances</td><td>Reductions</td><td>Deductions</td></tr>"
                + "<tr><td>" + parsed.abfCa + "</td><td>" + parsed.abfAd + "</td>"
                + "<td>" + parsed.abfRe + "</td><td>" + parsed.abfDe + "</td></tr></table></xml_balancefwd>";

        String mergedContent = transactionHtml + balanceFwdHtml
                + "<xml_local>" + localTotal + "</xml_local>"
                + "<xml_cheque>" + parsed.cheque + "</xml_cheque>"
                + "<xml_total>" + newTotal + "</xml_total>"
                + "<xml_other_total>" + otherTotal + "</xml_other_total>"
                + "<xml_ob_total>" + obTotal + "</xml_ob_total>"
                + "<xml_co_total>" + coTotal + "</xml_co_total>";

        // Mutation: persist parsed totals + merged content blob onto every
        // RaHeader sharing this filename + payment date.
        for (RaHeader r : raHeaderDao.findByFilenamePaymentDate(rh.getFilename(), parsed.paymentDate)) {
            r.setTotalAmount(parsed.cheque);
            r.setRecords(String.valueOf(parsed.recordCount));
            r.setClaims(String.valueOf(parsed.claimCount));
            r.setContent(mergedContent);
            raHeaderDao.merge(r);
        }

        // Re-load to get the canonical balance-forward / transaction blocks
        // from the merged content (matches the legacy second-load behavior).
        rh = raHeaderDao.find(raNo);
        String renderedBalance = "";
        String renderedTransaction = "";
        if (rh != null && !"D".equals(rh.getStatus())) {
            String c = nullToEmpty(rh.getContent());
            renderedBalance = nullToEmpty(SxmlMisc.getXmlContent(c, "<xml_balancefwd>", "</xml_balancefwd>"));
            renderedTransaction = nullToEmpty(SxmlMisc.getXmlContent(c, "<xml_transaction>", "</xml_transaction>"));
        }

        b.chequeTotal(parsed.cheque)
                .localTotal(localTotal)
                .otherTotal(otherTotal)
                .obTotal(obTotal)
                .coTotal(coTotal)
                .balanceForwardHtml(renderedBalance)
                .transactionHtml(renderedTransaction)
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

    private List<GenRADescViewModel.PremiumRow> loadPremiumRows(Integer raNo, Locale locale) {
        List<GenRADescViewModel.PremiumRow> rows = new ArrayList<>();
        for (BillingONPremium premium : billingONPremiumDao.getRAPremiumsByRaHeaderNo(raNo)) {
            List<Provider> providers = providerDao.getBillableProvidersByOHIPNo(premium.getProviderOHIPNo());
            if (providers == null || providers.isEmpty()) {
                continue;
            }
            List<GenRADescViewModel.ProviderOption> options = new ArrayList<>();
            String premiumProviderNo = premium.getProviderNo();
            for (Provider p : providers) {
                boolean selected = premiumProviderNo != null
                        && premiumProviderNo.equals(p.getProviderNo());
                options.add(new GenRADescViewModel.ProviderOption(
                        nullToEmpty(p.getProviderNo()),
                        nullToEmpty(p.getFormattedName()),
                        selected));
            }
            rows.add(new GenRADescViewModel.PremiumRow(
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
        StringBuilder transactions = new StringBuilder();
        StringBuilder messages = new StringBuilder();

        try (FileInputStream file = new FileInputStream(docDir + filename);
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
                    case "7" -> parseH7(nextline, transactions);
                    case "8" -> parseH8(nextline, messages);
                    default -> {
                        // unhandled header byte — skip
                    }
                }
            }
        } catch (IOException e) {
            MiscUtils.getLogger().error("Failed to parse RA file '{}'", filename, e);
        }

        out.transactionRows = transactions.toString();
        out.messageTxt = messages.toString();
        return out;
    }

    private static void parseH1(String line, ParsedFile out) {
        if (line.length() < 77) return;
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
        } catch (NumberFormatException e) {
            out.cheque = "0.00";
        }
    }

    private static void parseH6(String line, ParsedFile out) {
        if (line.length() < 43) return;
        out.abfCa = line.substring(3, 10) + "." + line.substring(10, 13);
        out.abfAd = line.substring(13, 20) + "." + line.substring(20, 23);
        out.abfRe = line.substring(23, 30) + "." + line.substring(30, 33);
        out.abfDe = line.substring(33, 40) + "." + line.substring(40, 43);
    }

    private static void parseH7(String line, StringBuilder transactions) {
        if (line.length() < 73) return;
        String transCode = decodeTransCode(line.substring(3, 5));
        String chequeIndicator = decodeChequeIndicator(line.substring(5, 6));
        String transDate = line.substring(6, 14);
        String transAmount = line.substring(14, 20) + "." + line.substring(20, 23);
        String transMessage = line.substring(23, 73);
        transactions.append("<tr><td width='14%'>").append(transCode)
                .append("</td><td width='12%'>").append(transDate)
                .append("</td><td width='17%'>").append(chequeIndicator)
                .append("</td><td width='13%'>").append(transAmount)
                .append("</td><td width='44%'>").append(transMessage).append("</td></tr>");
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

    private static class ParsedFile {
        String paymentDate = "";
        String cheque = "0.00";
        String abfCa = "0.000";
        String abfAd = "0.000";
        String abfRe = "0.000";
        String abfDe = "0.000";
        int recordCount = 0;
        int claimCount = 0;
        String transactionRows = "";
        String messageTxt = "";

        boolean transactionsEmpty() {
            return transactionRows == null || transactionRows.isEmpty();
        }
    }
}
