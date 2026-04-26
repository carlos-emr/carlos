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

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable view model for {@code billingONEditPrivateCode.jsp},
 * the manage-private-billing-code admin form.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingONEditPrivateCodeDataAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.ViewBillingONEditPrivateCode2Action})
 * and exposed to the JSP as request attribute {@code privateCodeModel}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingONEditPrivateCodeViewModel {

    /** A single private-billing-code dropdown entry: code (no leading "_") + truncated description. */
    public record PrivateCodeOption(String code, String label) { }

    private final String message;
    private final String alertLevel;
    private final String action;
    private final List<PrivateCodeOption> options;
    private final Map<String, String> formFields;

    private BillingONEditPrivateCodeViewModel(Builder b) {
        this.message = b.message == null ? "" : b.message;
        this.alertLevel = b.alertLevel == null ? "info" : b.alertLevel;
        this.action = b.action == null ? "search" : b.action;
        this.options = b.options == null ? Collections.emptyList() : List.copyOf(b.options);
        this.formFields = b.formFields == null
                ? Collections.emptyMap() : Map.copyOf(b.formFields);
    }

    public static Builder builder() { return new Builder(); }

    public String getMessage() { return message; }
    public String getAlertLevel() { return alertLevel; }
    public String getAction() { return action; }
    public List<PrivateCodeOption> getOptions() { return options; }
    public Map<String, String> getFormFields() { return formFields; }

    public static final class Builder {
        private String message;
        private String alertLevel;
        private String action;
        private List<PrivateCodeOption> options;
        private Map<String, String> formFields;

        public Builder message(String v) { this.message = v; return this; }
        public Builder alertLevel(String v) { this.alertLevel = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder options(List<PrivateCodeOption> v) { this.options = v; return this; }
        public Builder formFields(Map<String, String> v) { this.formFields = v; return this; }

        public BillingONEditPrivateCodeViewModel build() {
            return new BillingONEditPrivateCodeViewModel(this);
        }
    }
}
