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

import jakarta.servlet.http.HttpServletRequest;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.ArrayList;
import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.appt.ApptUtil;
import io.github.carlos_emr.carlos.billings.ca.on.service.GstSettingsService;
import io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingReviewServiceParam;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingDemographicSummary;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnReviewViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReferralDoctor;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewCodeItem;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReviewPercentageItem;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingReviewFeeComparator;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.ServiceCodeLoader;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.commn.IsPropertiesOn;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.SiteDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.prescript.data.RxProviderData;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnRequestParameters;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingOnReviewValidator;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewQueryService;

/**
 * Assembles {@link BillingOnReviewViewModel} for {@code billingONReview.jsp}.
 *
 * <p>Pure read of request state into a view model. Encapsulates all the data
 * preparation that previously lived in the JSP's body scriptlets.</p>
 *
 * @since 2026-04-24
 *        2026-04-25 (full body-scriptlet drain expansion)
 */
@org.springframework.stereotype.Service
public class BillingOnReviewViewModelAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final BillingReviewQueryService reviewPrep;
    private final BillingOnReviewValidator validator;
    private final ServiceCodeLoader serviceCodeLoader;
    private final BillingOnLookupService lookupService;
    private final SiteDao siteDao;
    private final GstSettingsService gstSettingsService;
    private final GstReportService gstReport;
    private final BillingOnClaimLoader claimLoader;

    public BillingOnReviewViewModelAssembler(DemographicDao demographicDao,
                                 ProviderDao providerDao,
                                 BillingReviewQueryService reviewPrep,
                                 BillingOnReviewValidator validator,
                                 ServiceCodeLoader serviceCodeLoader,
                                 BillingOnLookupService lookupService,
                                 SiteDao siteDao,
                                 GstSettingsService gstSettingsService,
                                 GstReportService gstReport,
                                 BillingOnClaimLoader claimLoader) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.reviewPrep = reviewPrep;
        this.validator = validator;
        this.serviceCodeLoader = serviceCodeLoader;
        this.lookupService = lookupService;
        this.siteDao = siteDao;
        this.gstSettingsService = gstSettingsService;
        this.gstReport = gstReport;
        this.claimLoader = claimLoader;
    }

    public BillingOnReviewViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));

        String dxDesc = reviewPrep.getDxDescription(dxCode);
        BillingOnReviewViewModel.Builder b = BillingOnReviewViewModel.builder()
                .dxCode(dxCode)
                .dxDesc(dxDesc == null ? "" : dxDesc);

        loadProvider(request, b);
        loadDemographic(demoNo, request.getParameter("DemoSex"), b);

        String billRefDate = firstNonEmpty(
                request.getParameter("service_date"),
                request.getParameter("appointment_date"),
                request.getParameter("billReferalDate"));
        BillingOnReviewValidator.Result validation = validator.validate(request, demoNo, billRefDate);
        b.validationMessages(validation.messages())
                .codeValid(validation.codeValid());

        Map<String, BillingOnReviewViewModel.ProviderName> providerNames = new HashMap<>();
        for (Provider p : providerDao.getProvidersWithNonEmptyCredentials()) {
            providerNames.put(nullToEmpty(p.getProviderNo()),
                    new BillingOnReviewViewModel.ProviderName(
                            nullToEmpty(p.getLastName()),
                            nullToEmpty(p.getFirstName())));
        }
        b.providerNameLookup(providerNames);

        populateRequestParamEchoes(request, b);
        String loggedInUserNo = loggedInInfo == null ? "" : nullToEmpty(loggedInInfo.getLoggedInProviderNo());
        b.loggedInUserNo(loggedInUserNo);
        populateLabelFields(request, b);
        populateDemoHeader(request, b);
        populateServiceCodeAndPercRows(request, billRefDate, b, validation.codeValid());
        populateBillingNotesAndPaymentInfo(request, b, validation.codeValid(), providerNames, loggedInUserNo);
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

    private void loadProvider(HttpServletRequest request, BillingOnReviewViewModel.Builder b) {
        String providerNo = BillingOnRequestParameters.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        b.providerView(providerNo);

        Provider p = providerNo.isEmpty() ? null : providerDao.getProvider(providerNo);
        if (p != null) {
            b.providerOhip(nullToEmpty(p.getOhipNo()));
            b.providerRma(nullToEmpty(p.getRmaNo()));
        }
    }

    private void loadDemographic(String demoNo, String demoSexParam, BillingOnReviewViewModel.Builder b) {
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
        List<BillingOnReviewViewModel.ReviewAlert> reviewAlerts = new ArrayList<>();
        String errorFlag = "";

        // Canonical demographic projection (HC type defaulting, DOB padding,
        // raw passthrough). Review's sex normalization to "1"/"2" applies on
        // top of the factory's pass-through.
        BillingDemographicSummary summary = BillingDemographicSummary.fromDemographic(demo);
        b.demoFirst(summary.firstName())
                .demoLast(summary.lastName())
                .demoHin(summary.hin())
                .demoVer(summary.ver())
                .assignedProviderNo(nullToEmpty(demo.getProviderNo()))
                .patientAddress(buildPatientAddress(demo))
                .demoHcType(summary.hcType())
                .demoDobYy(summary.dobYy())
                .demoDobMm(summary.dobMm())
                .demoDobDd(summary.dobDd())
                .demoDob(summary.dob());

        String demoSex = summary.sex();
        if ("M".equals(demoSex)) {
            demoSex = "1";
        } else if ("F".equals(demoSex)) {
            demoSex = "2";
        }
        b.demoSex(demoSex);

        BillingReferralDoctor referral = BillingReferralDoctor.fromFamilyDoctor(demo.getFamilyDoctor());
        b.referralDoctorName(referral.name()).referralDoctorOhip(referral.ohip());
        String referralDoctorOhip = referral.ohip();

        if (demo.getHin() == null) {
            errorFlag = "1";
            appendReviewAlert(errorMessage, reviewAlerts, "Error: The patient does not have a HIN");
        } else if (demo.getHin().isEmpty()) {
            appendReviewAlert(warningMessage, reviewAlerts, "Warning: The patient does not have a HIN");
        }
        if (!referralDoctorOhip.isEmpty() && referralDoctorOhip.length() != 6) {
            appendReviewAlert(warningMessage, reviewAlerts, "Warning: the referral doctor's no is wrong.");
        }
        if (summary.dob().length() != 8) {
            errorFlag = "1";
            appendReviewAlert(errorMessage, reviewAlerts, "Error: The patient does not have a valid DOB.");
        }

        b.errorFlag(errorFlag)
                .errorMessage(errorMessage.toString())
                .warningMessage(warningMessage.toString())
                .reviewAlerts(reviewAlerts);
    }

    private static String buildPatientAddress(Demographic demo) {
        return nullToEmpty(demo.getFirstName()) + " " + nullToEmpty(demo.getLastName()) + "\n"
                + nullToEmpty(demo.getAddress()) + "\n"
                + nullToEmpty(demo.getCity()) + ", " + nullToEmpty(demo.getProvince()) + "\n"
                + nullToEmpty(demo.getPostal()) + "\n"
                + "Tel: " + nullToEmpty(demo.getPhone());
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static void appendReviewAlert(StringBuilder messages,
                                          List<BillingOnReviewViewModel.ReviewAlert> reviewAlerts,
                                          String message) {
        if (messages.length() > 0) {
            messages.append('\n');
        }
        messages.append(message);
        reviewAlerts.add(new BillingOnReviewViewModel.ReviewAlert("danger", message));
    }

    private static String combineMessages(String first, String second) {
        String a = nullToEmpty(first);
        String b = nullToEmpty(second);
        if (a.isEmpty()) {
            return b;
        }
        if (b.isEmpty()) {
            return a;
        }
        return a + "\n" + b;
    }

    // -- 2026-04-25 expansion helpers --------------------------------------

    private void populateRequestParamEchoes(HttpServletRequest request, BillingOnReviewViewModel.Builder b) {
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
                                     BillingOnReviewViewModel.Builder b) {
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

    private void populateDemoHeader(HttpServletRequest request, BillingOnReviewViewModel.Builder b) {
        BillingOnReviewViewModel partial = b.build();
        String demoSex = partial.getDemoSex();
        String sexLabel = "1".equals(demoSex) ? "Male" : "Female";
        b.demoSexLabel(sexLabel);

        String header = "DOB: " + partial.getDemoDobYy() + "/"
                + partial.getDemoDobMm() + "/" + partial.getDemoDobDd()
                + " HIN: " + partial.getDemoHin() + partial.getDemoVer();
        b.demoHeaderLine(header);

        String wrong = combineMessages(partial.getErrorMessage(), partial.getWarningMessage());
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
                                                BillingOnReviewViewModel.Builder b,
                                                boolean codeValid) {
        Properties oscarVariables = CarlosProperties.getInstance();
        // Sentinel for parseReviewMoney — flipped to true if any code total
        // or GST percent fails strict parsing, so the JSP can banner-warn
        // and gate Submit.
        boolean[] parseFailed = {false};
        BigDecimal currentGst;
        try {
            currentGst = gstSettingsService.getCurrentPercent();
        } catch (RuntimeException e) {
            MiscUtils.getLogger().warn("BillingOnReviewViewModelAssembler: GstSettingsService.getCurrentPercent failed", e);
            currentGst = null;
        }
        String percent = currentGst == null ? "" : currentGst.toPlainString();
        b.gstPercent(percent);
        GstReportService gstRep = gstReport;

        List<BillingReviewServiceParam> serviceParams =
                "yes".equals(oscarVariables.getProperty("onBillingSingleClick", ""))
                        ? new ArrayList<>()
                        : new ArrayList<>(reviewPrep.getRequestFormCodes(request, "xml_", "1", "1"));
        serviceParams.addAll(reviewPrep.getRequestCodes(
                request, "serviceCode", "serviceUnit", "serviceAt", BillingOnConstants.FIELD_SERVICE_NUM));

        TreeMap<String, Integer> distinctCodeIndex = new TreeMap<>();
        for (int i = 0; i < serviceParams.size(); i++) {
            distinctCodeIndex.put(serviceParams.get(i).code(), i);
        }
        b.dupServiceCode(distinctCodeIndex.size() != serviceParams.size());

        serviceParams.sort(new BillingReviewFeeComparator(claimLoader, billReferalDate));
        b.totalItem(serviceParams.size());

        List<BillingReviewCodeItem> codeItems =
                reviewPrep.getServiceCodeReviewItems(serviceParams, billReferalDate);
        List<BillingReviewPercentageItem> percentageCodeItems =
                reviewPrep.getPercentageCodeReviewItems(serviceParams, codeItems, billReferalDate);

        ArrayList<String> serviceCodeNames = serviceParams.stream()
                .map(BillingReviewServiceParam::code)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        Properties propCodeDesc = serviceCodeLoader.getCodeDescByNames(serviceCodeNames);
        Map<String, String> codeDescMap = new HashMap<>();
        for (String key : propCodeDesc.stringPropertyNames()) {
            codeDescMap.put(key, propCodeDesc.getProperty(key, ""));
        }
        b.codeDescriptions(codeDescMap);

        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal gstBilledTotal = BigDecimal.ZERO;

        List<BillingOnReviewViewModel.ServiceCodeRow> serviceRows = new ArrayList<>();
        List<BillingOnReviewViewModel.PercCodeRow> percRows = new ArrayList<>();
        List<BillingOnReviewViewModel.PercJsBinding> percJsHandlers = new ArrayList<>();

        boolean bPerc = false;
        int n = 0;
        int nCode = 0;
        int nPerc = 0;
        for (int i = 0; i < serviceParams.size(); i++) {
            String codeName = serviceParams.get(i).code();
            if (nCode < codeItems.size()
                    && codeName.equals(codeItems.get(nCode).getCodeName())) {
                n++;
                BillingReviewCodeItem item = codeItems.get(nCode);
                String codeDescription = nullToEmpty(item.getCodeDescription());
                String codeUnit = nullToEmpty(item.getCodeUnit());
                String codeFee = nullToEmpty(item.getCodeFee());
                String codeTotalStr = nullToEmpty(item.getCodeTotal());
                String warning = nullToEmpty(item.getMsg());
                String codeAt = nullToEmpty(item.getCodeAt());

                String gstFlag = gstRep.getGstFlag(codeName, billReferalDate);
                BigDecimal cTotal = parseReviewMoney(codeTotalStr, "codeTotal[" + codeName + "]", parseFailed);
                boolean gstApplied = "1".equals(gstFlag);
                if (gstApplied) {
                    BigDecimal perc = parseReviewMoney(percent, "gstPercent", parseFailed);
                    BigDecimal hund = new BigDecimal(100);
                    BigDecimal stotal = cTotal.multiply(perc).divide(hund, 4, RoundingMode.HALF_UP);
                    gstTotal = gstTotal.add(stotal).setScale(2, RoundingMode.HALF_UP);
                    stotal = stotal.add(cTotal).setScale(2, RoundingMode.HALF_UP);
                    codeTotalStr = stotal.toString();
                    gstBilledTotal = gstBilledTotal.add(stotal).setScale(2, RoundingMode.HALF_UP);
                } else {
                    gstBilledTotal = gstBilledTotal.add(cTotal).setScale(2, RoundingMode.HALF_UP);
                }
                serviceRows.add(new BillingOnReviewViewModel.ServiceCodeRow(
                        i, n, codeName, codeUnit, codeFee, codeTotalStr, codeAt,
                        codeDescription, warning, gstApplied, codeValid));
                nCode++;
            } else if (nPerc < percentageCodeItems.size()
                    && codeName.equals(percentageCodeItems.get(nPerc).getCodeName())) {
                bPerc = true;
                BillingReviewPercentageItem percItem = percentageCodeItems.get(nPerc);
                String percFee = nullToEmpty(percItem.getCodeFee());
                List<String> percentageFees = percItem.getCodeFees() == null ? List.of() : percItem.getCodeFees();
                List<String> percentageTotals = percItem.getCodeTotals() == null ? List.of() : percItem.getCodeTotals();
                String codeUnit = nullToEmpty(percItem.getCodeUnit());

                List<BillingOnReviewViewModel.PercSegment> segments = new ArrayList<>();
                int unitInt = parseIntSafe(codeUnit, 0);
                for (int j = 0; j < percentageTotals.size(); j++) {
                    String pt = String.valueOf((Float.parseFloat(percentageTotals.get(j))) * unitInt);
                    String factor = j < percentageFees.size() ? String.valueOf(percentageFees.get(j)) : "";
                    segments.add(new BillingOnReviewViewModel.PercSegment(pt, factor));
                }

                String nMin = percItem.getCodeMinFee();
                String nMax = percItem.getCodeMaxFee();
                nMin = (nMin == null || nMin.isEmpty()) ? "0" : nMin;
                nMax = (nMax == null || nMax.isEmpty()) ? "9999" : nMax;
                percRows.add(new BillingOnReviewViewModel.PercCodeRow(
                        i, codeName, codeUnit, percFee, nMin, nMax, segments));
                percJsHandlers.add(new BillingOnReviewViewModel.PercJsBinding(
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
        b.totalsParseFailed(parseFailed[0]);
    }

    private void populateBillingNotesAndPaymentInfo(HttpServletRequest request,
                                                    BillingOnReviewViewModel.Builder b,
                                                    boolean codeValid,
                                                    Map<String, BillingOnReviewViewModel.ProviderName> providerNames,
                                                    String loggedInUserNo) {
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
                List<String> al = lookupService.getPaymentType();
                List<BillingOnReviewViewModel.PaymentType> paymentTypes = new ArrayList<>();
                for (int i = 0; i + 1 < al.size(); i = i + 2) {
                    paymentTypes.add(new BillingOnReviewViewModel.PaymentType(al.get(i), al.get(i + 1)));
                }
                b.paymentTypes(paymentTypes);
            } catch (RuntimeException e) {
                // ERROR (not WARN): an empty payment-type dropdown leaves the
                // private-payer review unable to proceed and the operator
                // with no diagnostic. Bump severity so the failure is visible
                // in default log levels; a follow-up should add a
                // paymentTypeLookupFailed flag + JSP banner so the user
                // sees "Payment types unavailable — contact admin" inline.
                MiscUtils.getLogger().error(
                        "BillingOnReviewViewModelAssembler: getPaymentType failed; rendering empty dropdown", e);
                b.paymentTypes(Collections.emptyList());
            }

            String clinicAddress = resolveClinicAddress(request, bMultisites, loggedInUserNo);
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
                BillingOnReviewViewModel.ProviderName provName = providerNames.get(providerNo);
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

    private String resolveClinicAddress(HttpServletRequest request, boolean bMultisites, String loggedInUserNo) {
        try {
            String userNo = loggedInUserNo == null || loggedInUserNo.isEmpty() ? null : loggedInUserNo;
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
                List<Site> sites = siteDao.getActiveSitesByProviderNo(userNo);
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
            // ERROR (not WARN): clinic address is a required field on
            // private-payer printed receipts. Empty string here renders a
            // headerless receipt that operators may not notice until the
            // patient queries.
            MiscUtils.getLogger().error("BillingOnReviewViewModelAssembler: clinicAddress resolution failed; printed receipt will lack clinic header", e);
            return "";
        }
    }

    private void populateAllRequestParams(HttpServletRequest request, BillingOnReviewViewModel.Builder b) {
        List<BillingOnReviewViewModel.ParamPair> all = new ArrayList<>();
        for (Enumeration<String> e = request.getParameterNames(); e.hasMoreElements(); ) {
            String name = e.nextElement();
            String value = request.getParameter(name);
            all.add(new BillingOnReviewViewModel.ParamPair(name, value == null ? "" : value));
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
                // The peer catches in this assembler all log at WARN; DEBUG
                // would mask a DB outage during provider-name lookup, leaving
                // the operator with a blank name and no signal in the log.
                MiscUtils.getLogger().warn("BillingOnReviewViewModelAssembler: providerDao lookup failed for providerNo {}",
                        LogSanitizer.sanitize(providerNo), e);
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

    /**
     * Strict-parse variant for the Review screen's GST / code-total inputs.
     * Returns {@link BigDecimal#ZERO} on failure (matches legacy display
     * behaviour) but flips {@code parseFailed[0]} to {@code true} and emits an
     * ERROR log so the assembler can surface a "totals not trusted" banner
     * via {@link BillingOnReviewViewModel#isTotalsParseFailed()}.
     */
    private static BigDecimal parseReviewMoney(String s, String fieldName, boolean[] parseFailed) {
        String trimmed = nullToEmpty(s);
        try {
            return new BigDecimal(trimmed);
        } catch (NumberFormatException e) {
            parseFailed[0] = true;
            MiscUtils.getLogger().error(
                    "BillingOnReviewViewModel: malformed numeric input on Review screen — field={}, value=\"{}\". GST/totals will read 0; submission must be gated.",
                    fieldName, io.github.carlos_emr.carlos.utility.LogSanitizer.sanitize(trimmed));
            return BigDecimal.ZERO;
        }
    }

    private static int parseIntSafe(String s, int fallback) {
        try { return Integer.parseInt(s); }
        catch (NumberFormatException e) { return fallback; }
    }
}
