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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.ArrayList;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao;
import io.github.carlos_emr.carlos.billing.CA.ON.model.BillingPercLimit;
import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg2ViewModel;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Multi-step service for {@code billingShortcutPg2.jsp}, the fast-track
 * confirmation page that:
 *
 * <ol>
 *   <li>Reads provider + demographic context to render the patient header.</li>
 *   <li>On submit ({@code addition=Confirm}): calculates per-line and percent-code
 *       totals, persists the {@code Billing} + {@code BillingDetail} rows (or
 *       delegates to {@code BillingClaimSubmissionService} for the new-billing pipeline), and
 *       hands the JSP a navigation directive (close window / redirect to pg1).</li>
 * </ol>
 *
 * <p>Owns the 6 inline {@code SpringUtils.getBean} lookups the JSP body used to
 * perform: BillingDao, BillingDetailDao, ProviderDao, DemographicDao,
 * BillingServiceDao, BillingPercLimitDao.</p>
 *
 * <p>Class is named {@code *Service} (not {@code *ViewModelAssembler}) because
 * it both reads view-state AND persists Billing/BillingDetail rows on the
 * confirm path — see {@code docs/architecture/layer-names.md} decision rule 4
 * (anything that writes / mutates state / calls across DAOs as the main verb
 * of a single business op is a {@code *Service}). A future split into
 * read-only assembler + write-only persister is tracked separately.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingShortcutPg2Service {

    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final ProviderDao providerDao;
    private final DemographicDao demographicDao;
    private final BillingServiceDao billingServiceDao;
    private final BillingPercLimitDao billingPercLimitDao;
    private final BillingClaimSubmissionService saveObj;

    public BillingShortcutPg2Service(BillingDao billingDao,
                                    BillingDetailDao billingDetailDao,
                                    ProviderDao providerDao,
                                    DemographicDao demographicDao,
                                    BillingServiceDao billingServiceDao,
                                    BillingPercLimitDao billingPercLimitDao,
                                    BillingClaimSubmissionService saveObj) {
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.providerDao = providerDao;
        this.demographicDao = demographicDao;
        this.billingServiceDao = billingServiceDao;
        this.billingPercLimitDao = billingPercLimitDao;
        this.saveObj = saveObj;
    }

    /**
     * Build the confirmation-page view model.
     *
     * @param request in-flight request — provides patient/provider IDs,
     *                bill-line params, submit-button decision
     * @param loggedInInfo logged-in user — provider-no derived locally as
     *                     {@code userNo} for the {@code creator} field on
     *                     persisted {@code Billing} rows
     * @return populated view model. The {@code postSaveAction} field tells the
     *         JSP what to render after the form body (close popup, redirect).
     */
    public BillingShortcutPg2ViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String userNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        BillingShortcutPg2ViewModel.Builder b = BillingShortcutPg2ViewModel.builder();

        Provider provider = providerDao.getProvider(request.getParameter("xml_provider"));
        if (provider != null) {
            b.providerOhipNo(provider.getOhipNo()).providerRmaNo(provider.getRmaNo());
        }

        DemoContext demo = loadDemographic(request.getParameter("demographic_no"));
        applyDemoToBuilder(b, demo);
        applyJspViewFieldsToBuilder(b, request);

        if (!shouldSave(request)) {
            return b.postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.NONE).build();
        }

        // Calculate-only case (submit=Save/Next/Save and Back without addition=Confirm)
        // never actually happened in the legacy code — both calculation and persist
        // ran inside the same `if(submit && addition=Confirm)` block. Preserved here.
        CalcResult calc = calculate(request);
        b.calculationHtml(calc.html).totalAmount(calc.total);

        if (!"Confirm".equals(trim(request.getParameter("addition")))) {
            return b.postSaveAction(BillingShortcutPg2ViewModel.PostSaveAction.NONE).build();
        }

        persistBills(request, calc, demo, provider, userNo);

        BillingShortcutPg2ViewModel.PostSaveAction action;
        String redirectUrl = "";
        String submit = request.getParameter("submit");
        if ("Save".equals(submit)) {
            action = BillingShortcutPg2ViewModel.PostSaveAction.CLOSE_WINDOW;
        } else {
            action = BillingShortcutPg2ViewModel.PostSaveAction.REDIRECT_TO_PG1;
            redirectUrl = buildPg1RedirectUrl(request, demo, userNo);
        }
        return b.calculationHtml(calc.html + "<br>Billing records were added.<br>")
                .postSaveAction(action)
                .redirectUrl(redirectUrl)
                .build();
    }

    private static boolean shouldSave(HttpServletRequest request) {
        String submit = request.getParameter("submit");
        return "Next".equals(submit) || "Save".equals(submit) || "Save and Back".equals(submit);
    }

    private DemoContext loadDemographic(String demoNo) {
        DemoContext ctx = new DemoContext();
        if (demoNo == null || demoNo.isEmpty()) {
            return ctx;
        }
        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
            return ctx;
        }
        ctx.demo = demo;
        ctx.first = nullToEmpty(demo.getFirstName());
        ctx.last = nullToEmpty(demo.getLastName());

        String sex = demo.getSex();
        if ("M".equals(sex)) {
            sex = "1";
        } else if ("F".equals(sex)) {
            sex = "2";
        }
        ctx.sex = nullToEmpty(sex);

        if (demo.getHin() != null && demo.getVer() != null) {
            ctx.hin = demo.getHin() + demo.getVer();
        }

        String hcType = demo.getHcType();
        if (hcType == null || hcType.isEmpty() || hcType.length() < 2) {
            ctx.hcType = "ON";
        } else {
            ctx.hcType = hcType.substring(0, 2).toUpperCase();
        }

        ctx.dobYy = nullToEmpty(demo.getYearOfBirth());
        ctx.dobMm = nullToEmpty(demo.getMonthOfBirth());
        ctx.dobDd = nullToEmpty(demo.getDateOfBirth());
        if (ctx.dobMm.length() == 1) ctx.dobMm = "0" + ctx.dobMm;
        if (ctx.dobDd.length() == 1) ctx.dobDd = "0" + ctx.dobDd;
        ctx.dob = ctx.dobYy + ctx.dobMm + ctx.dobDd;

        if (demo.getFamilyDoctor() == null) {
            ctx.refDoctor = "N/A";
            ctx.refDoctorOhip = "000000";
        } else {
            String rd = SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rd");
            String rdohip = SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rdohip");
            ctx.refDoctor = rd == null ? "" : rd;
            ctx.refDoctorOhip = rdohip == null ? "" : rdohip;
        }

        StringBuilder warn = new StringBuilder();
        StringBuilder err = new StringBuilder();
        if (demo.getHin() == null || demo.getHin().isEmpty()) {
            warn.append("<br><font color='orange'>Warning: The patient does not have a valid HIN. </font><br>");
        }
        if (ctx.refDoctorOhip != null && !ctx.refDoctorOhip.isEmpty() && ctx.refDoctorOhip.length() != 6) {
            warn.append("<br><font color='orange'>Warning: the referral doctor's no is wrong. </font><br>");
        }
        if (ctx.dob.length() != 8) {
            ctx.errorFlagged = true;
            err.append("<br><font color='red'>Error: The patient does not have a valid DOB. </font><br>");
        }
        ctx.warningMsg = warn.toString();
        ctx.errorMsg = err.toString();
        return ctx;
    }

    private void applyDemoToBuilder(BillingShortcutPg2ViewModel.Builder b, DemoContext c) {
        b.demoFirst(c.first).demoLast(c.last)
                .demoSex(c.sex).displaySex("1".equals(c.sex) ? "Male" : "Female")
                .demoDobYy(c.dobYy).demoDobMm(c.dobMm).demoDobDd(c.dobDd)
                .demoHin(c.hin).demoHcType(c.hcType)
                .referralDoctor(c.refDoctor).referralDoctorOhip(c.refDoctorOhip)
                .errorMsg(c.errorMsg).warningMsg(c.warningMsg).errorFlagged(c.errorFlagged);
    }

    /**
     * Populate the JSP-presentation fields the legacy scriptlet body computed
     * inline: provider-name labels (resolved via the session-scoped
     * {@code providerBean} populated at login), visit-type / bill-type /
     * location split-on-pipe labels, SLI-applicable flag, request-param
     * echoes for the hidden-input loop, and the pre-rendered billDate column
     * HTML. Centralizing these in the assembler keeps the JSP body 100% EL.
     */
    private void applyJspViewFieldsToBuilder(BillingShortcutPg2ViewModel.Builder b,
                                             HttpServletRequest request) {
        java.util.Properties providerBean;
        Object beanObj = request.getSession().getAttribute("providerBean"); // nosemgrep: tainted-session-from-http-request -- providerBean is built post-auth from DAO-sourced active providers (see ImportDemographicDataAction42Action)
        providerBean = (beanObj instanceof java.util.Properties) ? (java.util.Properties) beanObj : new java.util.Properties();

        String xmlProvider = nullToEmpty(request.getParameter("xml_provider"));
        String assgProviderNo = nullToEmpty(request.getParameter("assgProvider_no"));

        b.billingProviderLabel(providerBean.getProperty(xmlProvider, ""));
        b.assignedProviderLabel(providerBean.getProperty(assgProviderNo, ""));

        b.visitTypeLabel(splitOnPipeAfter(request.getParameter("xml_visittype")));
        b.billTypeLabel(splitOnPipeAfter(request.getParameter("xml_billtype")));
        b.visitLocationLabel(splitOnPipeAfter(request.getParameter("xml_location")));

        // SLI code: take everything after the | (the "label" half), then
        // compare against configured clinic_no. If it begins with that
        // value, the field is "Not Applicable" — the legacy treated SLI
        // codes that match the clinic prefix as N/A.
        String sliCode = splitOnPipeAfter(request.getParameter("xml_slicode"));
        String clinicNoTrim = io.github.carlos_emr.CarlosProperties.getInstance()
                .getProperty("clinic_no", "").trim();
        boolean sliNotApplicable = sliCode.startsWith(clinicNoTrim);
        b.sliCode(sliCode).sliNotApplicable(sliNotApplicable);

        b.admissionDate(nullToEmpty(request.getParameter("xml_vdate")));
        b.demographicName(nullToEmpty(request.getParameter("demographic_name")));
        b.dxCode(nullToEmpty(request.getParameter("dxCode")));
        b.rulePerc(nullToEmpty(request.getParameter("rulePerc")));
        b.referralDocName(nullToEmpty(request.getParameter("referralDocName")));
        b.referralCodeParam(nullToEmpty(request.getParameter("referralCode")));

        // billDate may contain a newline-separated list of dates (multi-row
        // billing). Each line is HTML-encoded and joined with <br> so the JSP
        // can output the result without further escaping.
        String billDateRaw = request.getParameter("billDate");
        if (billDateRaw == null) {
            b.billDateHtml("");
        } else {
            StringBuilder sb = new StringBuilder();
            String[] parts = billDateRaw.split("\\n");
            for (int i = 0; i < parts.length; i++) {
                if (i > 0) sb.append("<br>");
                sb.append(SafeEncode.forHtml(parts[i]));
            }
            b.billDateHtml(sb.toString());
        }

        // Hidden-input loop echo map — capture every request param verbatim
        // so the JSP renders one <c:forEach> rather than iterating
        // request.getParameterNames() inline. The original loop emitted
        // <input type="hidden" name="<param>" value="<param value>"> for
        // every parameter; the map preserves that exact behaviour.
        java.util.Map<String, String> echoes = new java.util.LinkedHashMap<>();
        Enumeration<String> en = request.getParameterNames();
        while (en.hasMoreElements()) {
            String name = en.nextElement();
            String v = request.getParameter(name);
            echoes.put(name, v == null ? "" : v);
        }
        b.requestParamEchoes(echoes);
    }

    /** Returns the substring after the first {@code |} delimiter, or empty
     *  string when the input is null or has no pipe. The legacy XML-attribute
     *  pattern stored "key|label" pairs in many param values. */
    private static String splitOnPipeAfter(String s) {
        if (s == null || !s.contains("|")) return "";
        return s.substring(s.indexOf('|') + 1);
    }

    private CalcResult calculate(HttpServletRequest request) {
        // Resolve the per-row service codes pulled out of `serviceDate{i}` /
        // `serviceUnit{i}` request params. Same loop unrolled the legacy JSP
        // ran. Layered on top: form-based codes from `code_xml_*` etc.
        int NUMTYPEINFIELD = 5;
        ArrayList<String> vecServiceCode = new ArrayList<>();
        ArrayList<String> vecServiceCodeDesc = new ArrayList<>();
        ArrayList<String> vecServiceCodeUnit = new ArrayList<>();
        ArrayList<String> vecServiceCodePrice = new ArrayList<>();
        ArrayList<String> vecServiceCodePerc = new ArrayList<>();

        String rulePerc = request.getParameter("rulePerc");
        if (rulePerc == null) rulePerc = "allAboveCode";
        int rulePercLabelNum = -1;

        String[] billrec = new String[]{"", "", "", "", ""};
        String[] billrecunit = new String[]{"", "", "", "", ""};
        int recordCount = 0;
        for (int i = 0; i < NUMTYPEINFIELD; i++) {
            String date = request.getParameter("serviceDate" + i);
            if (date == null || date.isEmpty()) break;
            billrec[i] = date;
            String unit = request.getParameter("serviceUnit" + i);
            billrecunit[i] = (unit == null || unit.isEmpty()) ? "1" : unit;
            recordCount++;
        }

        java.util.Date billDate = ConversionUtils.fromDateString(request.getParameter("billDate"));
        for (int i = 0; i < recordCount; i++) {
            for (BillingService bs : billingServiceDao.findByServiceCodeAndLatestDate(billrec[i], billDate)) {
                String desc = bs.getDescription();
                String price = bs.getValue() == null ? "" : bs.getValue();
                String perc = bs.getPercentage();
                if ((!price.isEmpty() && BillingMoney.isPositive(price)) || perc == null || perc.isEmpty()) {
                    vecServiceCode.add(billrec[i]);
                    vecServiceCodeDesc.add(desc);
                    vecServiceCodePrice.add(price);
                    vecServiceCodeUnit.add(billrecunit[i]);
                } else {
                    if (!"allAboveCode".equals(rulePerc) && rulePercLabelNum == -1) {
                        rulePercLabelNum = (i - 1 == -1) ? 0 : i - 1;
                    }
                    vecServiceCodePerc.add(billrec[i]);
                    vecServiceCodePerc.add(perc);
                    vecServiceCodePerc.add(billrecunit[i]);
                    vecServiceCodePerc.add(desc);
                }
            }
        }

        // Form-based service codes: parameter names matching `code_xml_*`
        // are visible in the request param map; iterate and pair up each
        // `code_xml_X` with its sibling `desc_xml_X`, `price_xml_X`,
        // `perc_xml_X`, `unit_xml_X`. Form-side codes use `_` separator.
        Enumeration<String> e = request.getParameterNames();
        while (e.hasMoreElements()) {
            String temp = e.nextElement();
            if (!temp.contains("code_xml_")) continue;
            temp = temp.substring("code_".length());
            String desc = request.getParameter("desc_" + temp);
            String fee = request.getParameter("price_" + temp);
            String perc = request.getParameter("perc_" + temp);
            String tempUnit = request.getParameter("unit_" + temp);
            if (tempUnit == null || tempUnit.isEmpty()) tempUnit = "1";
            String code = temp.substring("xml_".length()).toUpperCase();
            if ((fee != null && !fee.isEmpty() && BillingMoney.isPositive(fee))
                    || perc == null || perc.isEmpty()) {
                vecServiceCodePrice.add(fee == null ? "" : fee);
                vecServiceCodeUnit.add(tempUnit);
                vecServiceCode.add(code);
                vecServiceCodeDesc.add(desc == null ? "" : desc);
            } else {
                vecServiceCodePerc.add(code);
                vecServiceCodePerc.add(perc);
                vecServiceCodePerc.add(tempUnit);
                vecServiceCodePerc.add(desc == null ? "" : desc);
            }
        }

        int size = vecServiceCodePerc.size() / 4;
        String[] aMinFee = new String[size];
        String[] aMaxFee = new String[size];
        boolean[] aLimits = new boolean[size];
        int codeIdx = 0;
        for (int idx2 = 0; idx2 < size; idx2++) {
            for (BillingPercLimit bpl : billingPercLimitDao.findByServiceCode("" + vecServiceCodePerc.get(codeIdx))) {
                aLimits[idx2] = true;
                aMinFee[idx2] = bpl.getMin();
                aMaxFee[idx2] = bpl.getMax();
            }
            codeIdx += 4;
        }

        StringBuilder msg = new StringBuilder("<tr><td colspan='2'>Calculation</td></tr>");
        BigDecimal bdTotal = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        BigDecimal bdPercBase = new BigDecimal("0").setScale(2, RoundingMode.HALF_UP);
        for (int i = 0; i < vecServiceCodePrice.size(); i++) {
            BigDecimal price = BillingMoney.amount(vecServiceCodePrice.get(i));
            BigDecimal unit = BillingMoney.amount(vecServiceCodeUnit.get(i));
            bdTotal = bdTotal.add(price.multiply(unit).setScale(2, RoundingMode.HALF_UP));
            if (i == rulePercLabelNum) {
                bdPercBase = bdTotal;
            }
            msg.append("<tr bgcolor='#EEEEFF'><td align='right' width='20%'>")
                    .append(SafeEncode.forHtml(vecServiceCode.get(i)))
                    .append(" (").append(Math.round(unit.floatValue())).append(")</td>")
                    .append("<td align='right'>").append(i == 0 ? "" : " + ")
                    .append(price).append(" x ").append(unit).append(" = ")
                    .append(bdTotal).append("</td></tr>");
        }

        if ("allAboveCode".equals(rulePerc)) {
            bdPercBase = bdTotal;
        }
        codeIdx = 1;
        BigDecimal[] bdPercs = new BigDecimal[size];
        for (int idx3 = 0; idx3 < size; idx3++) {
            BigDecimal perc = BillingMoney.amount(vecServiceCodePerc.get(codeIdx));
            BigDecimal bdPerc = bdPercBase.multiply(perc).setScale(2, RoundingMode.HALF_UP);
            msg.append("<tr bgcolor='#EEEEFF'><td align='right'>")
                    .append(SafeEncode.forHtml(String.valueOf(vecServiceCodePerc.get(codeIdx - 1))))
                    .append(" (1)</td><td align='right'>Percentage : ")
                    .append(bdPercBase).append(" x ").append(perc).append(" = ")
                    .append(bdPerc).append("</td></tr>");
            if (aLimits[idx3]) {
                bdPerc = bdPerc.min(BillingMoney.amount(aMaxFee[idx3]));
                bdPerc = bdPerc.max(BillingMoney.amount(aMinFee[idx3]));
                msg.append("<tr bgcolor='ivory'><td align='right' colspan='2'>Adjust to (")
                        .append(aMinFee[idx3]).append(", ").append(aMaxFee[idx3]).append("): </td>")
                        .append("<td align='right'>").append(bdPerc).append("</td></tr>");
            }
            bdTotal = bdTotal.add(bdPerc);
            bdPercs[idx3] = bdPerc;
            codeIdx += 4;
        }

        msg.append("<tr><td align='right' colspan='2'>Total: ").append(bdTotal).append("</td></tr>");

        CalcResult result = new CalcResult();
        result.html = msg.toString();
        result.total = "" + bdTotal;
        result.vecServiceCode = vecServiceCode;
        result.vecServiceCodeDesc = vecServiceCodeDesc;
        result.vecServiceCodePrice = vecServiceCodePrice;
        result.vecServiceCodeUnit = vecServiceCodeUnit;
        result.vecServiceCodePerc = vecServiceCodePerc;
        result.bdPercs = bdPercs;
        result.size = size;
        return result;
    }

    private void persistBills(HttpServletRequest request, CalcResult calc, DemoContext demo,
                              Provider provider, String userNo) {
        String billingDate = request.getParameter("billDate");
        if (billingDate == null) return;
        String[] tempDate = billingDate.split("\\s");

        boolean isNewBilling = CarlosProperties.getInstance().getProperty("isNewONbilling", "")
                .equals("true");

        boolean bServicePerc = false;
        for (String dateStr : tempDate) {
            if (dateStr == null || dateStr.trim().length() != 10) continue;

            if (isNewBilling) {
                if (!bServicePerc && calc.vecServiceCodePerc.size() > 1) {
                    bServicePerc = true;
                    int codeIdx = 0;
                    for (int idx4 = 0; idx4 < calc.size; idx4++) {
                        calc.vecServiceCodePrice.add("" + calc.bdPercs[idx4]);
                        calc.vecServiceCodeUnit.add(calc.vecServiceCodePerc.get(codeIdx + 2));
                        calc.vecServiceCode.add(calc.vecServiceCodePerc.get(codeIdx));
                        calc.vecServiceCodeDesc.add(calc.vecServiceCodePerc.get(codeIdx + 3));
                        codeIdx += 4;
                    }
                }
                @SuppressWarnings("unchecked")
                ArrayList<Object> vecT = saveObj.getBillingClaimHospObj(request, dateStr, calc.total,
                        calc.vecServiceCode, calc.vecServiceCodeUnit, calc.vecServiceCodePrice);
                saveObj.addABillingRecord(vecT);
            } else {
                persistLegacyBillingRecord(request, dateStr, calc, demo, provider, userNo);
            }
        }
    }

    private void persistLegacyBillingRecord(HttpServletRequest request, String dateStr,
                                             CalcResult calc, DemoContext demo,
                                             Provider provider, String userNo) {
        Billing b = new Billing();
        // FK fields: must be valid integers. The legacy parseInt helper
        // returned 0 on failure, which silently persisted bills keyed to
        // demographic_no=0 / clinic_no=0 / appointment_no=0 — orphan rows
        // that contaminate downstream reports. Throw cleanly so the action's
        // BillingValidationException mapping renders the validation page.
        b.setClinicNo(parseRequiredInt("clinic_no", request.getParameter("clinic_no")));
        b.setDemographicNo(parseRequiredInt("demographic_no", request.getParameter("demographic_no")));
        b.setProviderNo(request.getParameter("xml_provider"));
        b.setAppointmentNo(parseRequiredInt("appointment_no", request.getParameter("appointment_no")));
        b.setOrganizationSpecCode(request.getParameter("ohip_version"));
        b.setDemographicName(request.getParameter("demographic_name"));
        b.setHin(request.getParameter("hin"));
        b.setUpdateDate(new java.util.Date());
        b.setUpdateTime(new java.util.Date());
        b.setBillingDate(MyDateFormat.getSysDate(dateStr));
        b.setBillingTime(MyDateFormat.getSysTime(request.getParameter("start_time")));
        b.setClinicRefCode(splitOnPipeFirst(request.getParameter("xml_location")));
        b.setContent(buildContent(request, demo));
        b.setTotal(calc.total);
        String billtype = request.getParameter("xml_billtype");
        b.setStatus(billtype != null && billtype.length() >= 1 ? billtype.substring(0, 1) : "");
        b.setDob(request.getParameter("demographic_dob"));
        b.setVisitDate(MyDateFormat.getSysDate(request.getParameter("xml_vdate")));
        String visitType = request.getParameter("xml_visittype");
        b.setVisitType(visitType != null && visitType.length() >= 2 ? visitType.substring(0, 2) : "");
        b.setProviderOhipNo(provider == null ? "" : nullToEmpty(provider.getOhipNo()));
        b.setProviderRmaNo(provider == null ? "" : nullToEmpty(provider.getRmaNo()));
        b.setApptProviderNo(request.getParameter("apptProvider_no"));
        b.setAsstProviderNo("");
        b.setCreator(userNo);
        billingDao.persist(b);

        int nBillNo = b.getId();

        // Append percentage rows into the per-line vectors (legacy did this
        // once, in the inner loop — preserved as-is to stay byte-identical
        // with what the legacy JSP would have written).
        if (calc.vecServiceCodePerc.size() > 1) {
            calc.vecServiceCodePrice.add("" + calc.bdPercs[0]);
            calc.vecServiceCodeUnit.add(calc.vecServiceCodePerc.get(2));
            calc.vecServiceCode.add(calc.vecServiceCodePerc.get(0));
            calc.vecServiceCodeDesc.add(calc.vecServiceCodePerc.get(3));
        }

        for (int i = 0; i < calc.vecServiceCode.size(); i++) {
            BigDecimal bdEachPrice = BillingMoney.amount(calc.vecServiceCodePrice.get(i));
            BigDecimal bdEachUnit = BillingMoney.amount(calc.vecServiceCodeUnit.get(i));
            BigDecimal bdEachTotal = bdEachPrice.multiply(bdEachUnit).setScale(2, RoundingMode.HALF_UP);

            BillingDetail bd = new BillingDetail();
            bd.setBillingNo(nBillNo);
            bd.setServiceCode(calc.vecServiceCode.get(i));
            bd.setServiceDesc(calc.vecServiceCodeDesc.get(i));
            bd.setBillingAmount(("" + bdEachTotal).replaceAll("\\.", ""));
            bd.setDiagnosticCode(request.getParameter("dxCode"));
            bd.setAppointmentDate(MyDateFormat.getSysDate(dateStr));
            bd.setStatus(billtype != null && billtype.length() >= 1 ? billtype.substring(0, 1) : "");
            bd.setBillingUnit(calc.vecServiceCodeUnit.get(i));
            billingDetailDao.persist(bd);

            if (bd.getId() == 0) {
                Billing bb = billingDao.find(nBillNo);
                if (bb != null) {
                    bb.setStatus("D");
                    billingDao.merge(bb);
                }
                break;
            }
        }
    }

    private String buildContent(HttpServletRequest request, DemoContext demo) {
        String content = "";
        String referralCode = (request.getParameter("referralCode") != null
                && request.getParameter("referralCode").length() == 6)
                ? request.getParameter("referralCode") : null;
        if (referralCode != null) {
            content += "<xml_referral>checked</xml_referral>";
            content += "<rdohip>" + referralCode + "</rdohip>";
        }
        content += "<hctype>" + demo.hcType + "</hctype>";
        content += "<demosex>" + demo.sex + "</demosex>";
        return content;
    }

    private String buildPg1RedirectUrl(HttpServletRequest request, DemoContext demo, String userNo) {
        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay = now.get(Calendar.DAY_OF_MONTH);

        CarlosProperties props = CarlosProperties.getInstance();
        String hospitalView = props.getProperty("hospital_view", props.getProperty("default_view"));

        StringBuilder url = new StringBuilder();
        url.append(request.getContextPath())
                .append("/billing/CA/ON/billingShortcutPg1View?billRegion=&billForm=")
                .append(URLEncoder.encode(hospitalView == null ? "" : hospitalView, StandardCharsets.UTF_8))
                .append("&hotclick=&appointment_no=0&demographic_name=")
                .append(URLEncoder.encode(demo.last, StandardCharsets.UTF_8))
                .append("%2C")
                .append(URLEncoder.encode(demo.first, StandardCharsets.UTF_8))
                .append("&demographic_no=")
                .append(URLEncoder.encode(nullToEmpty(request.getParameter("demographic_no")), StandardCharsets.UTF_8))
                .append("&providerview=1&user_no=")
                .append(URLEncoder.encode(nullToEmpty(userNo), StandardCharsets.UTF_8))
                .append("&apptProvider_no=none&appointment_date=")
                // Zero-pad month/day so the value matches the canonical
                // yyyy-MM-dd shape consumed by Pg1's calendar widget and any
                // downstream DateUtils.parseDate caller. Single-digit M / d
                // are visually wrong even when SimpleDateFormat is lenient,
                // and round-trip into form input echoes as e.g. "2026-4-7".
                .append(String.format("%d-%02d-%02d", curYear, curMonth, curDay))
                .append("&start_time=0:00:00&bNewForm=1&status=t");
        return url.toString();
    }

    private static String splitOnPipeFirst(String s) {
        if (s == null || !s.contains("|")) return "";
        return s.substring(0, s.indexOf("|")).trim();
    }

    private static int parseInt(String s) {
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException | NullPointerException ex) {
            return 0;
        }
    }

    /**
     * Strict parse for FK fields (demographic_no, clinic_no, appointment_no).
     * Throws {@link BillingValidationException} so the missing or malformed
     * value rolls back the persist path rather than silently writing a row
     * keyed to 0.
     */
    private static int parseRequiredInt(String paramName, String value) {
        if (value == null || value.isEmpty()) {
            throw new BillingValidationException(
                    "Bill save rejected: required parameter [" + paramName + "] is missing");
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            throw new BillingValidationException(
                    "Bill save rejected: parameter [" + paramName
                            + "] is not a valid integer", ex);
        }
    }

    private static String trim(String s) { return s == null ? "" : s.trim(); }
    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static class DemoContext {
        @SuppressWarnings("unused")
        Demographic demo;
        String first = "";
        String last = "";
        String sex = "";
        String hin = "";
        String hcType = "";
        String dobYy = "";
        String dobMm = "";
        String dobDd = "";
        String dob = "";
        String refDoctor = "";
        String refDoctorOhip = "";
        String warningMsg = "";
        String errorMsg = "";
        boolean errorFlagged;
    }

    private static class CalcResult {
        String html;
        String total;
        ArrayList<String> vecServiceCode;
        ArrayList<String> vecServiceCodeDesc;
        ArrayList<String> vecServiceCodePrice;
        ArrayList<String> vecServiceCodeUnit;
        ArrayList<String> vecServiceCodePerc;
        BigDecimal[] bdPercs;
        int size;

        CalcResult() {
            this.vecServiceCode = new ArrayList<>();
            this.vecServiceCodeDesc = new ArrayList<>();
            this.vecServiceCodePrice = new ArrayList<>();
            this.vecServiceCodeUnit = new ArrayList<>();
            this.vecServiceCodePerc = new ArrayList<>();
            this.bdPercs = new BigDecimal[0];
        }
    }

}
