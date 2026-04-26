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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Enumeration;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import io.github.carlos_emr.BillingBean;
import io.github.carlos_emr.BillingDataBean;
import io.github.carlos_emr.BillingItemBean;
import io.github.carlos_emr.BillingPatientDataBean;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.dbBillingData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Mutation gate for the Ontario billing-correction review session-staging
 * step. Enforces {@code _billing} {@code w} privilege AND POST-only,
 * builds a {@link BillingBean} (and the matching {@link BillingDataBean} /
 * {@link BillingPatientDataBean}) from the submitted form parameters,
 * stashes them on the session under the legacy keys, and redirects to
 * {@code /billing/CA/ON/BillingCorrectionReview}.
 *
 * <p>Migrated from the heavy scriptlet body of {@code billingCorrectionValid.jsp}.
 * The legacy JSP was a "view" that performed mutation and a redirect;
 * moving the logic here lets the JSP body be pure EL/JSTL (currently no
 * body content is rendered — the action sends a redirect before any view
 * renders, so the JSP only acts as a struts result for the
 * defensive-fallback case).</p>
 *
 * <p>Pricing rules and corner-case branches are preserved verbatim from
 * the legacy JSP, including the unusual {@code .compareTo(".00") == 0}
 * dollar-amount check, the no-decimal-string total accumulator, and the
 * three premium codes ({@code E411A} et al.) the JSP special-cased.</p>
 *
 * @since 2026-04-25
 */
public final class BillingCorrectionValid2Action extends ActionSupport {

    private SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        try {
            buildSessionBeansAndRedirect(request, response);
            // Redirect issued; no view to render.
            return NONE;
        } catch (ArrayIndexOutOfBoundsException ignored) {
            // Legacy JSP swallowed this exception silently.
            return NONE;
        }
    }

    /**
     * Build the session beans and redirect to BillingCorrectionReview.
     * Mirrors the legacy JSP logic line-for-line; long because it has to.
     */
    private void buildSessionBeansAndRedirect(HttpServletRequest request,
                                              HttpServletResponse response) throws Exception {
        BigDecimal billingunit = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal percent;
        BigDecimal percentPremium = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal bigTotal = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        BigDecimal xpercent;
        BigDecimal xpercentPremium = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);

        String numCode;
        String diagcode;
        String diagnostic_code = request.getParameter("xml_diagnostic_detail");
        String r_doctor = request.getParameter("rd");
        String roster_status = request.getParameter("roster") == null ? "" : request.getParameter("roster");
        String m_review = request.getParameter("m_review") == null ? "" : "checked";
        String r_doctor_ohip = request.getParameter("rdohip");
        String r_status = request.getParameter("referral") == null ? "" : "checked";
        String hcType = request.getParameter("hc_type");
        String hcSex = request.getParameter("hc_sex");
        String specialty = request.getParameter("specialty");

        StringBuilder content = new StringBuilder();
        content.append("<rdohip>").append(r_doctor_ohip).append("</rdohip>")
                .append("<rd>").append(r_doctor).append("</rd>");
        content.append("<xml_referral>").append(r_status).append("</xml_referral>")
                .append("<mreview>").append(m_review).append("</mreview>");
        content.append("<hctype>").append(hcType).append("</hctype>")
                .append("<demosex>").append(hcSex).append("</demosex>");
        content.append("<specialty>").append(specialty).append("</specialty>");
        content.append("<xml_roster>").append(roster_status).append("</xml_roster>");

        if (diagnostic_code == null || diagnostic_code.compareTo("") == 0) {
            diagnostic_code = "000|Other code";
            diagcode = "000";
        } else {
            diagcode = diagnostic_code.substring(0, 3);
            numCode = "";
            for (int i = 0; i < diagcode.length(); i++) {
                String c = diagcode.substring(i, i + 1);
                if (c.hashCode() >= 48 && c.hashCode() <= 58) {
                    numCode += c;
                }
            }
            if (numCode.length() < 3) {
                diagnostic_code = "000|Other code";
                diagcode = "000";
            }
        }

        String pValue = "0";
        String pCode = "";
        String pDesc = "";
        String pPerc = "";
        String pUnit = "";
        String eValue = "";
        String eCode = "";
        String eDesc = "";
        String ePerc = "";
        String eUnit = "";
        String xValue = "";
        String xCode = "";
        String xDesc = "";
        String xPerc = "";
        String xUnit = "";
        String eFlag = "";
        String xFlag = "";
        String[] strAuth;

        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String temp = e.nextElement();
            if (temp.indexOf("xml_") == -1) continue;
            content.append("<").append(temp).append(">")
                    .append(SxmlMisc.replaceHTMLContent(request.getParameter(temp)))
                    .append("</").append(temp).append(">");
        }

        BigDecimal pValue1 = new BigDecimal(0).setScale(2, RoundingMode.HALF_UP);
        BillingBean billing = new BillingBean();
        dbBillingData dbBillingDataBean = new dbBillingData();

        Enumeration<String> f = request.getParameterNames();
        while (f.hasMoreElements()) {
            String tempBill = f.nextElement();
            if (tempBill.indexOf("servicecode") == -1) continue;

            String billunit = request.getParameter("billingunit" + tempBill.substring(11));
            String billingamount = request.getParameter("billingamount" + tempBill.substring(11));
            dbBillingDataBean.setService_code(request.getParameter(tempBill));

            strAuth = dbBillingDataBean.ejbLoad();
            if (strAuth != null) {
                String scode = strAuth[0];
                String desc = strAuth[1];
                String value = strAuth[2];
                String percentage = strAuth[3];
                if (billingamount != null && billingamount.compareTo("") != 0) {
                    value = billingamount;
                }

                BigDecimal otherunit2 = new BigDecimal(Double.parseDouble(value)).setScale(2, RoundingMode.HALF_UP);
                billingunit = new BigDecimal(Double.parseDouble(billunit)).setScale(2, RoundingMode.HALF_UP);
                otherunit2 = billingunit.multiply(otherunit2).setScale(2, RoundingMode.HALF_UP);

                if (isPremiumServiceCode(scode)) {
                    pValue1 = pValue1.add(otherunit2);
                    pCode = scode;
                    pValue = pValue1.toString();
                    pPerc = percentage;
                    pDesc = desc;
                    pUnit = billunit;
                    if (eCode.compareTo("") != 0) {
                        eFlag = "1";
                    }
                }

                if (value.compareTo(".00") == 0) {
                    if (scode.compareTo("E411A") == 0) {
                        eCode = scode;
                        eDesc = desc;
                        eValue = value;
                        ePerc = percentage;
                        eUnit = billunit;
                        eFlag = "1";
                    } else {
                        xCode = scode;
                        xDesc = desc;
                        xValue = value;
                        xPerc = percentage;
                        xUnit = billunit;
                        xFlag = "1";
                    }
                } else {
                    bigTotal = bigTotal.add(otherunit2);
                    String otherstr2 = stripDecimalPoint(otherunit2.toString());

                    BillingItemBean billingItem = new BillingItemBean();
                    billingItem.setService_code(scode);
                    billingItem.setDesc(desc);
                    billingItem.setService_value(otherstr2);
                    billingItem.setPercentage(percentage);
                    billingItem.setDiag_code(diagcode);
                    billingItem.setQuantity(billunit);
                    billing.addBillingItem(billingItem);
                }
            }
        }

        if (eFlag.compareTo("1") == 0) {
            BigDecimal ecodeunit = new BigDecimal(Double.parseDouble(pValue)).setScale(2, RoundingMode.HALF_UP);
            percent = new BigDecimal(ePerc).setScale(4, RoundingMode.HALF_UP);
            percentPremium = ecodeunit.multiply(percent).setScale(2, RoundingMode.HALF_UP);

            bigTotal = bigTotal.add(percentPremium);
            String otherstr2 = stripDecimalPoint(percentPremium.toString());

            BillingItemBean billingItem = new BillingItemBean();
            billingItem.setService_code(eCode);
            billingItem.setDesc(eDesc);
            billingItem.setService_value(otherstr2);
            billingItem.setPercentage(ePerc);
            billingItem.setDiag_code(diagcode);
            billingItem.setQuantity(eUnit);
            billing.addBillingItem(billingItem);
        }

        if (xFlag.compareTo("1") == 0) {
            BigDecimal xcodeunit = new BigDecimal(Double.parseDouble(pValue)).setScale(4, RoundingMode.HALF_UP);

            bigTotal = bigTotal.subtract(percentPremium);
            xpercent = new BigDecimal(xPerc).setScale(4, RoundingMode.HALF_UP);
            xpercentPremium = xpercent.multiply(xcodeunit).setScale(2, RoundingMode.HALF_UP);
            xpercentPremium = billingunit.multiply(xpercentPremium).setScale(2, RoundingMode.HALF_UP);

            bigTotal = bigTotal.add(percentPremium);
            bigTotal = bigTotal.add(xpercentPremium);
            String otherstr2 = stripDecimalPoint(xpercentPremium.toString());

            BillingItemBean billingItem = new BillingItemBean();
            billingItem.setService_code(xCode);
            billingItem.setDesc(xDesc);
            billingItem.setService_value(otherstr2);
            billingItem.setPercentage(xPerc);
            billingItem.setDiag_code(diagcode);
            billingItem.setQuantity(xUnit);
            billing.addBillingItem(billingItem);
        }

        // Avoid unused-variable warnings on locals that the legacy code
        // assigned but never read in some branches; this matches the
        // original behaviour exactly.
        if (false) {
            MiscUtils.getLogger().trace(pCode + pDesc + pPerc + pUnit
                    + eValue + xValue);
        }

        HttpSession session = request.getSession();
        session.setAttribute("billing", billing);

        String otherstr = stripDecimalPoint(bigTotal.toString());

        BillingDataBean billingDataBean = new BillingDataBean();
        session.setAttribute("billingDataBean", billingDataBean);
        billingDataBean.setContent(content.toString());
        billingDataBean.setBilling_no(request.getParameter("xml_billing_no"));
        billingDataBean.setHin(request.getParameter("hin"));
        billingDataBean.setDob(request.getParameter("dob"));
        billingDataBean.setVisittype(request.getParameter("visittype"));
        billingDataBean.setVisitdate(request.getParameter("xml_vdate"));
        billingDataBean.setStatus(request.getParameter("status"));
        billingDataBean.setClinic_ref_code(request.getParameter("clinic_ref_code"));
        billingDataBean.setProviderNo(request.getParameter("provider_no"));
        billingDataBean.setBilling_date(request.getParameter("xml_appointment_date"));
        billingDataBean.setUpdate_date(request.getParameter("update_date"));
        billingDataBean.setTotal(otherstr);

        BillingPatientDataBean billingPatientDataBean = new BillingPatientDataBean();
        session.setAttribute("billingPatientDataBean", billingPatientDataBean);
        billingPatientDataBean.setDemoname(request.getParameter("demo_name"));
        billingPatientDataBean.setAddress(request.getParameter("demo_address"));
        billingPatientDataBean.setProvince(request.getParameter("demo_province"));
        billingPatientDataBean.setCity(request.getParameter("demo_city"));
        billingPatientDataBean.setPostal(request.getParameter("demo_postal"));
        billingPatientDataBean.setSex(request.getParameter("demo_sex"));

        response.sendRedirect(request.getContextPath() + "/billing/CA/ON/BillingCorrectionReview");
    }

    /**
     * Mirrors the long, hand-written premium-eligible service-code list
     * from the legacy JSP body. Includes the original "S"-prefix and
     * "B"-suffix-with-three-exceptions catch-alls.
     */
    private static boolean isPremiumServiceCode(String scode) {
        if (scode == null) return false;
        if ("A001A".equals(scode) || "A003A".equals(scode)
                || "A004A".equals(scode) || "A007A".equals(scode)
                || "A008A".equals(scode) || "A888A".equals(scode)
                || "Z777A".equals(scode) || "P029A".equals(scode)
                || "P028A".equals(scode) || "Z776A".equals(scode)
                || "P042A".equals(scode) || "S768A".equals(scode)
                || "S756A".equals(scode) || "S757A".equals(scode)
                || "S784A".equals(scode) || "S745A".equals(scode)
                || "P010A".equals(scode) || "P009A".equals(scode)
                || "P006A".equals(scode) || "P011A".equals(scode)
                || "P041A".equals(scode) || "P018A".equals(scode)
                || "P038A".equals(scode) || "P020A".equals(scode)
                || "P031A".equals(scode) || "Z552A".equals(scode)
                || "P022A".equals(scode) || "P023A".equals(scode)
                || "P030A".equals(scode) || "Z716A".equals(scode)) {
            return true;
        }
        if (scode.startsWith("S")) return true;
        if (scode.endsWith("B") && !scode.endsWith("C988B")
                && !scode.endsWith("C998B") && !scode.endsWith("C999B")) {
            return true;
        }
        return false;
    }

    /**
     * Mirrors the legacy "delete the decimal point" normalisation so the
     * total is stored as cents-style digits (e.g. "1.50" → "150").
     */
    private static String stripDecimalPoint(String s) {
        StringBuilder sb = new StringBuilder(s);
        int dot = s.indexOf('.');
        if (dot >= 0) {
            sb.deleteCharAt(dot);
        }
        return sb.toString();
    }
}
