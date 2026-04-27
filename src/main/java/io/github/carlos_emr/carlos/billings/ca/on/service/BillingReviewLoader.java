/**
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.math.BigDecimal;
import java.util.Enumeration;
import java.util.List;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReviewCodeItem;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReviewPercItem;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;

@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional(readOnly = true)
public class BillingReviewLoader {
    private static final Logger _logger = MiscUtils.getLogger();

    private final BillingONClaimLoader dbObj;
    private final BillingONDiskLoader diskQueryService;
    private final BillingONLookupService lookupService;
    private final DiagnosticCodeDao diagnosticCodeDao;

    BillingReviewLoader(BillingONClaimLoader dbObj,
                      BillingONDiskLoader diskQueryService,
                      BillingONLookupService lookupService,
                      DiagnosticCodeDao diagnosticCodeDao) {
        this.dbObj = dbObj;
        this.diskQueryService = diskQueryService;
        this.lookupService = lookupService;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    public ArrayList getServiceCodeReviewVec(ArrayList vecCode, ArrayList vecUnit,
                                          ArrayList vecAt, String billReferalDate) {
        ArrayList ret = new ArrayList();
        BillingReviewCodeItem codeItem = null;

        for (int i = 0; i < vecCode.size(); i++) {
            if (((String) vecCode.get(i)).equals(""))
                continue;

            // get fee
            String fee = dbObj.getCodeFee((String) vecCode.get(i), billReferalDate);

            // get code description
            String codeDescription = dbObj.getCodeDescription((String) vecCode.get(i), billReferalDate);

            // judge fee
            if (fee == null) {
                codeItem = new BillingReviewCodeItem();
                codeItem.setCodeName((String) vecCode.get(i));
                codeItem.setCodeUnit((String) vecUnit.get(i));
                codeItem.setCodeFee("0");
                codeItem.setCodeTotal("0");
                codeItem.setMsg("<b>This code is NOT in the database!!!</b>");
                ret.add(codeItem);
                _logger
                        .error("getServiceCodeReviewVec: This code is NOT in the database! "
                                + vecCode.get(i));
                continue;
            }
            if (fee.equals("defunct")) {
                codeItem = new BillingReviewCodeItem();
                codeItem.setCodeName((String) vecCode.get(i));
                codeItem.setCodeUnit((String) vecUnit.get(i));
                codeItem.setCodeFee("0");
                codeItem.setCodeTotal("0");
                codeItem.setMsg("<b>This code has expired!!!</b>");
                ret.add(codeItem);
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

            bigFee = bigFee.setScale(2, BigDecimal.ROUND_HALF_UP);
            // bigFee = bigFee.round(new MathContext(2));
            MiscUtils.getLogger().debug("big end: " + bigFee.toString());

            codeItem = new BillingReviewCodeItem();
            codeItem.setCodeName((String) vecCode.get(i));
            codeItem.setCodeUnit((String) vecUnit.get(i));
            codeItem.setCodeAt((String) vecAt.get(i));
            codeItem.setCodeFee(fee);
            codeItem.setCodeTotal(bigFee.toString());
            codeItem.setMsg("");
            codeItem.setCodeDescription(codeDescription);
            ret.add(codeItem);
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

        // BillingReviewCodeItem codeItem = null;
        BillingReviewPercItem percItem = null;
        ArrayList vecCodeFee = new ArrayList();
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
            String fee = dbObj.getPercFee((String) vecCode.get(i), billReferalDate);

            if (fee == null) {
                percItem = new BillingReviewPercItem();
                percItem.setCodeName((String) vecCode.get(i));
                percItem.setCodeUnit((String) vecUnit.get(i));
                percItem.setCodeFee("0.00");
                percItem.setCodeMinFee("");
                percItem.setCodeMaxFee("");
                percItem.setVecCodeFee(new ArrayList());
                percItem.setVecCodeTotal(new ArrayList());
                percItem.setMsg("<b>No this perc. code in the database!!!</b>");
                ret.add(percItem);
                _logger
                        .error("getServiceCodeReviewVec: No this perc. code in the database! "
                                + vecCode.get(i));
                continue;
            }

            // calculate fee
            ArrayList vecCodeTotal = new ArrayList();
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

                bigFee = bigFee.setScale(4, BigDecimal.ROUND_HALF_UP);
                // bigFee = bigFee.round(new MathContext(2));
                vecCodeTotal.add(bigFee.toString());
            }
            // get min/max fee
            String[] mFee = dbObj.getPercMinMaxFee((String) vecCode.get(i), billReferalDate);
            percItem = new BillingReviewPercItem();
            percItem.setCodeName((String) vecCode.get(i));
            percItem.setCodeUnit((String) vecUnit.get(i));
            percItem.setCodeFee(fee);
            percItem.setCodeMinFee(mFee[0]);
            percItem.setCodeMaxFee(mFee[1]);
            percItem.setVecCodeFee(vecCodeFee);
            percItem.setVecCodeTotal(vecCodeTotal);
            percItem.setMsg("");
            ret.add(percItem);
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
