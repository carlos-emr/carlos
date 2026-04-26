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

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.Vector;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.appt.ApptUtil;
import io.github.carlos_emr.carlos.billings.ca.on.administration.GstControl2Action;
import io.github.carlos_emr.carlos.billings.ca.on.administration.GstReport;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingONReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReviewCodeItem;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingReviewPercItem;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingSortComparator;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingCodeImpl;
import io.github.carlos_emr.carlos.billings.ca.on.data.JdbcBillingPageUtil;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONRequestParams;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONReviewValidator;
import io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingReviewPrep;

/**
 * Assembles {@link BillingONReviewViewModel} for {@code billingONReview.jsp}.
 *
 * <p>Pure read of request state into a view model. Encapsulates all the data
 * preparation that previously lived in the JSP's body scriptlets.</p>
 *
 * @since 2026-04-24
 *        2026-04-25 (full body-scriptlet drain expansion)
 */
public final class BillingONReviewDataAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final BillingReviewPrep reviewPrep;
    private final BillingONReviewValidator validator;

    public BillingONReviewDataAssembler() {
        this(SpringUtils.getBean(DemographicDao.class),
             SpringUtils.getBean(ProviderDao.class),
             new BillingReviewPrep(),
             new BillingONReviewValidator());
    }

    BillingONReviewDataAssembler(DemographicDao demographicDao,
                                 ProviderDao providerDao,
                                 BillingReviewPrep reviewPrep,
                                 BillingONReviewValidator validator) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.reviewPrep = reviewPrep;
        this.validator = validator;
    }

    public BillingONReviewViewModel assemble(HttpServletRequest request) {
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));

        String dxDesc = reviewPrep.getDxDescription(dxCode);
        BillingONReviewViewModel.Builder b = BillingONReviewViewModel.builder()
                .dxCode(dxCode)
                .dxDesc(dxDesc == null ? "" : dxDesc);

        loadProvider(request, b);
        loadDemographic(demoNo, request.getParameter("DemoSex"), b);

        String billRefDate = firstNonEmpty(
                request.getParameter("service_date"),
                request.getParameter("appointment_date"),
                request.getParameter("billReferalDate"));
        BillingONReviewValidator.Result validation = validator.validate(request, demoNo, billRefDate);
        b.validationMessages(validation.messages())
                .codeValid(validation.codeValid());

        Map<String, BillingONReviewViewModel.ProviderName> providerNames = new HashMap<>();
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providerNames.put(nullToEmpty(p.getProviderNo()),
                    new BillingONReviewViewModel.ProviderName(
                            nullToEmpty(p.getLastName()),
                            nullToEmpty(p.getFirstName())));
        }
        b.providerNameLookup(providerNames);

        populateRequestParamEchoes(request, b);
        b.loggedInUserNo(nullToEmpty((String) request.getSession().getAttribute("user")));
        populateLabelFields(request, b);
        populateDemoHeader(request, b);
        populateServiceCodeAndPercRows(request, billRefDate, b, validation.codeValid());
        populateBillingNotesAndPaymentInfo(request, b, validation.codeValid(), providerNames);
        populateAllRequestParams(request, b);

        return b.build();
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) {
            if (v != null && !v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private void loadProvider(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        String providerNo = BillingONRequestParams.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        b.providerView(providerNo);

        Provider p = providerNo.isEmpty() ? null : providerDao.getProvider(providerNo);
        if (p != null) {
            b.providerOhip(nullToEmpty(p.getOhipNo()));
            b.providerRma(nullToEmpty(p.getRmaNo()));
        }
    }

    private void loadDemographic(String demoNo, String demoSexParam, BillingONReviewViewModel.Builder b) {
        if (demoNo.isEmpty()) {
            b.demoSex(nullToEmpty(demoSexParam));
            return;
        }
        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
            b.demoSex(nullToEmpty(demoSexParam));
            return;
        }

        StringBuilder errorMessage = new StringBuilder();
        StringBuilder warningMessage = new StringBuilder();
        String errorFlag = "";

        b.demoFirst(nullToEmpty(demo.getFirstName()))
                .demoLast(nullToEmpty(demo.getLastName()))
                .demoHin(nullToEmpty(demo.getHin()))
                .demoVer(nullToEmpty(demo.getVer()))
                .assignedProviderNo(nullToEmpty(demo.getProviderNo()))
                .patientAddress(buildPatientAddress(demo));

        String demoSex = nullToEmpty(demo.getSex());
        if ("M".equals(demoSex)) {
            demoSex = "1";
        } else if ("F".equals(demoSex)) {
            demoSex = "2";
        }
        b.demoSex(demoSex);

        String demoHcType = nullToEmpty(demo.getHcType());
        if (demoHcType.length() < 2) {
            demoHcType = "ON";
        } else {
            demoHcType = demoHcType.substring(0, 2).toUpperCase();
        }
        b.demoHcType(demoHcType);

        String demoDobYy = nullToEmpty(demo.getYearOfBirth());
        String demoDobMm = padTwo(nullToEmpty(demo.getMonthOfBirth()));
        String demoDobDd = padTwo(nullToEmpty(demo.getDateOfBirth()));
        String demoDob = demoDobYy + demoDobMm + demoDobDd;
        b.demoDobYy(demoDobYy).demoDobMm(demoDobMm).demoDobDd(demoDobDd).demoDob(demoDob);

        String referralDoctorName = "";
        String referralDoctorOhip = "";
        if (demo.getFamilyDoctor() == null) {
            referralDoctorName = "N/A";
            referralDoctorOhip = "000000";
        } else {
            referralDoctorName = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rd"));
            referralDoctorOhip = nullToEmpty(SxmlMisc.getXmlContent(demo.getFamilyDoctor(), "rdohip"));
        }
        b.referralDoctorName(referralDoctorName).referralDoctorOhip(referralDoctorOhip);

        if (demo.getHin() == null) {
            errorFlag = "1";
            errorMessage.append("<br><div class='alert alert-danger'>Error: The patient does not have a HIN </div><br>");
        } else if (demo.getHin().isEmpty()) {
            warningMessage.append("<br><div class='alert alert-danger'>Warning: The patient does not have a HIN </div><br>");
        }
        if (!referralDoctorOhip.isEmpty() && referralDoctorOhip.length() != 6) {
            warningMessage.append("<br><div class='alert alert-danger'>Warning: the referral doctor's no is wrong. </div><br>");
        }
        if (demoDob.length() != 8) {
            errorFlag = "1";
            errorMessage.append("<br><div class='alert alert-danger'>Error: The patient does not have a valid DOB. </div><br>");
        }

        b.errorFlag(errorFlag)
                .errorMessage(errorMessage.toString())
                .warningMessage(warningMessage.toString());
    }

    private static String buildPatientAddress(Demographic demo) {
        return nullToEmpty(demo.getFirstName()) + " " + nullToEmpty(demo.getLastName()) + "\n"
                + nullToEmpty(demo.getAddress()) + "\n"
                + nullToEmpty(demo.getCity()) + ", " + nullToEmpty(demo.getProvince()) + "\n"
                + nullToEmpty(demo.getPostal()) + "\n"
                + "Tel: " + nullToEmpty(demo.getPhone());
    }

    private static String padTwo(String v) {
        return (v != null && v.length() == 1) ? "0" + v : v;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    // -- 2026-04-25 expansion helpers --------------------------------------

    private void populateRequestParamEchoes(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        Map<String, String> echoes = new LinkedHashMap<>();
        String[] keys = {
                "url_back", "billNo_old", "billStatus_old", "billForm",
                "service_date", "referralDocName", "referralCode", "site",
                "siteId", "xml_billtype", "xml_visittype", "xml_location",
                "xml_slicode", "xml_vdate", "m_review", "appointment_no",
                "demographic_no", "demographic_name", "apptProvider_no",
                "assgProvider_no", "checkFlag"
        };
        for (String k : keys) {
            String v = request.getParameter(k);
            if (v != null) echoes.put(k, v);
        }
        b.requestParamEchoes(echoes);
    }

    private void populateLabelFields(HttpServletRequest request,
                                     BillingONReviewViewModel.Builder b) {
        b.demoName(nullToEmpty(request.getParameter("demographic_name")));
        b.multisitesEnabled(IsPropertiesOn.isMultisitesEnable());
        b.mReview(request.getParameter("m_review") != null);
        b.siteName(nullToEmpty(request.getParameter("site")));
        b.admissionDate(nullToEmpty(request.getParameter("xml_vdate")));

        b.visitTypeLabel(extractAfterPipe(request.getParameter("xml_visittype")));
        b.locationLabel(extractAfterPipe(request.getParameter("xml_location")));
        b.sliCodeLabel(extractAfterPipe(request.getParameter("xml_slicode")));
        String xmlBilltype = request.getParameter("xml_billtype");
        b.billTypeLabel(extractAfterPipe(xmlBilltype));
        b.billType(extractBeforePipe(xmlBilltype));

        boolean publicPayer = xmlBilltype != null && xmlBilltype.matches("ODP.*|WCB.*|NOT.*|BON.*");
        b.publicPayer(publicPayer);
        b.privatePayer(xmlBilltype != null && !publicPayer);

        String xmlProvider = request.getParameter("xml_provider");
        String physicianNo = "";
        if (xmlProvider != null) {
            int sep = xmlProvider.indexOf("|");
            physicianNo = sep >= 0 ? xmlProvider.substring(0, sep) : xmlProvider;
        }
        b.billingPhysicianLabel(resolveProviderDisplay(request, physicianNo));

        String assgProviderNo = nullToEmpty(request.getParameter("assgProvider_no"));
        if (request.getParameter("assgProvider_no") == null) {
            b.mrpLabel("N/A");
        } else {
            b.mrpLabel(resolveProviderDisplay(request, assgProviderNo));
        }
    }

    private void populateDemoHeader(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        BillingONReviewViewModel partial = b.build();
        String demoSex = partial.getDemoSex();
        String sexLabel = "1".equals(demoSex) ? "Male" : "Female";
        b.demoSexLabel(sexLabel);

        String header = " DOB: " + partial.getDemoDobYy() + "/"
                + partial.getDemoDobMm() + "/" + partial.getDemoDobDd()
                + " &nbsp;&nbsp; HIN: " + partial.getDemoHin() + partial.getDemoVer();
        b.demoHeaderLine(header);

        String wrong = nullToEmpty(partial.getErrorMessage()) + nullToEmpty(partial.getWarningMessage());
        b.wrongMessage(wrong);

        List<String> serviceDateLines = new ArrayList<>();
        String serviceDate = request.getParameter("service_date");
        if (serviceDate != null) {
            for (String line : serviceDate.split("\\n")) {
                serviceDateLines.add(line);
            }
        }
        b.serviceDateLines(serviceDateLines);
    }

    private void populateServiceCodeAndPercRows(HttpServletRequest request,
                                                String billReferalDate,
                                                BillingONReviewViewModel.Builder b,
                                                boolean codeValid) {
        Properties oscarVariables = CarlosProperties.getInstance();
        Properties gstProp;
        try {
            gstProp = new GstControl2Action().readDatabase();
        } catch (RuntimeException e) {
            MiscUtils.getLogger().warn("BillingONReviewDataAssembler: GstControl2Action.readDatabase failed", e);
            gstProp = new Properties();
        }
        String percent = gstProp.getProperty("gstPercent", "");
        b.gstPercent(percent);
        GstReport gstRep = new GstReport();

        @SuppressWarnings("unchecked")
        Vector<String>[] vecServiceParam = new Vector[3];
        if ("yes".equals(oscarVariables.getProperty("onBillingSingleClick", ""))) {
            vecServiceParam[0] = new Vector<>();
            vecServiceParam[1] = new Vector<>();
            vecServiceParam[2] = new Vector<>();
        } else {
            vecServiceParam = reviewPrep.getRequestFormCodeVec(request, "xml_", "1", "1");
        }

        Vector<String>[] vecServiceParam0 = reviewPrep.getRequestCodeVec(
                request, "serviceCode", "serviceUnit", "serviceAt", BillingDataHlp.FIELD_SERVICE_NUM);
        vecServiceParam[0].addAll(vecServiceParam0[0]);
        vecServiceParam[1].addAll(vecServiceParam0[1]);
        vecServiceParam[2].addAll(vecServiceParam0[2]);

        TreeMap<String, Integer> mapServiceParam = new TreeMap<>();
        for (int i = 0; i < vecServiceParam[0].size(); i++) {
            mapServiceParam.put(vecServiceParam[0].get(i), i);
        }
        boolean dupServiceCode = mapServiceParam.size() != vecServiceParam[0].size();
        b.dupServiceCode(dupServiceCode);

        Vector<Hashtable> v = new Vector<>();
        for (int ii = 0; ii < vecServiceParam[0].size(); ii++) {
            Hashtable h = new Hashtable();
            h.put("serviceCode", vecServiceParam[0].get(ii));
            h.put("serviceUnit", vecServiceParam[1].get(ii));
            h.put("serviceAt", vecServiceParam[2].get(ii));
            h.put("billReferenceDate", nullToEmpty(billReferalDate));
            v.add(h);
        }
        Collections.sort(v, new BillingSortComparator());

        vecServiceParam[0] = new Vector<>();
        vecServiceParam[1] = new Vector<>();
        vecServiceParam[2] = new Vector<>();
        for (int ii = 0; ii < v.size(); ii++) {
            Hashtable h = v.get(ii);
            vecServiceParam[0].add((String) h.get("serviceCode"));
            vecServiceParam[1].add((String) h.get("serviceUnit"));
            vecServiceParam[2].add((String) h.get("serviceAt"));
        }
        b.totalItem(vecServiceParam[0].size());

        Vector vecCodeItem = reviewPrep.getServiceCodeReviewVec(
                vecServiceParam[0], vecServiceParam[1], vecServiceParam[2], billReferalDate);
        Vector vecPercCodeItem = reviewPrep.getPercCodeReviewVec(
                vecServiceParam[0], vecServiceParam[1], vecCodeItem, billReferalDate);

        Properties propCodeDesc = new JdbcBillingCodeImpl().getCodeDescByNames(vecServiceParam[0]);
        Map<String, String> codeDescMap = new HashMap<>();
        for (String key : propCodeDesc.stringPropertyNames()) {
            codeDescMap.put(key, propCodeDesc.getProperty(key, ""));
        }
        b.codeDescriptions(codeDescMap);

        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal gstBilledTotal = BigDecimal.ZERO;

        List<BillingONReviewViewModel.ServiceCodeRow> serviceRows = new ArrayList<>();
        List<BillingONReviewViewModel.PercCodeRow> percRows = new ArrayList<>();
        List<BillingONReviewViewModel.PercJsHandler> percJsHandlers = new ArrayList<>();

        boolean bPerc = false;
        int n = 0;
        int nCode = 0;
        int nPerc = 0;
        for (int i = 0; i < vecServiceParam[0].size(); i++) {
            String codeName = vecServiceParam[0].get(i);
            if (nCode < vecCodeItem.size()
                    && codeName.equals(((BillingReviewCodeItem) vecCodeItem.get(nCode)).getCodeName())) {
                n++;
                BillingReviewCodeItem item = (BillingReviewCodeItem) vecCodeItem.get(nCode);
                String codeDescription = nullToEmpty(item.getCodeDescription());
                String codeUnit = nullToEmpty(item.getCodeUnit());
                String codeFee = nullToEmpty(item.getCodeFee());
                String codeTotalStr = nullToEmpty(item.getCodeTotal());
                String warning = nullToEmpty(item.getMsg());
                String codeAt = nullToEmpty(item.getCodeAt());

                String gstFlag = gstRep.getGstFlag(codeName, billReferalDate);
                BigDecimal cTotal = parseBigDecimal(codeTotalStr);
                boolean gstApplied = "1".equals(gstFlag);
                if (gstApplied) {
                    BigDecimal perc = parseBigDecimal(percent);
                    BigDecimal hund = new BigDecimal(100);
                    BigDecimal stotal = cTotal.multiply(perc).divide(hund, 4, RoundingMode.HALF_UP);
                    gstTotal = gstTotal.add(stotal).setScale(2, RoundingMode.HALF_UP);
                    stotal = stotal.add(cTotal).setScale(2, RoundingMode.HALF_UP);
                    codeTotalStr = stotal.toString();
                    gstBilledTotal = gstBilledTotal.add(stotal).setScale(2, RoundingMode.HALF_UP);
                } else {
                    gstBilledTotal = gstBilledTotal.add(cTotal).setScale(2, RoundingMode.HALF_UP);
                }
                serviceRows.add(new BillingONReviewViewModel.ServiceCodeRow(
                        i, n, codeName, codeUnit, codeFee, codeTotalStr, codeAt,
                        codeDescription, warning, gstApplied, codeValid));
                nCode++;
            } else if (nPerc < vecPercCodeItem.size()
                    && codeName.equals(((BillingReviewPercItem) vecPercCodeItem.get(nPerc)).getCodeName())) {
                bPerc = true;
                BillingReviewPercItem percItem = (BillingReviewPercItem) vecPercCodeItem.get(nPerc);
                String percFee = nullToEmpty(percItem.getCodeFee());
                Vector vecPercFee = percItem.getVecCodeFee() == null ? new Vector() : percItem.getVecCodeFee();
                Vector vecPercTotal = percItem.getVecCodeTotal() == null ? new Vector() : percItem.getVecCodeTotal();
                String codeUnit = nullToEmpty(percItem.getCodeUnit());

                List<BillingONReviewViewModel.PercSegment> segments = new ArrayList<>();
                int unitInt = parseIntSafe(codeUnit, 0);
                for (int j = 0; j < vecPercTotal.size(); j++) {
                    String pt = String.valueOf((Float.parseFloat((String) vecPercTotal.get(j))) * unitInt);
                    String factor = j < vecPercFee.size() ? String.valueOf(vecPercFee.get(j)) : "";
                    segments.add(new BillingONReviewViewModel.PercSegment(pt, factor));
                }

                String nMin = percItem.getCodeMinFee();
                String nMax = percItem.getCodeMaxFee();
                nMin = (nMin == null || nMin.isEmpty()) ? "0" : nMin;
                nMax = (nMax == null || nMax.isEmpty()) ? "9999" : nMax;
                percRows.add(new BillingONReviewViewModel.PercCodeRow(
                        i, codeName, codeUnit, percFee, nMin, nMax, segments));
                percJsHandlers.add(new BillingONReviewViewModel.PercJsHandler(
                        String.valueOf(i), nMin, nMax));
                nPerc++;
            }
        }
        b.serviceCodeRows(serviceRows);
        b.percCodeRows(percRows);
        b.percRendered(bPerc);
        b.percJsHandlers(percJsHandlers);
        b.gstTotal(gstTotal.toString());
        b.gstBilledTotal(gstBilledTotal.toString());
    }

    private void populateBillingNotesAndPaymentInfo(HttpServletRequest request,
                                                    BillingONReviewViewModel.Builder b,
                                                    boolean codeValid,
                                                    Map<String, BillingONReviewViewModel.ProviderName> providerNames) {
        String xmlBilltype = request.getParameter("xml_billtype");
        boolean publicPayer = xmlBilltype != null && xmlBilltype.matches("ODP.*|WCB.*|NOT.*|BON.*");
        boolean privatePayer = xmlBilltype != null && !publicPayer;
        boolean bMultisites = IsPropertiesOn.isMultisitesEnable();

        String tempLoc;
        if (publicPayer) {
            if (!bMultisites) {
                CarlosProperties props = CarlosProperties.getInstance();
                boolean bMoreAddr = !props.getProperty("scheduleSiteID", "").isEmpty();
                if (bMoreAddr) {
                    tempLoc = request.getParameter("siteId") != null
                            ? request.getParameter("siteId").trim() : "";
                } else {
                    tempLoc = props.getProperty("BILLING_NOTE", "");
                }
            } else {
                tempLoc = nullToEmpty(request.getParameter("site"));
            }
        } else if (privatePayer) {
            tempLoc = CarlosProperties.getInstance().getProperty("BILLING_NOTE", "");
        } else {
            tempLoc = "";
        }
        b.billingNotes(tempLoc);

        if (privatePayer && codeValid) {
            try {
                List<String> al = new JdbcBillingPageUtil().getPaymentType();
                List<BillingONReviewViewModel.PaymentType> paymentTypes = new ArrayList<>();
                for (int i = 0; i + 1 < al.size(); i = i + 2) {
                    paymentTypes.add(new BillingONReviewViewModel.PaymentType(al.get(i), al.get(i + 1)));
                }
                b.paymentTypes(paymentTypes);
            } catch (RuntimeException e) {
                MiscUtils.getLogger().warn("BillingONReviewDataAssembler: getPaymentType failed", e);
                b.paymentTypes(Collections.emptyList());
            }

            String clinicAddress = resolveClinicAddress(request, bMultisites);
            b.clinicAddress(clinicAddress);

            String providerNo = request.getParameter("xml_provider");
            if (providerNo != null) {
                int sep = providerNo.indexOf("|");
                if (sep >= 0) providerNo = providerNo.substring(0, sep);
            }
            b.payeeProviderNo(nullToEmpty(providerNo));

            String lname = "";
            String fname = "";
            if (providerNo != null) {
                BillingONReviewViewModel.ProviderName provName = providerNames.get(providerNo);
                if (provName != null) {
                    lname = provName.lastName();
                    fname = provName.firstName();
                }
            }
            b.payeeName((fname + " " + lname).trim());

            String configPayee = CarlosProperties.getInstance().getProperty("PAYEE", "").trim();
            b.payeeFromConfig(configPayee);
            b.payeeFromConfigSet(!configPayee.isEmpty());
        }
    }

    private String resolveClinicAddress(HttpServletRequest request, boolean bMultisites) {
        try {
            String userNo = (String) request.getSession().getAttribute("user");
            RxProviderData.Provider provider = userNo == null ? null
                    : new RxProviderData().getProvider(userNo);
            String strClinicAddr;
            if (provider == null) {
                strClinicAddr = "";
            } else {
                strClinicAddr = nullToEmpty(provider.getClinicName()).replaceAll("\\(\\d{6}\\)", "") + "\n"
                        + nullToEmpty(provider.getClinicAddress()) + "\n"
                        + nullToEmpty(provider.getClinicCity()) + "," + nullToEmpty(provider.getClinicProvince()) + "\n"
                        + nullToEmpty(provider.getClinicPostal()) + "\n"
                        + "Tel: " + nullToEmpty(provider.getClinicPhone()) + "\n"
                        + "Fax: " + nullToEmpty(provider.getClinicFax());
            }

            if (bMultisites) {
                String siteName = request.getParameter("site");
                SiteDao siteDao = SpringUtils.getBean(SiteDao.class);
                List<Site> sites = siteDao.getActiveSitesByProviderNo(
                        (String) request.getSession().getAttribute("user"));
                Site s = ApptUtil.getSiteFromName(sites, siteName);
                if (s == null) return strClinicAddr;
                return s.getName() + "\n" + s.getAddress() + "\n"
                        + s.getCity() + ", " + s.getProvince() + " " + s.getPostal()
                        + "\nTel: " + s.getPhone() + "\nFax: " + s.getFax();
            }

            String siteID = request.getParameter("siteId");
            CarlosProperties props2 = CarlosProperties.getInstance();
            if (siteID != null && props2.getProperty("clinicSatelliteCity") != null) {
                String[] clinicCity = props2.getProperty("clinicSatelliteCity", "").split("\\|");
                int siteFlag = 0;
                for (int i = 0; i < clinicCity.length; i++) {
                    if (siteID.equals(clinicCity[i])) siteFlag = i;
                }
                String[] temp0 = props2.getProperty("clinicSatelliteName", "").split("\\|");
                String[] temp1 = props2.getProperty("clinicSatelliteAddress", "").split("\\|");
                String[] temp3 = props2.getProperty("clinicSatelliteProvince", "").split("\\|");
                String[] temp4 = props2.getProperty("clinicSatellitePostal", "").split("\\|");
                String[] temp5 = props2.getProperty("clinicSatellitePhone", "").split("\\|");
                String[] temp6 = props2.getProperty("clinicSatelliteFax", "").split("\\|");
                if (siteFlag < clinicCity.length && siteFlag < temp0.length) {
                    return temp0[siteFlag] + "\n"
                            + (siteFlag < temp1.length ? temp1[siteFlag] : "") + "\n"
                            + clinicCity[siteFlag] + ", "
                            + (siteFlag < temp3.length ? temp3[siteFlag] : "") + " "
                            + (siteFlag < temp4.length ? temp4[siteFlag] : "")
                            + "\nTel: " + (siteFlag < temp5.length ? temp5[siteFlag] : "")
                            + "\nFax: " + (siteFlag < temp6.length ? temp6[siteFlag] : "");
                }
            }
            return strClinicAddr;
        } catch (RuntimeException e) {
            MiscUtils.getLogger().warn("BillingONReviewDataAssembler: clinicAddress resolution failed", e);
            return "";
        }
    }

    private void populateAllRequestParams(HttpServletRequest request, BillingONReviewViewModel.Builder b) {
        List<BillingONReviewViewModel.ParamPair> all = new ArrayList<>();
        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {
            String name = e.nextElement();
            String value = request.getParameter(name);
            all.add(new BillingONReviewViewModel.ParamPair(name, value == null ? "" : value));
        }
        b.allRequestParams(all);
    }

    private String resolveProviderDisplay(HttpServletRequest request, String providerNo) {
        if (providerNo == null || providerNo.isEmpty()) return "";
        Object sessionBean = request.getSession().getAttribute("providerBean");
        String name = "";
        if (sessionBean instanceof Properties props) {
            name = props.getProperty(providerNo, "");
        }
        if (name.isEmpty()) {
            try {
                Provider p = providerDao.getProvider(providerNo);
                if (p != null) {
                    name = p.getFormattedName();
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().debug("BillingONReviewDataAssembler: providerDao lookup failed", e);
            }
        }
        return nullToEmpty(name);
    }

    private static String extractAfterPipe(String s) {
        if (s == null) return "";
        int idx = s.indexOf("|");
        return idx >= 0 ? s.substring(idx + 1) : "";
    }

    private static String extractBeforePipe(String s) {
        if (s == null) return "";
        int idx = s.indexOf("|");
        return idx >= 0 ? s.substring(0, idx).trim() : "";
    }

    private static BigDecimal parseBigDecimal(String s) {
        try { return new BigDecimal(nullToEmpty(s)); }
        catch (NumberFormatException e) { return BigDecimal.ZERO; }
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return fallback; }
    }
}
