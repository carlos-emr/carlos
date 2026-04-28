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
 * Immutable view model for {@code billingCodeSearchAjax.jsp}, the
 * jQuery-UI Autocomplete endpoint that returns OHIP service-code
 * suggestions as JSON.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCodeSearchAjaxDataAssembler#assemble}
 * and exposed to the JSP as request attribute {@code ajaxModel}.</p>
 *
 * <p>Eliminates the inline {@code SpringUtils.getBean(BillingServiceDao)}
 * lookup the JSP used to perform inside its scriptlet body.</p>
 *
 * @since 2026-04-26
 */
public final class BillingCodeSearchAjaxViewModel {

    private final List<Suggestion> suggestions;

    /** One autocomplete suggestion. {@code label} is rendered in the
     *  dropdown; {@code value} is what gets inserted on selection. */
    public record Suggestion(String value, String label, String code, String description) {}

    private BillingCodeSearchAjaxViewModel(Builder b) {
        this.suggestions = b.suggestions == null
                ? Collections.emptyList() : List.copyOf(b.suggestions);
    }

    public static Builder builder() { return new Builder(); }

    public List<Suggestion> getSuggestions() { return suggestions; }

    public static final class Builder {
        private List<Suggestion> suggestions;

        public Builder suggestions(List<Suggestion> v) { this.suggestions = v == null ? null : List.copyOf(v); return this; }

        public BillingCodeSearchAjaxViewModel build() { return new BillingCodeSearchAjaxViewModel(this); }
    }
}
