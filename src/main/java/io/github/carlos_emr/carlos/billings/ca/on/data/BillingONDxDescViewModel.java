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
package io.github.carlos_emr.carlos.billings.ca.on.data;

/**
 * Immutable view model for {@code billingON_dx_desc.jsp}, the tiny
 * dx-description-fragment endpoint that returns a 32-char-truncated
 * diagnostic-code description for inline display next to a code on
 * the billing form.
 *
 * @since 2026-04-26
 */
public final class BillingONDxDescViewModel {

    private final String description;

    private BillingONDxDescViewModel(Builder b) {
        this.description = b.description == null ? "" : b.description;
    }

    public static Builder builder() { return new Builder(); }

    public String getDescription() { return description; }

    public static final class Builder {
        private String description;

        public Builder description(String v) { this.description = v; return this; }

        public BillingONDxDescViewModel build() { return new BillingONDxDescViewModel(this); }
    }
}
