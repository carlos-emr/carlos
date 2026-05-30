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
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BatchBillingViewModel;
import io.github.carlos_emr.carlos.commn.dao.BatchBillingDAO;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.BatchBilling;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.DateUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Assembles {@link BatchBillingViewModel} for {@code batchBilling.jsp}, the
 * provider/service-code-filtered batch-billing review page. Owns the four
 * inline {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (BatchBillingDAO, ProviderDao, ClinicLocationDao, DemographicDao) plus
 * the legacy {@link CarlosProperties} reads, the {@link GregorianCalendar}
 * "now" formatting, and the per-row provider/demographic resolution loop.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public final class BatchBillingViewModelAssembler {

    private final BatchBillingDAO batchBillingDao;
    private final ProviderDao providerDao;
    private final ClinicLocationDao clinicLocationDao;
    private final DemographicDao demographicDao;

    public BatchBillingViewModelAssembler(BatchBillingDAO batchBillingDao,
                                          ProviderDao providerDao,
                                          ClinicLocationDao clinicLocationDao,
                                          DemographicDao demographicDao) {
        this.batchBillingDao = batchBillingDao;
        this.providerDao = providerDao;
        this.clinicLocationDao = clinicLocationDao;
        this.demographicDao = demographicDao;
    }

    /**
     * Build the view model from {@code request}.
     *
     * @param request the current {@link HttpServletRequest}; reads the
     *                {@code provider_no} and {@code service_code}
     *                parameters and the {@code user} session attribute
     * @return populated view model
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public BatchBillingViewModel assemble(HttpServletRequest request) {
        BatchBillingViewModel.Builder b = BatchBillingViewModel.builder();

        String userNo = (String) request.getSession().getAttribute("user");
        b.userNo(nullToEmpty(userNo));

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        // Mirrors the legacy date format: "Y/M/D" with no zero-padding (e.g.
        // "2026/4/9"). Used to populate the hidden curDate form field.
        String nowDate = curYear + "/" + curMonth + "/" + curDay;
        String nowTime = now.get(Calendar.HOUR_OF_DAY) + ":" + now.get(Calendar.MINUTE) + ":" + now.get(Calendar.SECOND);
        b.nowDate(nowDate).nowTime(nowTime);
        // The visible BillDate input pre-fills with today in "YYYY-MM-DD"
        // (no zero-padding either, matching legacy).
        b.defaultBillDate(curYear + "-" + curMonth + "-" + curDay);

        String clinicView = CarlosProperties.getInstance().getProperty("clinic_view");
        if (clinicView == null) clinicView = "";
        b.clinicView(clinicView);

        String serviceCodeParam = request.getParameter("service_code");
        String providerView = request.getParameter("provider_no");
        if (providerView == null) providerView = "";
        b.providerView(providerView);
        b.serviceCode(nullToEmpty(serviceCodeParam));

        // Provider dropdown
        List<BatchBillingViewModel.ProviderOption> providers = new ArrayList<>();
        for (Provider p : providerDao.getBillableProviders()) {
            providers.add(new BatchBillingViewModel.ProviderOption(
                    p.getProviderNo(), p.getFirstName(), p.getLastName()));
        }
        b.providers(providers);

        // Service-code dropdown
        b.serviceCodes(batchBillingDao.findDistinctServiceCodes());

        // Clinic-location dropdown
        List<BatchBillingViewModel.ClinicOption> clinics = new ArrayList<>();
        for (ClinicLocation cl : clinicLocationDao.findByClinicNo(1)) {
            clinics.add(new BatchBillingViewModel.ClinicOption(
                    cl.getClinicLocationNo(), cl.getClinicLocationName()));
        }
        b.clinicLocations(clinics);

        // Filter the batch-billing rows by the same combinations the legacy
        // scriptlet supported. The "filterApplied" flag distinguishes "user
        // hasn't selected yet" (renders "Nothing to report") from "user
        // selected but no rows match" (renders "Make selection above to
        // generate batch billing"), which the JSP differentiates inline.
        List<BatchBilling> batchBillings = null;
        boolean filterApplied = false;
        if (!"#".equalsIgnoreCase(providerView) && !providerView.isEmpty() && serviceCodeParam != null) {
            filterApplied = true;
            if ("all".equals(providerView)) {
                if ("all".equals(serviceCodeParam)) {
                    batchBillings = batchBillingDao.findAll();
                } else {
                    batchBillings = batchBillingDao.findByServiceCode(serviceCodeParam);
                }
            } else {
                if ("all".equals(serviceCodeParam)) {
                    batchBillings = batchBillingDao.findByProvider(providerView.trim());
                } else {
                    batchBillings = batchBillingDao.findByProvider(providerView.trim(), serviceCodeParam);
                }
            }
        }
        b.filterApplied(filterApplied);

        List<BatchBillingViewModel.Row> rows = new ArrayList<>();
        if (batchBillings != null) {
            for (BatchBilling bb : batchBillings) {
                Provider provider = providerDao.getProvider(bb.getBillingProviderNo());
                String providerName = provider == null ? "" : provider.getFullName();
                Demographic demographic = demographicDao.getDemographic(String.valueOf(bb.getDemographicNo()));
                String demoName = demographic == null ? "" : demographic.getFormattedName();
                String billingAmount = bb.getBillingAmount() == null ? "N/A" : bb.getBillingAmount();
                String billDate;
                if (bb.getLastBilledDate() == null) {
                    billDate = "N/A";
                } else {
                    billDate = DateUtils.format("yyyy-MM-dd", bb.getLastBilledDate(), request.getLocale());
                }
                String checkboxValue = nullToEmpty(bb.getServiceCode()) + ";"
                        + nullToEmpty(bb.getDxcode()) + ";"
                        + bb.getDemographicNo() + ";"
                        + nullToEmpty(bb.getBillingProviderNo());
                rows.add(new BatchBillingViewModel.Row(
                        checkboxValue,
                        demoName,
                        providerName,
                        bb.getServiceCode(),
                        billingAmount,
                        bb.getDxcode(),
                        billDate));
            }
        }
        b.rows(rows).rowsAvailable(!rows.isEmpty());

        return b.build();
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
