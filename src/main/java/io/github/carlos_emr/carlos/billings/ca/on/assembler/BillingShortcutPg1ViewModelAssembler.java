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

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.Misc;
import io.github.carlos_emr.SxmlMisc;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingDemographicSummary;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingReferralDoctor;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingShortcutPg1ViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimLoader;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServicePremiumDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.CtlBillingServicePremium;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnRequestParameters;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Assembles {@link BillingShortcutPg1ViewModel} for {@code billingShortcutPg1.jsp}.
 *
 * <p>Encapsulates the 14 DAO lookups, billing-history walk, service-code grid
 * prep, and demographic-driven validation messaging that previously lived in
 * the 350-line top scriptlet of the legacy JSP. The shortcut page is the
 * fast-track hospital-billing variant of {@code billingON.jsp}, so the prep
 * shape mirrors {@link BillingOnFormViewModelAssembler} where it can — both pull
 * provider lists, location lists, billing-service grids — with shortcut-only
 * differences (hospital-billing visit-type override, legacy
 * {@code BillingDao.findActiveBillingsByDemoNo} history walk).</p>
 *
 * @since 2026-04-24
 */
@org.springframework.stereotype.Service
public class BillingShortcutPg1ViewModelAssembler {

    private final DemographicDao demographicDao;
    private final ProviderDao providerDao;
    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final BillingServiceDao billingServiceDao;
    private final CtlBillingServicePremiumDao ctlBillingServicePremiumDao;
    private final ClinicLocationDao clinicLocationDao;
    private final ProfessionalSpecialistDao professionalSpecialistDao;
    // 3 new DAOs added so the JSP no longer needs SpringUtils.getBean
    // for the side-panel service-type list, the dx-code panel list, or the
    // clinic-nbr dropdown. ProviderDao for the comments XML stays — it was
    // already a field above, the JSP reuse just uses the pre-loaded value.
    private final io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao ctlBillingServiceDao;
    private final io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao diagnosticCodeDao;
    private final io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao clinicNbrDao;
    private final BillingOnClaimLoader billingClaimQueryService;

    public BillingShortcutPg1ViewModelAssembler(DemographicDao demographicDao,
                                    ProviderDao providerDao,
                                    BillingDao billingDao,
                                    BillingDetailDao billingDetailDao,
                                    BillingServiceDao billingServiceDao,
                                    CtlBillingServicePremiumDao ctlBillingServicePremiumDao,
                                    ClinicLocationDao clinicLocationDao,
                                    ProfessionalSpecialistDao professionalSpecialistDao,
                                    io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao ctlBillingServiceDao,
                                    io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao diagnosticCodeDao,
                                    io.github.carlos_emr.carlos.commn.dao.ClinicNbrDao clinicNbrDao,
                                    BillingOnClaimLoader billingClaimQueryService) {
        this.demographicDao = demographicDao;
        this.providerDao = providerDao;
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.billingServiceDao = billingServiceDao;
        this.ctlBillingServicePremiumDao = ctlBillingServicePremiumDao;
        this.clinicLocationDao = clinicLocationDao;
        this.professionalSpecialistDao = professionalSpecialistDao;
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.diagnosticCodeDao = diagnosticCodeDao;
        this.clinicNbrDao = clinicNbrDao;
        this.billingClaimQueryService = billingClaimQueryService;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public BillingShortcutPg1ViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String userProviderNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        CarlosProperties props = CarlosProperties.getInstance();
        // Hospital-billing only: the original code carried a `hospitalBilling`
        // boolean toggle but it was hardcoded to true and never reassigned, so
        // the non-hospital fallback branches were dead. Inline the hospital
        // values to remove the misleading seam — when a real toggle lands
        // (likely derived from ctlBillForm or a form param), introduce it
        // explicitly rather than re-resurrecting the dead branch.
        String clinicView = nullToEmpty(props.getProperty("clinic_hospital", ""));
        String clinicNo = nullToEmpty(props.getProperty("clinic_no", ""));
        String visitType = "02";

        String apptNo = nullToEmpty(request.getParameter("appointment_no"));
        String demoName = nullToEmpty(request.getParameter("demographic_name"));
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));
        String apptProviderNo = nullToEmpty(request.getParameter("apptProvider_no"));
        String apptDate = nullToEmpty(request.getParameter("appointment_date"));
        String startTime = nullToEmpty(request.getParameter("start_time"));
        String ctlBillForm = nullToEmpty(request.getParameter("billForm"));
        String assignedProviderNo = nullToEmpty(request.getParameter("assgProvider_no"));

        // providerview default order: xml_provider param > providerview param >
        // logged-in provider. The legacy View2Action defaulted to userProviderNo
        // when both params were absent; preserve that.
        // xml_provider is "providerNo|ohipNo" from the picker — strip the suffix
        // and don't let an empty value clobber a populated providerview.
        String providerView = BillingOnRequestParameters.extractProviderNo(
                request.getParameter("xml_provider"),
                request.getParameter("providerview"));
        if (providerView.isEmpty()) {
            providerView = nullToEmpty(userProviderNo);
        }

        DemographicLoad demoLoad = loadDemographic(demoNo, request.getParameter("DemoSex"));
        if (!demoLoad.assignedProviderOverride.isEmpty()) {
            assignedProviderNo = demoLoad.assignedProviderOverride;
        }

        // Billing history (5 most recent)
        List<Properties> billingHistory = new ArrayList<>();
        List<Properties> billingHistoryDetails = new ArrayList<>();
        // Tracks the catch at line ~289 — the JSP must render a banner if true,
        // so an empty history pane isn't mistaken for "patient was never billed
        // before" (duplicate-bill risk).
        boolean historyUnavailable = false;
        int historyPartialRowCount = 0;
        String referralDoctorOhip = demoLoad.referralDoctorOhip;
        boolean isNewOnBilling = "true".equals(props.getProperty("isNewONbilling", ""));

        // Per-row try/catch so a single malformed billing row doesn't wipe
        // the whole 5-bill history. Outer catch protects the DAO call itself
        // (DB outage); each row catch protects against per-row data-shape
        // regression (ClassCastException) and per-row corruption.
        try {
            if (!isNewOnBilling) {
                boolean firstReferral = true;
                Integer demoIdInt = ConversionUtils.fromIntString(demoNo);
                for (Billing b : billingDao.findActiveBillingsByDemoNo(demoIdInt, 5)) {
                    // Skip null rows up front rather than relying on the
                    // catch below: the DAO contract doesn't permit null
                    // entries, but a defensive explicit check makes the
                    // invariant obvious to static analyzers (which keep
                    // re-flagging the inner b.getId() deref) and avoids
                    // taking the NPE detour through the catch.
                    if (b == null) {
                        historyPartialRowCount++;
                        MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                                "BillingShortcutPg1: null Billing row in history for demo {}; skipping",
                                LogSafe.sanitize(demoNo));
                        continue;
                    }
                    // Capture bill id outside the try so any catch handler
                    // doesn't re-dereference `b` if a downstream call throws.
                    Object capturedBillId = b.getId();
                    try {
                        Properties p = new Properties();
                        p.setProperty("billing_no", "" + b.getId());
                        p.setProperty("visitdate", ConversionUtils.toDateString(b.getVisitDate()));
                        p.setProperty("billing_date", ConversionUtils.toDateString(b.getBillingDate()));
                        p.setProperty("update_date", ConversionUtils.toDateString(b.getUpdateDate()));
                        p.setProperty("visitType", nullToEmpty(b.getVisitType()));
                        p.setProperty("clinic_ref_code", nullToEmpty(b.getClinicRefCode()));
                        billingHistory.add(p);

                        if (firstReferral && "checked".equals(SxmlMisc.getXmlContent(b.getContent(), "xml_referral"))) {
                            firstReferral = false;
                            String rdohip = SxmlMisc.getXmlContent(b.getContent(), "rdohip");
                            if (rdohip != null) {
                                referralDoctorOhip = rdohip;
                            }
                        }
                    } catch (RuntimeException rowEx) {
                        historyPartialRowCount++;
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: skipping malformed billing row id={} for demo={}",
                                capturedBillId, demoNo, rowEx);
                    }
                }

                for (Properties hist : billingHistory) {
                    String billingNo = hist.getProperty("billing_no", "");
                    // Build the comma-joined dx and service-code summaries for one
                    // bill, deduplicating against the FULL accumulated set rather
                    // than just the last entry. The legacy JSP did `last.equals(...)`
                    // which broke as soon as any non-adjacent code matched a prior
                    // one — for instance "401, 402, 401" would emit "401, 402, 401"
                    // even though "401" already appeared.
                    java.util.LinkedHashSet<String> dxSeen = new java.util.LinkedHashSet<>();
                    java.util.LinkedHashSet<String> serSeen = new java.util.LinkedHashSet<>();
                    try {
                        for (BillingDetail bd : billingDetailDao.findByBillingNo(Integer.valueOf(ConversionUtils.fromIntString(billingNo)))) {
                            if (bd.getDiagnosticCode() != null && !bd.getDiagnosticCode().isEmpty()) {
                                dxSeen.add(bd.getDiagnosticCode());
                            }
                            if (bd.getServiceCode() != null && !bd.getServiceCode().isEmpty()) {
                                serSeen.add(bd.getServiceCode() + " x " + bd.getBillingUnit());
                            }
                        }
                    } catch (RuntimeException detailEx) {
                        historyPartialRowCount++;
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: detail lookup failed for billing_no={}",
                                billingNo, detailEx);
                    }
                    Properties detail = new Properties();
                    detail.setProperty("service_code", String.join(", ", serSeen));
                    detail.setProperty("diagnostic_code", String.join(", ", dxSeen));
                    billingHistoryDetails.add(detail);
                }
            } else {
                List<?> aL = billingClaimQueryService.getBillingHist(demoNo, 5, 0, null);
                // aL contains alternating pairs of (BillingClaimHeaderDto, BillingClaimItemDto).
                // Walk pairwise; per-pair try/catch so a single bad pair doesn't
                // strip the surrounding good rows.
                for (int i = 0; i + 1 < aL.size(); i += 2) {
                    try {
                        BillingClaimHeaderDto obj = (BillingClaimHeaderDto) aL.get(i);
                        BillingClaimItemDto iobj = (BillingClaimItemDto) aL.get(i + 1);
                        // Build BOTH Properties before adding either to the
                        // parent lists. If detail construction throws, neither
                        // list is mutated — keeps billingHistory and
                        // billingHistoryDetails strictly in sync at index
                        // boundaries so the JSP iteration can pair them
                        // positionally.
                        Properties p = new Properties();
                        p.setProperty("billing_no", nullToEmpty(String.valueOf(obj.getId())));
                        p.setProperty("billing_date", nullToEmpty(obj.billingDate()));
                        p.setProperty("visitdate", obj.admissionDate() == null ? "" : obj.admissionDate());
                        p.setProperty("visitType", nullToEmpty(obj.visitType()));
                        p.setProperty("clinic_ref_code", nullToEmpty(obj.facilityNumber()));
                        String updateDt = obj.updateDateTime();
                        p.setProperty("update_date", updateDt != null && updateDt.length() >= 10 ? updateDt.substring(0, 10) : nullToEmpty(updateDt));

                        Properties detail = new Properties();
                        detail.setProperty("service_code", nullToEmpty(iobj.serviceCode()));
                        detail.setProperty("diagnostic_code", nullToEmpty(iobj.getDx()));

                        billingHistory.add(p);
                        billingHistoryDetails.add(detail);
                    } catch (ClassCastException ccEx) {
                        historyPartialRowCount++;
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                                "Shortcut history: data-shape regression at pair index {} for demo={}",
                                i, demoNo, ccEx);
                    } catch (RuntimeException rowEx) {
                        historyPartialRowCount++;
                        io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().warn(
                                "Shortcut history: skipping malformed pair at index {} for demo={}",
                                i, demoNo, rowEx);
                    }
                }
            }
        } catch (RuntimeException rtEx) {
            // Outer DAO call failed (DB outage). Render with empty history
            // AND set the unavailable flag so the JSP can warn the provider —
            // an empty pane is otherwise indistinguishable from a clean slate
            // and creates a duplicate-bill risk.
            io.github.carlos_emr.carlos.utility.MiscUtils.getLogger().error(
                    "Shortcut billing history lookup failed for demo={}; rendering with empty history",
                    demoNo, rtEx);
            billingHistory.clear();
            billingHistoryDetails.clear();
            historyUnavailable = true;
            historyPartialRowCount = 0;
        }

        // Provider list (doctors with OHIP)
        List<Properties> providers = new ArrayList<>();
        for (Provider pr : providerDao.getDoctorsWithOhip()) {
            Properties p = new Properties();
            p.setProperty("last_name", nullToEmpty(pr.getLastName()));
            p.setProperty("first_name", nullToEmpty(pr.getFirstName()));
            p.setProperty("proOHIP", nullToEmpty(pr.getProviderNo()));
            providers.add(p);
        }

        // Clinic locations
        List<Properties> clinicLocations = new ArrayList<>();
        for (ClinicLocation cl : clinicLocationDao.findAll()) {
            Properties p = new Properties();
            p.setProperty("clinic_location_name", nullToEmpty(cl.getClinicLocationName()));
            p.setProperty("clinic_location_no", nullToEmpty(cl.getClinicLocationNo()));
            clinicLocations.add(p);
        }

        // Resolve referral doctor name (from history's last referral_ohip if available)
        String referralDoctorName = demoLoad.referralDoctorName;
        if (referralDoctorOhip != null && !referralDoctorOhip.trim().isEmpty()) {
            ProfessionalSpecialist specialist = professionalSpecialistDao.getByReferralNo(referralDoctorOhip);
            if (specialist != null) {
                // nullToEmpty both halves: a null lastName or firstName would
                // otherwise leak the literal "null" string into EL output via
                // the model. Matches the Provider null-coalesce pattern at
                // lines 291-293.
                referralDoctorName = nullToEmpty(specialist.getLastName())
                        + "," + nullToEmpty(specialist.getFirstName());
            }
        }

        // Default-value resolution for dxCode / visitType / clinicView / visitdate
        String dxCode = getDefaultValue(request.getParameter("dxCode"), billingHistoryDetails, "diagnostic_code");

        String xmlVisitType = getDefaultValue(request.getParameter("xml_visittype"), billingHistory, "visitType");
        if (!xmlVisitType.isEmpty()) {
            visitType = xmlVisitType;
        }
        String xmlLocation = getDefaultValue(request.getParameter("xml_location"), billingHistory, "clinic_ref_code");
        if (!xmlLocation.isEmpty()) {
            clinicView = xmlLocation;
        }
        String xmlVdate = getDefaultValue(request.getParameter("xml_vdate"), billingHistory, "visitdate");
        String visitDate = xmlVdate.isEmpty() ? "" : xmlVdate;

        // Service-code grid (3 columns) + premium flags
        Date now = new Date();
        Map<String, String> propPremium = new HashMap<>();
        ServiceCodeGroup g1 = loadServiceCodeGroup(ctlBillForm, "Group1", now, propPremium);
        ServiceCodeGroup g2 = loadServiceCodeGroup(ctlBillForm, "Group2", now, propPremium);
        ServiceCodeGroup g3 = loadServiceCodeGroup(ctlBillForm, "Group3", now, propPremium);

        // msg = res.getString("billing.hospitalBilling.msgDates") + errorMsg + warningMsg
        // The localized base lives in the JSP's ResourceBundle resolution; we pass the
        // accumulated error/warning portion and the JSP keeps the i18n prefix.
        String msg = demoLoad.errorMessage + demoLoad.warningMessage;

        // pre-load the data the JSP previously fetched via inline
        // SpringUtils.getBean — clinic_nbr dropdown, service-type side panel,
        // dx-code panel, and the user provider's xml_p_nbr / xml_p_sli
        // comments-XML fields. All four legacy DAO calls in
        // billingShortcutPg1.jsp body code are replaced by these reads.
        boolean rmaEnabled = io.github.carlos_emr.CarlosProperties.getInstance()
                .getBooleanProperty("rma_enabled", "true");

        java.util.List<io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingMultisiteContext.ClinicNbrEntry> clinicNbrEntries = new ArrayList<>();
        if (rmaEnabled && clinicNbrDao != null) {
            for (io.github.carlos_emr.carlos.commn.model.ClinicNbr c : clinicNbrDao.findAll()) {
                String value = nullToEmpty(c.getNbrValue());
                String label = String.format("%s | %s", value, nullToEmpty(c.getNbrString()));
                clinicNbrEntries.add(new io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingMultisiteContext.ClinicNbrEntry(value, label));
            }
        }

        // The user-provider's xml_p_nbr / xml_p_sli comments-XML fields drive
        // the auto-select on the clinic-nbr / SLI dropdowns. Resolve once here.
        String providerSearch = "none".equalsIgnoreCase(apptProviderNo)
                ? nullToEmpty(userProviderNo) : apptProviderNo;
        String selectedClinicNbrPrefix = "";
        String selectedXmlPSli = "";
        if (!providerSearch.isEmpty()) {
            io.github.carlos_emr.carlos.commn.model.Provider pr = providerDao.getProvider(providerSearch);
            if (pr != null) {
                selectedClinicNbrPrefix = nullToEmpty(io.github.carlos_emr.SxmlMisc.getXmlContent(pr.getComments(), "xml_p_nbr"));
                String sli = io.github.carlos_emr.SxmlMisc.getXmlContent(pr.getComments(), "xml_p_sli");
                selectedXmlPSli = sli == null ? "" : sli.trim();
            }
        }

        // Service-type side panel (all active service types).
        java.util.List<BillingShortcutPg1ViewModel.ServiceTypeEntry> serviceTypeEntries = new ArrayList<>();
        if (ctlBillingServiceDao != null) {
            for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceTypeRow row :
                    ctlBillingServiceDao.findServiceTypesByStatus("A")) {
                if (row.serviceType().isEmpty()) continue;
                String code = row.serviceType().replaceAll("[^A-Za-z0-9_-]", "_");
                serviceTypeEntries.add(new BillingShortcutPg1ViewModel.ServiceTypeEntry(
                        code, row.serviceTypeName()));
            }
        }

        // Dx code panel for the selected ctlBillForm.
        java.util.List<BillingShortcutPg1ViewModel.DxCodeEntry> dxCodeEntries = new ArrayList<>();
        if (diagnosticCodeDao != null) {
            for (io.github.carlos_emr.carlos.commn.dao.projection.DiagnosticCodeRow row :
                    diagnosticCodeDao.findDiagnosictsAndCtlDiagCodesByServiceType(ctlBillForm)) {
                dxCodeEntries.add(new BillingShortcutPg1ViewModel.DxCodeEntry(
                        row.diagnosticCode(),
                        row.description()));
            }
        }

        // drain residual scriptlets — pre-resolve all the values
        // the legacy JSP read inline via request.getParameter and session
        // attribute lookup. These move the form-state preservation into the
        // model so the JSP can use pure EL.
        java.util.Map<String, String> echoes = new java.util.HashMap<>();
        for (String name : new String[]{
                "billDate", "serviceDate0", "serviceDate1", "serviceDate2",
                "serviceDate3", "serviceDate4", "serviceUnit0", "serviceUnit1",
                "serviceUnit2", "serviceUnit3", "serviceUnit4", "dxCode",
                "referralCode", "referralDocName", "rulePerc", "xml_billtype",
                "xml_visittype", "xml_location", "xml_vdate", "appointment_no",
                "demographic_no", "demographic_name", "apptProvider_no",
                "providerview", "appointment_date", "status", "start_time",
                "asstProvider_no", "assgProvider_no"}) {
            String v = request.getParameter(name);
            if (v != null) echoes.put(name, v);
        }
        // Per-service-code echoes (code_xml_<code> = "checked", unit_xml_<code> = unit count).
        for (List<Properties> grid : java.util.Arrays.asList(g1.entries, g2.entries, g3.entries)) {
            for (Properties p : grid) {
                String code = p.getProperty("serviceCode", "");
                if (code.isEmpty()) continue;
                String codeKey = "code_xml_" + code;
                String unitKey = "unit_xml_" + code;
                String codeVal = request.getParameter(codeKey);
                String unitVal = request.getParameter(unitKey);
                if (codeVal != null) echoes.put(codeKey, codeVal);
                if (unitVal != null) echoes.put(unitKey, unitVal);
            }
        }

        // currentFormName: the legacy JSP iterates serviceTypes and remembers
        // the matching display name when entry.code() equals ctlBillForm.
        // Pre-resolve once, truncated to 30 chars to match legacy display.
        String currentFormName = "";
        for (BillingShortcutPg1ViewModel.ServiceTypeEntry st : serviceTypeEntries) {
            if (st.code().equals(ctlBillForm)) {
                String name = st.name();
                currentFormName = name.length() < 30 ? name : name.substring(0, 30);
                break;
            }
        }

        // assgProviderDisplay: legacy JSP looked up
        // providerBean.getProperty(assgProvider_no, "") from the session
        // Properties; reuse the BillingOnFormViewModelAssembler pattern but
        // without the 15-char truncation (the shortcut JSP renders the
        // raw value).
        ResolvedAssgProviderDisplay assgProviderDisplay =
                resolveAssgProviderDisplay(request, assignedProviderNo);

        // isNewONbilling property: drives the legacy/new history-table
        // branch at the bottom of billingShortcutPg1.jsp.
        boolean isNewOnBillingFlag = "true".equals(props.getProperty("isNewONbilling", ""));

        // Admission date: blank unless visit type starts with "02" or "04",
        // in which case it equals visitDate. Matches legacy JSP scriptlet.
        String admissionDate = "";
        if (visitType.startsWith("02") || visitType.startsWith("04")) {
            admissionDate = visitDate;
        }

        return BillingShortcutPg1ViewModel.builder()
                .userProviderNo(userProviderNo)
                .providerView(providerView)
                .demoNo(demoNo)
                .demoName(demoName)
                .apptNo(apptNo)
                .apptProviderNo(apptProviderNo)
                .apptDate(apptDate)
                .startTime(startTime)
                .ctlBillForm(ctlBillForm)
                .clinicNo(clinicNo)
                .demoFirst(demoLoad.firstName)
                .demoLast(demoLoad.lastName)
                .demoSex(demoLoad.sex)
                .demoHin(demoLoad.hin)
                .demoDob(demoLoad.dob)
                .demoDobYy(demoLoad.dobYy)
                .demoDobMm(demoLoad.dobMm)
                .demoDobDd(demoLoad.dobDd)
                .demoHcType(demoLoad.hcType)
                .assignedProviderNo(assignedProviderNo)
                .referralDoctorName(referralDoctorName)
                .referralDoctorOhip(referralDoctorOhip)
                .visitType(visitType)
                .clinicView(clinicView)
                .visitDate(visitDate)
                .dxCode(dxCode)
                .errorFlag(demoLoad.errorFlag)
                .errorMessage(demoLoad.errorMessage)
                .warningMessage(demoLoad.warningMessage)
                .msg(msg)
                .billingHistory(billingHistory)
                .billingHistoryDetails(billingHistoryDetails)
                .historyUnavailable(historyUnavailable)
                .historyPartialRowCount(historyPartialRowCount)
                .providers(providers)
                .clinicLocations(clinicLocations)
                .serviceCodeCol1(g1.entries)
                .serviceCodeCol2(g2.entries)
                .serviceCodeCol3(g3.entries)
                .headerTitle1(g1.headerTitle)
                .headerTitle2(g2.headerTitle)
                .headerTitle3(g3.headerTitle)
                .propPremium(propPremium)
                .rmaEnabled(rmaEnabled)
                .clinicNbrs(clinicNbrEntries)
                .selectedClinicNbrPrefix(selectedClinicNbrPrefix)
                .selectedXmlPSli(selectedXmlPSli)
                .serviceTypes(serviceTypeEntries)
                .dxCodes(dxCodeEntries)
                .requestParamEchoes(echoes)
                .currentFormName(currentFormName)
                .assgProviderDisplay(assgProviderDisplay.value())
                .assignedProviderUnavailable(assgProviderDisplay.unavailable())
                .newOnBilling(isNewOnBillingFlag)
                .admissionDate(admissionDate)
                .build();
    }

    /**
     * Resolves the assigned billing physician display string the legacy JSP
     * looked up via {@code providerBean.getProperty(assgProvider_no, "")} in
     * session scope. Mirrors the helper in
     * {@link BillingOnFormViewModelAssembler} but does not truncate (the shortcut
     * JSP renders the raw value).
     */
    private record ResolvedAssgProviderDisplay(String value, boolean unavailable) {
    }

    private ResolvedAssgProviderDisplay resolveAssgProviderDisplay(HttpServletRequest request, String assgProviderNo) {
        if (assgProviderNo == null || assgProviderNo.isEmpty()) return new ResolvedAssgProviderDisplay("", false);
        Object sessionBean = request.getSession().getAttribute("providerBean");
        String name = "";
        if (sessionBean instanceof java.util.Properties props) {
            name = props.getProperty(assgProviderNo, "");
        }
        if (name.isEmpty()) {
            try {
                Provider p = providerDao.getProvider(assgProviderNo);
                if (p != null) {
                    name = p.getFormattedName();
                }
            } catch (RuntimeException e) {
                MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "Shortcut: assgProvider display lookup failed for provider={}; rendering blank",
                        LogSafe.sanitize(assgProviderNo), e);
                return new ResolvedAssgProviderDisplay("", true);
            }
        }
        return new ResolvedAssgProviderDisplay(name == null ? "" : name, false);
    }

    private static final class ServiceCodeGroup {
        final List<Properties> entries;
        final String headerTitle;
        ServiceCodeGroup(List<Properties> entries, String headerTitle) {
            this.entries = entries;
            this.headerTitle = headerTitle;
        }
    }

    private ServiceCodeGroup loadServiceCodeGroup(String ctlBillForm,
                                                  String serviceGroup,
                                                  Date billReferenceDate,
                                                  Map<String, String> propPremium) {
        List<Properties> entries = new ArrayList<>();
        String headerTitle = "";
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ServiceCodeMagicRow row :
                billingServiceDao.findBillingServiceAndCtlBillingServiceByMagic(ctlBillForm, serviceGroup, billReferenceDate)) {
            Properties p = new Properties();
            headerTitle = row.serviceGroupName();
            p.setProperty("serviceCode", row.serviceCode());
            p.setProperty("serviceDesc", row.description());
            p.setProperty("serviceDisp", row.value());
            p.setProperty("servicePercentage", Misc.getStr(row.percentage(), ""));
            p.setProperty("serviceSLI", Misc.getStr("" + row.sliFlag(), "false"));
            entries.add(p);
        }
        if (!entries.isEmpty()) {
            List<String> svcCodes = new ArrayList<>();
            for (Properties p : entries) {
                svcCodes.add(p.getProperty("serviceCode"));
            }
            for (CtlBillingServicePremium pr : ctlBillingServicePremiumDao.findByServceCodes(svcCodes)) {
                propPremium.put(pr.getServiceCode(), "A");
            }
        }
        return new ServiceCodeGroup(entries, headerTitle);
    }

    /**
     * Mirrors the legacy JSP's {@code getDefaultValue} helper: prefer the request
     * parameter, otherwise fall back to the first non-empty value of the named
     * key in the history vector.
     */
    private static String getDefaultValue(String paramValue, List<Properties> historyVec, String key) {
        if (paramValue != null && !paramValue.isEmpty()) {
            return paramValue;
        }
        for (Properties p : historyVec) {
            String v = p.getProperty(key, "");
            if (!v.isEmpty()) {
                return v;
            }
        }
        return "";
    }

    private DemographicLoad loadDemographic(String demoNo, String demoSexParam) {
        DemographicLoad load = new DemographicLoad();
        load.sex = nullToEmpty(demoSexParam);
        if (demoNo == null || demoNo.isEmpty()) {
            return load;
        }
        Demographic demo = demographicDao.getDemographic(demoNo);
        if (demo == null) {
            return load;
        }

        load.assignedProviderOverride = nullToEmpty(demo.getProviderNo());

        // Canonical demographic projection (HC type defaulting, DOB padding,
        // raw fields). Shortcut concatenates hin+ver into one display field
        // and normalizes sex to "1"/"2", so we apply both on top of the
        // pass-through projection.
        BillingDemographicSummary summary = BillingDemographicSummary.fromDemographic(demo);
        load.firstName = summary.firstName();
        load.lastName = summary.lastName();
        load.hcType = summary.hcType();
        load.dobYy = summary.dobYy();
        load.dobMm = summary.dobMm();
        load.dobDd = summary.dobDd();
        load.dob = summary.dob();
        if (demo.getHin() != null && demo.getVer() != null) {
            load.hin = demo.getHin() + demo.getVer();
        }

        String sex = summary.sex();
        if ("M".equals(sex)) {
            sex = "1";
        } else if ("F".equals(sex)) {
            sex = "2";
        }
        load.sex = sex;

        BillingReferralDoctor referral = BillingReferralDoctor.fromFamilyDoctor(demo.getFamilyDoctor());
        load.referralDoctorName = referral.name();
        load.referralDoctorOhip = referral.ohip();

        StringBuilder error = new StringBuilder();
        StringBuilder warning = new StringBuilder();
        String errorFlag = "";

        if (demo.getHin() == null || demo.getHin().isEmpty()) {
            warning.append("<br><b><font color='orange'>Warning: The patient does not have a valid HIN. </font></b><br>");
        }
        if (!load.referralDoctorOhip.isEmpty() && load.referralDoctorOhip.length() != 6) {
            warning.append("<br><font color='orange'>Warning: the referral doctor's no is wrong. </font><br>");
        }
        if (load.dob.length() != 8) {
            errorFlag = "1";
            error.append("<br><b><font color='red'>Error: The patient does not have a valid DOB. </font></b><br>");
        }

        load.errorFlag = errorFlag;
        load.errorMessage = error.toString();
        load.warningMessage = warning.toString();
        return load;
    }

    private static class DemographicLoad {
        String firstName = "";
        String lastName = "";
        String sex = "";
        String hin = "";
        String dob = "";
        String dobYy = "";
        String dobMm = "";
        String dobDd = "";
        String hcType = "";
        String assignedProviderOverride = "";
        String referralDoctorName = "";
        String referralDoctorOhip = "";
        String errorFlag = "";
        String errorMessage = "";
        String warningMessage = "";
    }


    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
