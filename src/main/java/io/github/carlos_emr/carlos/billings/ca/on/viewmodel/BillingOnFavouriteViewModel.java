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
import java.util.Map;

/**
 * Immutable view model for {@code billingONfavourite.jsp}, the
 * Add/Edit Service Code "favourites" admin form.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnFavouriteViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnFavourite2Action})
 * and exposed to the JSP as request attribute {@code favouriteModel}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingOnFavouriteViewModel {

    /** A single dropdown entry in the favourite-name select box. */
    public record FavouriteName(String value) { }

    /** i18n message key resolved via {@code fmt:message} in the JSP. */
    private final String messageKey;
    /** Raw (unencoded) name substituted as {@code {0}} parameter; may be null. */
    private final String messageName;
    /** Bootstrap alert class suffix: info, success, warning, or danger. */
    private final String messageLevel;
    private final String action;
    private final List<FavouriteName> names;
    private final Map<String, String> formFields;
    private final int serviceFieldCount;

    private BillingOnFavouriteViewModel(Builder b) {
        this.messageKey = b.messageKey == null ? "" : b.messageKey;
        this.messageName = b.messageName;
        this.messageLevel = b.messageLevel == null ? "info" : b.messageLevel;
        this.action = b.action == null ? "search" : b.action;
        this.names = b.names == null ? Collections.emptyList() : List.copyOf(b.names);
        this.formFields = b.formFields == null
                ? Collections.emptyMap() : Map.copyOf(b.formFields);
        this.serviceFieldCount = b.serviceFieldCount;
    }

    public static Builder builder() { return new Builder(); }

    public String getMessageKey() { return messageKey; }
    public String getMessageName() { return messageName; }
    public String getMessageLevel() { return messageLevel; }
    public String getAction() { return action; }
    public List<FavouriteName> getNames() { return names; }
    public Map<String, String> getFormFields() { return formFields; }
    public int getServiceFieldCount() { return serviceFieldCount; }

    public static final class Builder {
        private String messageKey;
        private String messageName;
        private String messageLevel;
        private String action;
        private List<FavouriteName> names;
        private Map<String, String> formFields;
        private int serviceFieldCount;

        public Builder messageKey(String v) { this.messageKey = v; return this; }
        public Builder messageName(String v) { this.messageName = v; return this; }
        public Builder messageLevel(String v) { this.messageLevel = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder names(List<FavouriteName> v) { this.names = v; return this; }
        public Builder formFields(Map<String, String> v) { this.formFields = v; return this; }
        public Builder serviceFieldCount(int v) { this.serviceFieldCount = v; return this; }

        public BillingOnFavouriteViewModel build() {
            return new BillingOnFavouriteViewModel(this);
        }
    }
}
