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
package io.github.carlos_emr.carlos.billings.ca.on.data;

import java.util.Collections;
import java.util.List;

/**
 * Immutable bill-form selection state for {@code billingON.jsp}. Replaces
 * the five-field cluster ({@code defaultBillFormName}, {@code defaultBillType},
 * {@code defaultServiceType}, {@code billingForms}, {@code selectedBillType})
 * the form view model previously carried as flat fields.
 *
 * <p>{@link #defaultFormName} is the resolved default per the legacy
 * roster/preference/group/properties fallback chain.
 * {@link #defaultBillType} is the matching default bill-type code.
 * {@link #defaultServiceType} is the resolved default service-type bucket
 * ("RA", "WCB", "ODP" etc.) that drives which column lights up first.
 * {@link #forms} is the full menu of available bill-form options for the
 * Layer1 dropdown and the {@code _billingForms} JS array.
 * {@link #selectedBillType} is the request-param-or-roster-resolved value
 * the form's selected radio button should match.</p>
 *
 * @since 2026-04-26
 */
public record BillingBillFormSelector(
        String defaultFormName,
        String defaultBillType,
        String defaultServiceType,
        List<BillingONFormViewModel.BillingFormMenuEntry> forms,
        String selectedBillType) {

    /** Empty / unset default. */
    public static final BillingBillFormSelector EMPTY = new BillingBillFormSelector(
            "", "", "", List.of(), "");

    /** Compact constructor: null-coalesces strings, immutably copies the form list. */
    public BillingBillFormSelector {
        defaultFormName = defaultFormName == null ? "" : defaultFormName;
        defaultBillType = defaultBillType == null ? "" : defaultBillType;
        defaultServiceType = defaultServiceType == null ? "" : defaultServiceType;
        forms = forms == null ? Collections.emptyList() : List.copyOf(forms);
        selectedBillType = selectedBillType == null ? "" : selectedBillType;
    }
}
