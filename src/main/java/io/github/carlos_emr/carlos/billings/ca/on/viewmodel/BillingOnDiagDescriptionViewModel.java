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

/**
 * Immutable view model for {@code billingON_dx_desc.jsp}, the tiny
 * dx-description-fragment endpoint that returns a 32-char-truncated
 * diagnostic-code description for inline display next to a code on
 * the billing form.
 *
 * @since 2026-04-26
 */
public final class BillingOnDiagDescriptionViewModel {

    private final String description;

    private BillingOnDiagDescriptionViewModel(Builder b) {
        this.description = b.description == null ? "" : b.description;
    }

    public static Builder builder() { return new Builder(); }

    public String getDescription() { return description; }

    public static final class Builder {
        private String description;

        public Builder description(String v) { this.description = v; return this; }

        public BillingOnDiagDescriptionViewModel build() { return new BillingOnDiagDescriptionViewModel(this); }
    }
}
