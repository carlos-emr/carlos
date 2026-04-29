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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billing/CA/ON/onGenRAError.jsp}, the
 * Billing Reconciliation - Error Report page. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.OnRaErrorViewModelAssembler}
 * after the action validates the {@code rano} parameter.
 *
 * @since 2026-04-25
 */
public final class OnRaErrorViewModel {

    /** Provider option for the "Filter by provider" dropdown. */
    public record ProviderOption(String ohipNo, String firstName, String lastName) {}

    /** One row of the per-provider error report. */
    public record ErrorRow(
            String account,
            String demoLast,
            String serviceDate,
            String serviceCode,
            String serviceNo,
            String amountSubmit,
            String amountPay,
            String explain) {}

    private final boolean valid;
    private final String raNo;
    private final String selectedProviderOhip;
    private final List<ProviderOption> providerOptions;
    /** Empty when "all" / "" is the selected provider — top section is rendered instead. */
    private final List<ErrorRow> errorRows;

    private OnRaErrorViewModel(Builder b) {
        this.valid = b.valid;
        this.raNo = b.raNo == null ? "" : b.raNo;
        this.selectedProviderOhip = b.selectedProviderOhip == null ? "" : b.selectedProviderOhip;
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList()
                : List.copyOf(b.providerOptions);
        this.errorRows = b.errorRows == null
                ? Collections.emptyList()
                : List.copyOf(b.errorRows);
    }

    public boolean isValid() { return valid; }
    public String getRaNo() { return raNo; }
    public String getSelectedProviderOhip() { return selectedProviderOhip; }
    public List<ProviderOption> getProviderOptions() { return providerOptions; }
    public List<ErrorRow> getErrorRows() { return errorRows; }
    /** True when the legacy JSP would render the per-provider error rows table. */
    public boolean isShowProviderRows() {
        return !selectedProviderOhip.isEmpty() && !"all".equals(selectedProviderOhip);
    }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private boolean valid = true;
        private String raNo;
        private String selectedProviderOhip;
        private List<ProviderOption> providerOptions;
        private List<ErrorRow> errorRows;

        public Builder valid(boolean v) { this.valid = v; return this; }
        public Builder raNo(String v) { this.raNo = v; return this; }
        public Builder selectedProviderOhip(String v) { this.selectedProviderOhip = v; return this; }
        public Builder providerOptions(List<ProviderOption> v) { this.providerOptions = v; return this; }
        public Builder errorRows(List<ErrorRow> v) { this.errorRows = v; return this; }

        public OnRaErrorViewModel build() { return new OnRaErrorViewModel(this); }
    }
}
