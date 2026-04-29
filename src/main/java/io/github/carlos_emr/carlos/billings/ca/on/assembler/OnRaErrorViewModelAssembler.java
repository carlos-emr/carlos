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
import java.util.List;
import java.util.Properties;

import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.OnRaErrorViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingRaReportService;

/**
 * Assembles {@link OnRaErrorViewModel} for
 * {@code billing/CA/ON/onGenRAError.jsp}, the Billing Reconciliation Error
 * Report. Hoists the inline DAO calls the JSP body used to perform: provider
 * dropdown lookup ({@link BillingRaReportService#getProviderListFromRAReport}) and
 * the per-provider error rows ({@link BillingRaReportService#getRAErrorReport}).
 *
 * <p>The legacy JSP did an early return when {@code rano} was missing —
 * here we set {@code valid=false} on the model and the JSP body skips
 * rendering.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class OnRaErrorViewModelAssembler {

    private static final String[] NOT_ERROR_CODES = new String[]{"I2"};

    private final BillingRaReportService prep;

    /** Production constructor — Struts no-arg shape. */
    /** Test-friendly constructor. */
    public OnRaErrorViewModelAssembler(BillingRaReportService prep) {
        this.prep = prep;
    }

    /**
     * Build the view model.
     *
     * @param raNoParam request parameter {@code rano}
     * @param proNoParam request parameter {@code proNo} ({@code null} → "")
     */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public OnRaErrorViewModel assemble(String raNoParam, String proNoParam) {
        OnRaErrorViewModel.Builder b = OnRaErrorViewModel.builder();
        if (raNoParam == null || raNoParam.isEmpty()) {
            return b.valid(false).build();
        }
        String selectedProvider = (proNoParam == null) ? "" : proNoParam;
        b.raNo(raNoParam).selectedProviderOhip(selectedProvider);

        // Always populate the dropdown — both render paths show it.
        List rawProviders = prep.getProviderListFromRAReport(raNoParam);
        List<OnRaErrorViewModel.ProviderOption> providers = new ArrayList<>(
                rawProviders == null ? 0 : rawProviders.size());
        if (rawProviders != null) {
            for (Object o : rawProviders) {
                Properties prop = (Properties) o;
                providers.add(new OnRaErrorViewModel.ProviderOption(
                        prop.getProperty("providerohip_no", ""),
                        prop.getProperty("first_name", ""),
                        prop.getProperty("last_name", "")));
            }
        }
        b.providerOptions(providers);

        // Per-provider error rows are only loaded for a specific provider
        // selection — the "all" / "" branch in the legacy JSP rendered a
        // single empty row from local-default values, which we treat as
        // an empty list.
        if (!selectedProvider.isEmpty() && !"all".equals(selectedProvider)) {
            List<Properties> errorList = prep.getRAErrorReport(raNoParam, selectedProvider, NOT_ERROR_CODES);
            List<OnRaErrorViewModel.ErrorRow> rows = new ArrayList<>(
                    errorList == null ? 0 : errorList.size());
            if (errorList != null) {
                for (Properties prop : errorList) {
                    rows.add(new OnRaErrorViewModel.ErrorRow(
                            prop.getProperty("account", ""),
                            prop.getProperty("demoLast", ""),
                            prop.getProperty("servicedate", ""),
                            prop.getProperty("servicecode", ""),
                            prop.getProperty("serviceno", ""),
                            prop.getProperty("amountsubmit", ""),
                            prop.getProperty("amountpay", ""),
                            prop.getProperty("explain", "")));
                }
            }
            b.errorRows(rows);
        }

        return b.build();
    }
}
