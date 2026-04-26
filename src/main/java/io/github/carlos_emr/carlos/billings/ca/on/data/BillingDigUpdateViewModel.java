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
 * Immutable view model for {@code billingDigUpdate.jsp}, the diagnostic-code
 * description-update popup.
 *
 * <p>Carries only the success/error flag — the assembler runs the
 * {@code DiagnosticCodeDao.merge} mutation before the JSP renders, and the
 * JSP just shows the matching success/error banner.</p>
 *
 * @since 2026-04-26
 */
public final class BillingDigUpdateViewModel {

    private final boolean error;

    private BillingDigUpdateViewModel(Builder b) {
        this.error = b.error;
    }

    public static Builder builder() { return new Builder(); }

    public boolean isError() { return error; }

    public static final class Builder {
        private boolean error;

        public Builder error(boolean v) { this.error = v; return this; }

        public BillingDigUpdateViewModel build() { return new BillingDigUpdateViewModel(this); }
    }
}
