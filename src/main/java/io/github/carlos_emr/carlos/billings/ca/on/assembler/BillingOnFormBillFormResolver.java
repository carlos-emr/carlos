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

import java.util.Collections;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnFormViewModel;
import io.github.carlos_emr.carlos.commn.dao.CtlBillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.MyGroupDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderPreferenceDao;
import io.github.carlos_emr.carlos.commn.model.CtlBillingService;
import io.github.carlos_emr.carlos.commn.model.MyGroup;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.CarlosProperties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Resolver for the {@code ctlBillForm} priority-chain resolution. The
 * legacy scriptlet had this 70-line decision tree inline in the assembler;
 * extracting it makes each branch independently grokkable and lets the
 * priority rules be unit-tested without standing up the full assembler.
 *
 * <p>Priority order:</p>
 * <ol>
 *   <li>{@code curBillForm} request param (user's explicit pick)</li>
 *   <li>roster-status-specific billing service via {@link CtlBillingServiceDao}</li>
 *   <li>provider preference</li>
 *   <li>group default billing form</li>
 *   <li>{@code carlos.properties default_view}</li>
 * </ol>
 *
 * <p>Plus post-selection MIP / PRI overrides based on visit type + roster.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingOnFormBillFormResolver {

    private final CtlBillingServiceDao ctlBillingServiceDao;
    private final ProviderPreferenceDao providerPreferenceDao;
    private final MyGroupDao myGroupDao;

    public BillingOnFormBillFormResolver(CtlBillingServiceDao ctlBillingServiceDao,
                                  ProviderPreferenceDao providerPreferenceDao,
                                  MyGroupDao myGroupDao) {
        this.ctlBillingServiceDao = ctlBillingServiceDao;
        this.providerPreferenceDao = providerPreferenceDao;
        this.myGroupDao = myGroupDao;
    }

    /**
     * Resolution result — the chosen ctlBillForm and the
     * defaultServiceType derived along the way (the service-grid composer
     * uses the latter to drive MIP/PRI re-overrides).
     */
    record ResolvedBillForm(String ctlBillForm, String defaultServiceType) {
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    ResolvedBillForm resolve(BillingOnFormViewModel.Builder b,
                             HttpServletRequest request,
                             String visitType,
                             String rosterStatus,
                             String providerNo,
                             String userNo,
                             String apptProviderNo) {
        String curBillForm = request.getParameter("curBillForm");
        String ctlBillForm = request.getParameter("billForm");
        String defaultServiceType = "";

        if (curBillForm != null) {
            ctlBillForm = curBillForm;
        } else {
            List<CtlBillingService> rosterBillSrvList = !rosterStatus.isEmpty()
                    ? ctlBillingServiceDao.findByServiceTypeId(rosterStatus)
                    : Collections.emptyList();

            if (!rosterBillSrvList.isEmpty() && !rosterStatus.isEmpty()) {
                ctlBillForm = rosterBillSrvList.get(0).getServiceType();
            } else {
                ProviderPreference providerPreference = (apptProviderNo != null && apptProviderNo.equalsIgnoreCase("none"))
                        ? providerPreferenceDao.find(userNo)
                        : providerPreferenceDao.find(apptProviderNo);

                if (providerPreference != null) {
                    defaultServiceType = nullToEmpty(providerPreference.getDefaultServiceType());
                }

                if (("QU - Quebec".equals(rosterStatus) || "FS".equals(rosterStatus))
                        && !"RN".equals(defaultServiceType)) {
                    defaultServiceType = "PRI";
                }
                if (defaultServiceType != null
                        && !defaultServiceType.isEmpty()
                        && !"no".equals(defaultServiceType)
                        && providerPreference != null) {
                    ctlBillForm = providerPreference.getDefaultServiceType();
                } else {
                    List<MyGroup> myGroups = myGroupDao.getProviderGroups(providerNo);
                    for (MyGroup group : myGroups) {
                        String groupBillForm = group.getDefaultBillingForm();
                        if (groupBillForm != null && !groupBillForm.isEmpty()) {
                            ctlBillForm = groupBillForm;
                            break;
                        }
                    }
                    if (ctlBillForm == null || ctlBillForm.isEmpty()) {
                        String dv = CarlosProperties.getInstance().getProperty("default_view");
                        if (dv != null) {
                            ctlBillForm = dv;
                        }
                    }
                }
            }
        }

        if (ctlBillForm == null) {
            ctlBillForm = "";
        }

        // Post-selection overrides: MIP / PRI based on visit type + roster.
        if ((visitType.startsWith("02") || visitType.startsWith("04"))
                && !"RN".equals(defaultServiceType)) {
            ctlBillForm = "MIP";
        }
        if (("QU - Quebec".equals(rosterStatus) || "FS".equals(rosterStatus))
                && !"RN".equals(defaultServiceType)) {
            ctlBillForm = "PRI";
        }

        b.ctlBillForm(ctlBillForm)
                .defaultServiceType(nullToEmpty(defaultServiceType));

        return new ResolvedBillForm(ctlBillForm, defaultServiceType);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
