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
public class BillingReviewQueryService {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingOnClaimLoader dbObj;
    private final BillingOnDiskLoader diskQueryService;
    private final BillingOnLookupService lookupService;
    private final DiagnosticCodeDao diagnosticCodeDao;

    BillingReviewQueryService(BillingOnClaimLoader dbObj,
                      BillingOnDiskLoader diskQueryService,
                      BillingOnLookupService lookupService,
                      DiagnosticCodeDao diagnosticCodeDao) {
        this.dbObj = dbObj;
        this.diskQueryService = diskQueryService;
        this.lookupService = lookupService;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    public ArrayList getServiceCodeReviewVec(ArrayList vecCode, ArrayList vecUnit,
                                          ArrayList vecAt, String billReferalDate) {
        ArrayList ret = new ArrayList();
        for (int i = 0; i < vecCode.size(); i++) {
            if (((String) vecCode.get(i)).equals(""))
                continue;

            // get fee
            BillingOnClaimLoader.FeeLookupResult feeResult =
                    dbObj.getCodeFeeResult((String) vecCode.get(i), billReferalDate);
            String fee = feeResult.value();

            // get code description
            String codeDescription = dbObj.getCodeDescription((String) vecCode.get(i), billReferalDate);

            // judge fee
            if (feeResult.partial()) {
                ret.add(new BillingReviewCodeItem(
                        (String) vecCode.get(i),
                        (String) vecUnit.get(i),
                        "0",
                        "0",
                        "",
                        "<b>Fee lookup failed; totals may be incomplete.</b>",
                        codeDescription));
                continue;
            }
            if (fee == null) {
                ret.add(new BillingReviewCodeItem(
                        (String) vecCode.get(i),
                        (String) vecUnit.get(i),
                        "0",
                        "0",
                        "",
                        "<b>This code is NOT in the database!!!</b>",
                        ""));
                _logger
                        .error("getServiceCodeReviewVec: This code is NOT in the database! "
                                + vecCode.get(i));
                continue;
            }
            if (BillingONItem.DEFUNCT_FEE.equals(fee)) {
                ret.add(new BillingReviewCodeItem(
                        (String) vecCode.get(i),
                        (String) vecUnit.get(i),
                        "0",
                        "0",
                        "",
                        "<b>This code has expired!!!</b>",
                        ""));
                _logger
                        .error("getServiceCodeReviewVec: This code has expired! "
                                + vecCode.get(i));
                continue;
            }
            // if perc. code ( This code is not added to the vector of billing codes)
            if (fee.equals(".00") || fee.equals(""))
                continue;

            // calculate fee
            BigDecimal bigCodeFee = new BigDecimal(fee);
            BigDecimal bigCodeUnit = new BigDecimal((String) vecUnit.get(i));
            BigDecimal bigCodeAt = new BigDecimal((String) vecAt.get(i));

            BigDecimal bigFee = bigCodeFee.multiply(bigCodeUnit);

            bigFee = bigFee.multiply(bigCodeAt);

            bigFee = bigFee.setScale(2, RoundingMode.HALF_UP);
            MiscUtils.getLogger().debug("big end: " + bigFee.toString());

            ret.add(new BillingReviewCodeItem(
                    (String) vecCode.get(i),
                    (String) vecUnit.get(i),
                    fee,
                    bigFee.toString(),
                    (String) vecAt.get(i),
                    "",
                    codeDescription));
        }
        return ret;
    }

    // get perc code item display
    public ArrayList getPercCodeReviewVec(ArrayList vecCode, ArrayList vecUnit,
                                       ArrayList vecReviewCodeItem, String billReferalDate) {
        ArrayList ret = new ArrayList();
        // no perc. code  ( perc codes are recognized in the database by having a value of .00, They are not in the vecReviewCodeItem)
        if (vecCode.size() == vecReviewCodeItem.size())
            return ret;

        ArrayList<String> vecCodeFee = new ArrayList<String>();
        for (int i = 0; i < vecReviewCodeItem.size(); i++) {
            vecCodeFee.add(((BillingReviewCodeItem) vecReviewCodeItem.get(i))
                    .getCodeFee());
        }
        for (int i = 0; i < vecCode.size(); i++) {   //LOOP thru all the billing codes from for submission
            if (((String) vecCode.get(i)).equals(""))
                continue;

            // not perc. code
            if (i < vecReviewCodeItem.size()
                    && ((String) vecCode.get(i))
                    .equals(((BillingReviewCodeItem) vecReviewCodeItem
                            .get(i)).getCodeName())) {
                continue;
            }
            // take perc. code
            // get fee
            BillingOnClaimLoader.FeeLookupResult feeResult =
                    dbObj.getPercFeeResult((String) vecCode.get(i), billReferalDate);
            String fee = feeResult.value();

            if (feeResult.partial()) {
                ret.add(new BillingReviewPercentageItem(
                        (String) vecCode.get(i),
                        (String) vecUnit.get(i),
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
                        (String) vecCode.get(i),
                        (String) vecUnit.get(i),
                        "0.00",
                        "",
                        "",
                        java.util.List.of(),
                        java.util.List.of(),
                        "<b>No this perc. code in the database!!!</b>"));
                _logger
                        .error("getServiceCodeReviewVec: No this perc. code in the database! "
                                + vecCode.get(i));
                continue;
            }

            // calculate fee
            ArrayList<String> vecCodeTotal = new ArrayList<String>();
            for (int j = 0; j < vecCodeFee.size(); j++) {
                BigDecimal bigCodeFee = new BigDecimal((String) vecCodeFee
                        .get(j));
                // BigDecimal bigCodeUnit = new BigDecimal((String)
                // vecUnit.get(i));
                // BigDecimal bigCodeAt = new BigDecimal((String) vecAt.get(i));


                if (fee.equals(".00") || fee.equals("")) {
                    continue;
                }

                BigDecimal bigFee = bigCodeFee.multiply(new BigDecimal(fee));

                // bigFee = bigFee.multiply(bigCodeAt);

                bigFee = bigFee.setScale(4, RoundingMode.HALF_UP);
                vecCodeTotal.add(bigFee.toString());
            }
            // get min/max fee
            BillingOnClaimLoader.FeeRangeLookupResult mFee =
                    dbObj.getPercMinMaxFeeResult((String) vecCode.get(i), billReferalDate);
            ret.add(new BillingReviewPercentageItem(
                    (String) vecCode.get(i),
                    (String) vecUnit.get(i),
                    fee,
                    mFee.min(),
                    mFee.max(),
                    vecCodeFee,
                    vecCodeTotal,
                    mFee.partial() ? "<b>Percentage min/max lookup failed; limits may be incomplete.</b>" : ""));
        }
        return ret;
    }

    // ret[0],[1],[2] - ArrayList vecCode, ArrayList vecUnit, ArrayList vecAt
    public ArrayList<String>[] getRequestCodeVec(HttpServletRequest requestData,
                                              String paramNameCode, String paramNameUnit, String paramNameAt,
                                              int numItem) {
        @SuppressWarnings("unchecked")
        ArrayList<String>[] ret = new ArrayList[3];
        ret[0] = new ArrayList<String>();
        ret[1] = new ArrayList<String>();
        ret[2] = new ArrayList<String>();

        for (int i = 0; i < numItem; i++) {
            // Skip both null (param absent from form) and "" (param sent
            // empty). The legacy code only checked "" via "".equals(...),
            // which returns false for null and let null leak into the
            // returned ArrayList — TreeMap.put downstream then NPE'd.
            String code = requestData.getParameter(paramNameCode + i);
            if (code == null || code.isEmpty()) continue;
            ret[0].add(code);
            ret[1].add(defaultParamValue(requestData.getParameter(paramNameUnit
                    + i)));
            ret[2].add(defaultParamValue(requestData.getParameter(paramNameAt
                    + i)));
        }
        return ret;
    }

    // ret[0],[1],[2] - ArrayList vecCode, ArrayList vecUnit, ArrayList vecAt - from form
    // checkbox
    // this way for no sequence order
    // should change to col1, col2, col3 scan and get a sequence order
    public ArrayList<String>[] getRequestFormCodeVec(HttpServletRequest requestData,
                                                  String paramNameCode, String paramNameUnit, String paramNameAt) {
        @SuppressWarnings("unchecked")
        ArrayList<String>[] ret = new ArrayList[3];
        ret[0] = new ArrayList<String>();
        ret[1] = new ArrayList<String>();
        ret[2] = new ArrayList<String>();

        for (Enumeration e = requestData.getParameterNames(); e
                .hasMoreElements(); ) {
            String temp = e.nextElement().toString();
            if (temp.startsWith(paramNameCode)
                    && (temp.length() == 9 || temp.startsWith(paramNameCode
                    + "_")) && !temp.equals("xml_vdate")) {
                // _logger.info(requestData.getParameter(temp) +
                // "getRequestFormCodeVec:" + temp);
                ret[0].add(temp.substring(4));
                ret[1].add(defaultParamValue(paramNameUnit));
                ret[2].add(defaultParamValue(paramNameAt));
            }
        }
        return ret;
    }

    public List getMRIList(String sDate, String eDate, String status) {
        return diskQueryService.getMRIList(sDate, eDate, status);
    }

    // ret - ArrayList = || ||
    public List getProviderBillingStr() {
        return lookupService.getCurProviderStr();
    }

    public List getTeamProviderBillingStr(String provider_no) {
        return lookupService.getCurTeamProviderStr(provider_no);
    }

    public List getSiteProviderBillingStr(String provider_no) {
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
