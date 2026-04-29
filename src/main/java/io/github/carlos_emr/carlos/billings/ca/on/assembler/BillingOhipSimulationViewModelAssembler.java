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
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingBatchHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOhipSimulationViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.OhipClaimFileService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.DateRange;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewQueryService;

import org.springframework.beans.factory.ObjectFactory;
/**
 * Assembles {@link BillingOhipSimulationViewModel} for
 * {@code billingOHIPsimulation.jsp}, the OHIP-extract simulation admin form.
 *
 * <p>Owns the form-defaulting logic (provider, date range, multisite flag,
 * health-office lookup) and the "Create Report" mutation branch the legacy
 * JSP performed inline. The Create Report path runs
 * {@link OhipClaimFileService} with {@code eFlag="0"} (dry run);
 * the resulting HTML preview is exposed to the JSP via the model's
 * {@code previewHtml} property.</p>
 *
 * <p>Honours the per-user privacy filter trio
 * ({@code _team_billing_only}, {@code _site_access_privacy},
 * {@code _team_access_privacy}) the legacy {@code <security:oscarSec>}
 * scriptlets evaluated. Those checks now resolve up-front in the data
 * assembler so the JSP body needs none of them.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOhipSimulationViewModelAssembler {

    private static final int PROVIDER_BILLINGNO_LENGTH = 6;
    private static final int PROVIDER_SPECIALTYCODE_LENGTH = 2;
    private static final int PROVIDER_GROUPNO_LENGTH = 4;

    private final BillingReviewQueryService reviewPrep;
    private final BillingOnLookupService lookupService;
    private final ObjectFactory<OhipClaimFileService> ohipClaimFileFactory;

    /** Production constructor — Struts no-arg shape. */
    public BillingOhipSimulationViewModelAssembler(BillingReviewQueryService reviewPrep,
                                       BillingOnLookupService lookupService,
                                       ObjectFactory<OhipClaimFileService> ohipClaimFileFactory) {
        this.reviewPrep = reviewPrep;
        this.lookupService = lookupService;
        this.ohipClaimFileFactory = ohipClaimFileFactory;
    }

    /** Build the simulation view model. */
    public BillingOhipSimulationViewModel assemble(HttpServletRequest request,
                                                   LoggedInInfo loggedInInfo,
                                                   boolean teamBillingOnly,
                                                   boolean siteAccessPrivacy,
                                                   boolean teamAccessPrivacy) {
        String userNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        boolean bMultisites = IsPropertiesOn.isMultisitesEnable();

        GregorianCalendar now = new GregorianCalendar();
        int curMonth = now.get(Calendar.MONTH) + 1;
        String nowDate = UtilDateUtilities.DateToString(new java.util.Date());
        String monthCode = BillingOnConstants.propMonthCode.getProperty(String.valueOf(curMonth));

        CarlosProperties props = CarlosProperties.getInstance();
        String billCenter = props.getProperty("billcenter", "").trim();
        String healthOffice = BillingOnConstants.propBillingCenter.getProperty(billCenter);

        boolean summaryView = "on".equals(request.getParameter("summaryView"));
        String providerView = request.getParameter("providers") == null
                ? userNo : request.getParameter("providers");
        String xmlVdate = request.getParameter("xml_vdate") == null
                ? "" : request.getParameter("xml_vdate");
        String xmlApptDate = request.getParameter("xml_appointment_date") == null
                ? nowDate : request.getParameter("xml_appointment_date");

        // Provider dropdown (filtered by privacy trio).
        List<BillingOhipSimulationViewModel.ProviderOption> providers = loadProviders(
                userNo, teamBillingOnly, siteAccessPrivacy, teamAccessPrivacy);

        String previewHtml = "";
        if ("Create Report".equals(request.getParameter("submit"))) {
            previewHtml = bMultisites
                    ? buildMultisitePreview(request, loggedInInfo)
                    : buildSoloPreview(request, loggedInInfo, userNo,
                            teamBillingOnly, siteAccessPrivacy, teamAccessPrivacy,
                            summaryView);
        }

        return BillingOhipSimulationViewModel.builder()
                .multisites(bMultisites)
                .billCenter(billCenter)
                .healthOffice(healthOffice)
                .monthCode(monthCode)
                .nowDate(nowDate)
                .userNo(userNo)
                .providerView(providerView)
                .startDate(xmlVdate)
                .endDate(xmlApptDate)
                .summaryView(summaryView)
                .providers(providers)
                .previewHtml(previewHtml)
                .build();
    }

    private List<BillingOhipSimulationViewModel.ProviderOption> loadProviders(
            String userNo, boolean teamBillingOnly, boolean siteAccessPrivacy,
            boolean teamAccessPrivacy) {
        @SuppressWarnings("rawtypes")
        List providerStr;
        if (teamBillingOnly || teamAccessPrivacy) {
            providerStr = reviewPrep.getTeamProviderBillingStr(userNo);
        } else if (siteAccessPrivacy) {
            providerStr = reviewPrep.getSiteProviderBillingStr(userNo);
        } else {
            providerStr = reviewPrep.getProviderBillingStr();
        }
        List<BillingOhipSimulationViewModel.ProviderOption> out = new ArrayList<>();
        for (Object o : providerStr) {
            String[] temp = ((String) o).split("\\|");
            String providerNo = temp.length > 0 ? temp[0] : "";
            String last = temp.length > 1 ? temp[1] : "";
            String first = temp.length > 2 ? temp[2] : "";
            out.add(new BillingOhipSimulationViewModel.ProviderOption(
                    providerNo, last, first));
        }
        return out;
    }

    private String buildMultisitePreview(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String pro = request.getParameter("providers");
        StringBuilder errorMsg = new StringBuilder();
        DateRange dateRange = resolveDateRange(request);

        var proObj = lookupService.getProviderObj(pro);
        if (proObj.getOhipNo().length() != PROVIDER_BILLINGNO_LENGTH) {
            errorMsg.append(formatErrorLine("The providers's billing code is not correct!"));
        }
        String proOHIP = proObj.getOhipNo();
        String groupNo = proObj.getBillingGroupNo();
        String specialty = proObj.getSpecialtyCode();
        if (specialty.length() != PROVIDER_SPECIALTYCODE_LENGTH) {
            errorMsg.append(formatErrorLine("The providers's specialty code is not correct!"));
            specialty = "00";
        }
        if (groupNo.length() != PROVIDER_GROUPNO_LENGTH) {
            errorMsg.append(formatErrorLine("The providers's group no is not correct!"));
            groupNo = "0000";
        }

        OhipClaimFileService dbObj = ohipClaimFileFactory.getObject();
        dbObj.setContextPath(request.getContextPath());
        dbObj.setEFlag("0");
        dbObj.setDateRange(dateRange);
        dbObj.setProviderNo(pro);
        BillingBatchHeaderDto bhObj = new BillingBatchHeaderDto();
        bhObj.setSpec_id("   ");
        bhObj.setMoh_office(" ");
        bhObj.setGroup_num(groupNo);
        bhObj.setProvider_reg_num(proOHIP);
        bhObj.setSpecialty(specialty);
        dbObj.setBatchHeaderObj(bhObj);
        dbObj.createSiteBillingFileStr(loggedInInfo, "0", new String[]{"O", "W", "I"});

        return new StringBuilder()
                .append("<font color='red'>").append(errorMsg).append("</font>")
                .append(dbObj.getHtmlValue()).toString();
    }

    private String buildSoloPreview(HttpServletRequest request, LoggedInInfo loggedInInfo,
                                    String userNo, boolean teamBillingOnly,
                                    boolean siteAccessPrivacy, boolean teamAccessPrivacy,
                                    boolean summaryView) {
        String pro = request.getParameter("providers");
        List<String> providerList = new ArrayList<>();
        if ("all".equals(pro)) {
            @SuppressWarnings("rawtypes")
            List providerStr;
            if (teamBillingOnly || teamAccessPrivacy) {
                providerStr = reviewPrep.getTeamProviderBillingStr(userNo);
            } else if (siteAccessPrivacy) {
                providerStr = reviewPrep.getSiteProviderBillingStr(userNo);
            } else {
                providerStr = reviewPrep.getProviderBillingStr();
            }
            for (Object s : providerStr) {
                providerList.add(((String) s).split("\\|")[0]);
            }
        } else {
            providerList.add(pro);
        }

        BigDecimal bigTotal = BillingMoney.zero();
        int recordCount = 0;
        int errorCount = 0;
        StringBuilder htmlValue = new StringBuilder();

        for (String provider : providerList) {
            StringBuilder errorMsg = new StringBuilder();
            var proObj = lookupService.getProviderObj(provider);
            if (proObj.getOhipNo().length() != PROVIDER_BILLINGNO_LENGTH) {
                errorMsg.append(formatErrorLine("The billing code (" + proObj.getOhipNo()
                        + ") for providers (" + provider + ") is not correct!"));
            }
            String proOHIP = proObj.getOhipNo();
            String groupNo = proObj.getBillingGroupNo();
            String specialty = proObj.getSpecialtyCode();
            DateRange dateRange = resolveDateRange(request);

            if (specialty.length() != PROVIDER_SPECIALTYCODE_LENGTH) {
                errorMsg.append(formatErrorLine("The specialty code (" + specialty
                        + ") for providers (" + provider + ") is not correct!"));
                specialty = "00";
            }
            if (groupNo.length() != PROVIDER_GROUPNO_LENGTH) {
                errorMsg.append(formatErrorLine("The group no (" + groupNo
                        + ") for providers (" + provider + ") is not correct!"));
                groupNo = "0000";
            }

            OhipClaimFileService dbObj = ohipClaimFileFactory.getObject();
            dbObj.setContextPath(request.getContextPath());
            dbObj.setEFlag("0");
            dbObj.setDateRange(dateRange);
            dbObj.setProviderNo(provider);
            BillingBatchHeaderDto bhObj = new BillingBatchHeaderDto();
            bhObj.setSpec_id("   ");
            bhObj.setMoh_office(" ");
            bhObj.setGroup_num(groupNo);
            bhObj.setProvider_reg_num(proOHIP);
            bhObj.setSpecialty(specialty);
            dbObj.setBatchHeaderObj(bhObj);
            dbObj.errorMsg += errorMsg.toString();

            dbObj.createBillingFileStr(loggedInInfo, "0",
                    new String[]{"O", "W", "I"}, true, null, summaryView);
            if (dbObj.getRecordCount() > 0
                    || !"".equals(dbObj.errorMsg)
                    || !"".equals(dbObj.errorFatalMsg)) {
                recordCount += dbObj.getRecordCount();
                bigTotal = bigTotal.add(dbObj.getBigTotal());
                htmlValue.append(dbObj.getHtmlValue());
            }
            errorCount += "".equals(dbObj.errorMsg) ? 0 : dbObj.errorMsg.split("<br>").length;
            errorCount += "".equals(dbObj.errorFatalMsg)
                    ? 0 : dbObj.errorFatalMsg.split("<br>").length;
            dbObj.errorMsg = "";
        }

        // Wrap into the legacy results table.
        String billingTable = htmlValue.toString();
        StringBuilder result = new StringBuilder();
        result.append("\n<table class='table table-hover table-sm'>\n<thead>");
        if (summaryView) {
            result.append("\n<tr><th >OHIP NO</th><th >Number of Records</th>")
                    .append("<th >Total Billed</th><th colspan='9'></th></tr></thead>");
        } else {
            result.append("<tr><th >OHIP NO</th><th >Acct NO</th>")
                    .append("<th >Name</th><th >RO</th><th >DOB</th><th >Sex</th>")
                    .append("<th >Health #</th><th >Billdate</th><th >Code</th>")
                    .append("<th >Billed</th><th >DX</th>")
                    .append("<th align='right' >Comment</th></tr></thead>");
        }
        result.append("<tbody>").append(billingTable);
        result.append("\n<tr><td colspan='12' >&nbsp;</td></tr><tr><td colspan='4'>")
                .append(recordCount).append(" RECORDS PROCESSED, ")
                .append(errorCount).append(" ERROR")
                .append(errorCount > 1 ? "S" : "")
                .append("</td><td colspan='8'>TOTAL: ")
                .append(bigTotal.toString())
                .append("\n</td></tr>");
        result.append("</tbody></table>");
        return result.toString();
    }

    private DateRange resolveDateRange(HttpServletRequest request) {
        String dateBegin = request.getParameter("xml_vdate");
        String dateEnd = request.getParameter("xml_appointment_date");
        if (dateEnd == null || "".equals(dateEnd)) {
            dateEnd = request.getParameter("curDate");
        }
        if (dateBegin == null || "".equals(dateBegin)) {
            return new DateRange(null, ConversionUtils.fromDateString(dateEnd));
        }
        return new DateRange(
                ConversionUtils.fromDateString(dateBegin),
                ConversionUtils.fromDateString(dateEnd));
    }

    static String formatErrorLine(String message) {
        return SafeEncode.forHtml(message) + "<br>";
    }
}
