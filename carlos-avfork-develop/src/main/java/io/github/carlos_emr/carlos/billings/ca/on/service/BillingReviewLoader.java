/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingReviewServiceParam;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewCodeItem;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewPercentageItem;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONItem;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;

/**
 * Read-only queries that back the bill-review screen: service-code and
 * percentage-code review rows, the MRI list, the provider dropdown sets
 * (own / team / site), and diagnostic-code descriptions. Composes
 * {@link BillingOnClaimLoader}, {@link BillingOnDiskLoader}, and
 * {@link BillingOnLookupService} under one query-side surface so the
 * action / assembler tier doesn't have to wire them up individually.
 *
 * <p>Web security is enforced at the action layer before invocation.</p>
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingReviewLoader {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingOnClaimLoader claimLoader;
    private final BillingOnDiskLoader diskQueryService;
    private final BillingOnLookupService lookupService;
    private final DiagnosticCodeDao diagnosticCodeDao;

    BillingReviewLoader(BillingOnClaimLoader claimLoader,
                      BillingOnDiskLoader diskQueryService,
                      BillingOnLookupService lookupService,
                      DiagnosticCodeDao diagnosticCodeDao) {
        this.claimLoader = claimLoader;
        this.diskQueryService = diskQueryService;
        this.lookupService = lookupService;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    public List<BillingReviewCodeItem> getServiceCodeReviewItems(List<BillingReviewServiceParam> rows,
                                                                  String billReferalDate) {
        List<BillingReviewCodeItem> ret = new ArrayList<>();
        for (BillingReviewServiceParam row : rows) {
            if (row.code().isEmpty())
                continue;

            // get fee
            BillingOnClaimLoader.FeeLookupResult feeResult =
                    claimLoader.getCodeFeeResult(row.code(), billReferalDate);
            String fee = feeResult.value();

            // get code description
            String codeDescription = claimLoader.getCodeDescription(row.code(), billReferalDate);

            // judge fee
            if (feeResult.partial()) {
                ret.add(new BillingReviewCodeItem(
                        row.code(),
                        row.unit(),
                        "0",
                        "0",
                        "",
                        "<b>Fee lookup failed; totals may be incomplete.</b>",
                        codeDescription));
                continue;
            }
            if (fee == null) {
                ret.add(new BillingReviewCodeItem(
                        row.code(),
                        row.unit(),
                        "0",
                        "0",
                        "",
                        "<b>This code is NOT in the database!!!</b>",
                        ""));
                _logger
                        .error("getServiceCodeReviewItems: This code is NOT in the database! "
                                + row.code());
                continue;
            }
            if (BillingONItem.DEFUNCT_FEE.equals(fee)) {
                ret.add(new BillingReviewCodeItem(
                        row.code(),
                        row.unit(),
                        "0",
                        "0",
                        "",
                        "<b>This code has expired!!!</b>",
                        ""));
                _logger
                        .error("getServiceCodeReviewItems: This code has expired! "
                                + row.code());
                continue;
            }
            // if perc. code (this code is not added to the list of billing codes)
            if (fee.equals(".00") || fee.equals(""))
                continue;

            // calculate fee
            BigDecimal bigCodeFee = new BigDecimal(fee);
            BigDecimal bigCodeUnit = new BigDecimal(row.unit());
            BigDecimal bigCodeAt = new BigDecimal(row.servicedAt());

            BigDecimal bigFee = bigCodeFee.multiply(bigCodeUnit);

            bigFee = bigFee.multiply(bigCodeAt);

            bigFee = bigFee.setScale(2, RoundingMode.HALF_UP);
            MiscUtils.getLogger().debug("big end: " + bigFee.toString());

            ret.add(new BillingReviewCodeItem(
                    row.code(),
                    row.unit(),
                    fee,
                    bigFee.toString(),
                    row.servicedAt(),
                    "",
                    codeDescription));
        }
        return ret;
    }

    // get perc code item display
    public List<BillingReviewPercentageItem> getPercentageCodeReviewItems(List<BillingReviewServiceParam> rows,
                                                                          List<BillingReviewCodeItem> codeItems,
                                                                          String billReferalDate) {
        List<BillingReviewPercentageItem> ret = new ArrayList<>();
        // no perc. code (perc codes are recognized in the database by having a value of .00, they are not in the codeItems)
        if (rows.size() == codeItems.size())
            return ret;

        List<String> codeFees = new ArrayList<>();
        for (BillingReviewCodeItem item : codeItems) {
            codeFees.add(item.getCodeFee());
        }
        for (int i = 0; i < rows.size(); i++) {   //LOOP thru all the billing codes from for submission
            BillingReviewServiceParam row = rows.get(i);
            if (row.code().isEmpty())
                continue;

            // not perc. code
            if (i < codeItems.size()
                    && row.code().equals(codeItems.get(i).getCodeName())) {
                continue;
            }
            // take perc. code
            // get fee
            BillingOnClaimLoader.FeeLookupResult feeResult =
                    claimLoader.getPercFeeResult(row.code(), billReferalDate);
            String fee = feeResult.value();

            if (feeResult.partial()) {
                ret.add(new BillingReviewPercentageItem(
                        row.code(),
                        row.unit(),
                        "0.00",
                        "",
                        "",
                        java.util.List.of(),
                        java.util.List.of(),
                        "<b>Percentage lookup failed; totals may be incomplete.</b>"));
                continue;
            }
            if (fee == null) {
                ret.add(new BillingReviewPercentageItem(
                        row.code(),
                        row.unit(),
                        "0.00",
                        "",
                        "",
                        java.util.List.of(),
                        java.util.List.of(),
                        "<b>No this perc. code in the database!!!</b>"));
                _logger
                        .error("getPercentageCodeReviewItems: No this perc. code in the database! "
                                + row.code());
                continue;
            }

            // calculate fee
            List<String> codeTotals = new ArrayList<>();
            for (String codeFee : codeFees) {
                BigDecimal bigCodeFee = new BigDecimal(codeFee);

                if (fee.equals(".00") || fee.equals("")) {
                    continue;
                }

                BigDecimal bigFee = bigCodeFee.multiply(new BigDecimal(fee));

                bigFee = bigFee.setScale(4, RoundingMode.HALF_UP);
                codeTotals.add(bigFee.toString());
            }
            // get min/max fee
            BillingOnClaimLoader.FeeRangeLookupResult mFee =
                    claimLoader.getPercMinMaxFeeResult(row.code(), billReferalDate);
            ret.add(new BillingReviewPercentageItem(
                    row.code(),
                    row.unit(),
                    fee,
                    mFee.min(),
                    mFee.max(),
                    codeFees,
                    codeTotals,
                    mFee.partial() ? "<b>Percentage min/max lookup failed; limits may be incomplete.</b>" : ""));
        }
        return ret;
    }

    public List<BillingReviewServiceParam> getRequestCodes(HttpServletRequest requestData,
                                                            String paramNameCode, String paramNameUnit, String paramNameAt,
                                                            int numItem) {
        List<BillingReviewServiceParam> ret = new ArrayList<>();
        for (int i = 0; i < numItem; i++) {
            // Skip both null (param absent from form) and "" (param sent
            // empty). The legacy code only checked "" via "".equals(...),
            // which returns false for null and let null leak into the
            // returned list — TreeMap.put downstream then NPE'd.
            String code = requestData.getParameter(paramNameCode + i);
            if (code == null || code.isEmpty()) continue;
            ret.add(new BillingReviewServiceParam(
                    code,
                    defaultParamValue(requestData.getParameter(paramNameUnit + i)),
                    defaultParamValue(requestData.getParameter(paramNameAt + i))));
        }
        return ret;
    }

    // pulls codes from form checkbox params (no sequence order — should
    // change to col1, col2, col3 scan to get a sequence order).
    public List<BillingReviewServiceParam> getRequestFormCodes(HttpServletRequest requestData,
                                                                String paramNameCode, String paramNameUnit, String paramNameAt) {
        List<BillingReviewServiceParam> ret = new ArrayList<>();
        for (Enumeration<?> e = requestData.getParameterNames(); e.hasMoreElements(); ) {
            String temp = e.nextElement().toString();
            if (temp.startsWith(paramNameCode)
                    && (temp.length() == 9 || temp.startsWith(paramNameCode + "_"))
                    && !temp.equals("xml_vdate")) {
                ret.add(new BillingReviewServiceParam(
                        temp.substring(4),
                        defaultParamValue(paramNameUnit),
                        defaultParamValue(paramNameAt)));
            }
        }
        return ret;
    }

    public List getMRIList(String sDate, String eDate, String status) {
        return diskQueryService.getMRIList(sDate, eDate, status);
    }

    public List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> getProviderBillingStr() {
        return lookupService.getCurProviderStr();
    }

    public List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> getTeamProviderBillingStr(String provider_no) {
        return lookupService.getCurTeamProviderStr(provider_no);
    }

    public List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> getSiteProviderBillingStr(String provider_no) {
        return lookupService.getCurSiteProviderStr(provider_no);
    }

    // default value to 1 if it is empty
    private String defaultParamValue(String val) {
        String ret = "1";
        if (val != null && !val.equals("")) {
            ret = val;
        }

        return ret;
    }

    /**
     * Returns the description for a diagnostic code, or empty string if the
     * code is not on file. Iterates results to mirror legacy behavior of
     * returning the last hit when multiple rows share the same code.
     */
    public String getDxDescription(String val) {
        String ret = "";
        for (DiagnosticCode dcode : diagnosticCodeDao.findByDiagnosticCode(val)) {
            ret = dcode.getDescription();
        }
        return ret;
    }

}
