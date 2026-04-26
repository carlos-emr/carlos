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

/**
 * Immutable view model for {@code billingDigSearchAjax.jsp}, the
 * jQuery-UI Autocomplete endpoint that returns ICD-9 diagnostic-code
 * suggestions as JSON.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.pageUtil.BillingDigSearchAjaxDataAssembler#assemble}
 * and exposed to the JSP as request attribute {@code ajaxModel}.</p>
 *
 * @since 2026-04-26
 */
public final class BillingDigSearchAjaxViewModel {

    private final List<Suggestion> suggestions;

    public record Suggestion(String value, String label, String code, String description) {}

    private BillingDigSearchAjaxViewModel(Builder b) {
        this.suggestions = b.suggestions == null
                ? Collections.emptyList() : List.copyOf(b.suggestions);
    }

    public static Builder builder() { return new Builder(); }

    public List<Suggestion> getSuggestions() { return suggestions; }

    public static final class Builder {
        private List<Suggestion> suggestions;

        public Builder suggestions(List<Suggestion> v) { this.suggestions = v == null ? null : List.copyOf(v); return this; }

        public BillingDigSearchAjaxViewModel build() { return new BillingDigSearchAjaxViewModel(this); }
    }
}
