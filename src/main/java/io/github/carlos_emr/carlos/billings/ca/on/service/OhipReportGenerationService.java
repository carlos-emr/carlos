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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.on.OHIP.ExtractBean;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.DateRange;
/**
 * Shared mutation service for the three forward-shim OHIP-extract JSPs:
 * {@code genreport.jsp}, {@code genGroupReport.jsp}, and
 * {@code genSimulation.jsp}.
 *
 * <p>All three pages did the same broad work: iterate billable providers,
 * run {@link ExtractBean} per provider to generate the OHIP claim
 * file/HTML preview, optionally persist a {@link BillActivity} row, then
 * {@code <jsp:forward>} to a downstream display page. This service owns
 * the {@code BillActivityDao} + {@code ProviderDao} lookups the JSPs
 * performed inline.</p>
 *
 * <p>Three modes:
 * <ul>
 *   <li>{@link Mode#GROUP_REPORT}: for hybrid-billing group submissions —
 *       writes a group-keyed file with {@code H{monthCode}{groupNo}} naming.</li>
 *   <li>{@link Mode#SOLO_REPORT}: for solo-provider submissions —
 *       writes provider-keyed files; skips group-billing providers.</li>
 *   <li>{@link Mode#SIMULATION}: dry run with {@code eFlag="0"} — no
 *       BillActivity persist, no file write; just builds an HTML preview
 *       which the action stashes on the request for the simulation page.</li>
 * </ul>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class OhipReportGenerationService {

    public enum Mode { GROUP_REPORT, SOLO_REPORT, SIMULATION }

    private static final int PROVIDER_BILLINGNO_LENGTH = 6;
    private static final int PROVIDER_SPECIALTYCODE_LENGTH = 2;
    private static final int PROVIDER_GROUPNO_LENGTH = 4;

    private final BillActivityDao billActivityDao;
    private final ProviderDao providerDao;

    OhipReportGenerationService(BillActivityDao billActivityDao, ProviderDao providerDao) {
        this.billActivityDao = billActivityDao;
        this.providerDao = providerDao;
    }

    /**
     * Result returned to the simulation mode — captures the HTML preview
     * the legacy JSP stashed on the request via {@code request.setAttribute}.
     */
    public record SimulationResult(String htmlPreview, String errorMsg, String dateBeginStr, String dateEndStr) {}

    /**
     * Run the OHIP extract for a group/solo submission. Persists
     * BillActivity rows and writes the OHIP+HTML files for each
     * eligible provider.
     */
    public void generateReport(HttpServletRequest request, Mode mode) {
        if (mode == Mode.SIMULATION) {
            throw new IllegalArgumentException("Use generateSimulation for SIMULATION mode");
        }

        String monthCode = request.getParameter("monthCode");
        if (monthCode == null || monthCode.isEmpty()) {
            return;
        }

        String providerParam = request.getParameter("providers");
        if (providerParam == null || providerParam.trim().isEmpty()) {
            return;
        }
        providerParam = providerParam.trim();

        boolean hybridBilling = isHybridBilling();
        List<String> hybridProviders = hybridProviderList();
        int curYear = new GregorianCalendar().get(Calendar.YEAR);

        List<Provider> providersToProcess = resolveProviders(providerParam);

        int batchOrdinal = 1;
        for (Provider p : providersToProcess) {
            if (p.getOhipNo() == null || p.getOhipNo().isEmpty()) continue;

            // Hybrid filter: GROUP_REPORT processes only group providers;
            // SOLO_REPORT processes only solo (non-group) providers.
            boolean inHybridList = hybridBilling && hybridProviders.contains(p.getProviderNo());
            if (mode == Mode.GROUP_REPORT && hybridBilling && !inHybridList) continue;
            if (mode == Mode.SOLO_REPORT && hybridBilling && inHybridList) continue;

            String proOHIP = p.getOhipNo();
            String groupNo = sanitizeGroupNo(SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>"));
            String specialty = sanitizeSpecialty(SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_specialty_code>", "</xml_p_specialty_code>"));

            String groupKey = mode == Mode.GROUP_REPORT ? groupNo : proOHIP;
            String batchCount = nextBatchCount(monthCode, groupKey, curYear);

            ExtractBean extract = new ExtractBean();
            extract.seteFlag("1");
            extract.setOhipVer(request.getParameter("verCode"));
            extract.setProviderNo(proOHIP);
            extract.setOhipCenter(request.getParameter("billcenter"));
            extract.setGroupNo(groupNo);
            extract.setSpecialty(specialty);
            extract.setBatchCount(String.valueOf(batchOrdinal));
            extract.dbQuery();

            String[] filenames = buildFilenames(monthCode, groupKey, proOHIP, batchCount,
                    mode == Mode.GROUP_REPORT);
            persistBillActivity(monthCode, batchCount, filenames[0], filenames[1],
                    proOHIP, groupNo, request.getParameter("curUser"), extract);
            extract.setHtmlFilename(filenames[1]);
            extract.setOhipFilename(filenames[0]);
            extract.writeFile(extract.getValue());
            extract.writeHtml(extract.getHtmlCode());

            batchOrdinal++;
        }
    }

    /**
     * Run the OHIP simulation (eFlag="0") — produces an HTML preview
     * for {@code genSimulation.jsp}. No persistence.
     */
    public SimulationResult generateSimulation(HttpServletRequest request) {
        StringBuilder errorMsg = new StringBuilder();
        String provider = request.getParameter("providers");
        if (provider == null || provider.length() != PROVIDER_BILLINGNO_LENGTH) {
            errorMsg.append("The providers's billing code is not correct!<br>");
        }

        Date dateBegin = ConversionUtils.fromDateString(request.getParameter("xml_vdate"));
        Date dateEnd = ConversionUtils.fromDateString(request.getParameter("xml_appointment_date"));
        if (dateEnd == null) {
            dateEnd = ConversionUtils.fromDateString(request.getParameter("curDate"));
        }
        DateRange dateRange = new DateRange(dateBegin, dateEnd);

        StringBuilder htmlValue = new StringBuilder();
        for (Provider p : providerDao.getActiveProviders()) {
            if (p.getOhipNo() == null || p.getOhipNo().isEmpty()) continue;

            String proOHIP = p.getOhipNo();
            String groupNo = SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_billinggroup_no>", "</xml_p_billinggroup_no>");
            String specialty = SxmlMisc.getXmlContent(p.getComments(),
                    "<xml_p_specialty_code>", "</xml_p_specialty_code>");

            if (specialty == null || specialty.isEmpty() || "null".equals(specialty)
                    || specialty.length() != PROVIDER_SPECIALTYCODE_LENGTH) {
                errorMsg.append("The providers's specialty code is not correct!<br>");
                specialty = "00";
            }
            if (groupNo == null || groupNo.isEmpty() || "null".equals(groupNo)
                    || groupNo.length() != PROVIDER_GROUPNO_LENGTH) {
                errorMsg.append("The providers's group no is not correct!<br>");
                groupNo = "0000";
            }

            ExtractBean extract = new ExtractBean();
            extract.seteFlag("0");
            extract.setDateRange(dateRange);
            extract.setOhipVer(request.getParameter("verCode"));
            extract.setProviderNo(proOHIP);
            extract.setOhipCenter(request.getParameter("billcenter"));
            extract.setGroupNo(groupNo);
            extract.setSpecialty(specialty);
            extract.setBatchCount("1");
            extract.dbQuery();

            htmlValue.append(extract.getHtmlValue());
        }
        return new SimulationResult(htmlValue.toString(), errorMsg.toString(),
                dateBegin == null ? "" : dateBegin.toString(),
                dateEnd == null ? "" : dateEnd.toString());
    }

    private List<Provider> resolveProviders(String providerParam) {
        if ("all".equalsIgnoreCase(providerParam)) {
            return providerDao.getActiveProviders();
        }
        // Single provider — first 6 chars of the param value.
        String pn = providerParam.length() > PROVIDER_BILLINGNO_LENGTH
                ? providerParam.substring(0, PROVIDER_BILLINGNO_LENGTH) : providerParam;
        Provider p = providerDao.getProvider(pn);
        List<Provider> out = new ArrayList<>();
        if (p != null) out.add(p);
        return out;
    }

    /**
     * Whether the {@code hybrid_billing} property is enabled. Callers
     * use this to decide whether to chain a SOLO_REPORT into a follow-up
     * GROUP_REPORT pass.
     */
    public boolean isHybridBilling() {
        String v = CarlosProperties.getInstance().getProperty("hybrid_billing", "");
        return "on".equalsIgnoreCase(v);
    }

    private List<String> hybridProviderList() {
        List<String> out = new ArrayList<>();
        if (!isHybridBilling()) return out;
        String list = CarlosProperties.getInstance().getProperty("group_billing_providerNo", "");
        for (String pn : list.split(",")) {
            String trimmed = pn.trim();
            if (!trimmed.isEmpty()) out.add(trimmed);
        }
        return out;
    }

    private String nextBatchCount(String monthCode, String key, int curYear) {
        int max = 0;
        for (BillActivity ba : billActivityDao.findCurrentByMonthCodeAndGroupNo(
                monthCode, key, ConversionUtils.fromDateString(curYear + "-01-01"))) {
            try {
                int bc = ba.getBatchCount();
                if (bc > max) max = bc;
            } catch (NullPointerException ignore) {
                // BillActivity.batchCount returns int via Hibernate — null guard
                // for defensive parity.
            }
        }
        return String.valueOf(max + 1);
    }

    private String[] buildFilenames(String monthCode, String groupKey, String proOHIP,
                                     String batchCount, boolean isGroup) {
        int padLen = 3 - batchCount.length();
        String pad = padLen == 1 ? "0" : padLen == 2 ? "00" : "";
        String htmlFilename;
        String ohipFilename;
        if (isGroup) {
            htmlFilename = "H" + monthCode + groupKey + "_" + proOHIP + "_" + pad + batchCount + ".htm";
            ohipFilename = "H" + monthCode + groupKey + "." + pad + batchCount;
        } else {
            htmlFilename = "H" + monthCode + proOHIP + "_" + pad + batchCount + ".htm";
            ohipFilename = "H" + monthCode + proOHIP + "." + pad + batchCount;
        }
        return new String[]{ohipFilename, htmlFilename};
    }

    private void persistBillActivity(String monthCode, String batchCount, String ohipFilename,
                                      String htmlFilename, String proOHIP, String groupNo,
                                      String creator, ExtractBean extract) {
        BillActivity ba = new BillActivity();
        ba.setMonthCode(monthCode);
        ba.setBatchCount(parseIntOrZero(batchCount));
        ba.setHtmlFilename(htmlFilename);
        ba.setOhipFilename(ohipFilename);
        ba.setProviderOhipNo(proOHIP);
        ba.setGroupNo(groupNo);
        ba.setCreator(creator);
        ba.setHtmlContext(extract.getHtmlCode());
        ba.setOhipContext(extract.getValue());
        ba.setClaimRecord(extract.getOhipClaim() + "/" + extract.getOhipRecord());
        ba.setUpdateDateTime(new Date());
        ba.setStatus("A");
        ba.setTotal(extract.getTotalAmount());
        billActivityDao.persist(ba);
    }

    private static String sanitizeGroupNo(String raw) {
        return (raw == null || raw.isEmpty() || "null".equals(raw)) ? "0000" : raw;
    }

    private static String sanitizeSpecialty(String raw) {
        return (raw == null || raw.isEmpty() || "null".equals(raw)) ? "00" : raw;
    }

    private static int parseIntOrZero(String s) {
        try { return Integer.parseInt(s); } catch (NumberFormatException | NullPointerException e) { return 0; }
    }
}
