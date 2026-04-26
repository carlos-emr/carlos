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
import java.util.Properties;

/**
 * Immutable view model for {@code billing/CA/ON/onAddEdit3rdAddr.jsp},
 * the add/edit 3rd-party billing-address admin form.
 *
 * <p>The legacy JSP scriptlet ran a state machine across two submit paths
 * (Search / Save) plus an in-page row-list dropdown rendered from
 * {@link Billing3rdPartyService#get3rdAddrNameList()}. The action now does all
 * mutation, search, and assembly so the JSP body becomes pure EL/JSTL.</p>
 *
 * @since 2026-04-25
 */
public final class OnAddEdit3rdAddrViewModel {

    /** Banner message — may be HTML (color tags etc.). */
    private final String message;
    /** Hidden-input "action" value: "search", "edit&lt;name&gt;", or "add&lt;name&gt;". */
    private final String action;
    /** Existing form-field values keyed by name (id, attention, company_name, etc). */
    private final Properties prop;
    /** Existing 3rd-party address rows for the company-name dropdown. */
    private final List<Properties> companyOptions;

    private OnAddEdit3rdAddrViewModel(Builder b) {
        this.message = b.message == null ? "" : b.message;
        this.action = b.action == null ? "search" : b.action;
        this.prop = b.prop == null ? new Properties() : (Properties) b.prop.clone();
        this.companyOptions = b.companyOptions == null ? Collections.emptyList() : List.copyOf(b.companyOptions);
    }

    public static Builder builder() { return new Builder(); }

    public String getMessage() { return message; }
    public String getAction() { return action; }
    public Properties getProp() { return (Properties) prop.clone(); }
    public String getProp(String key, String def) { return prop.getProperty(key, def); }
    public List<Properties> getCompanyOptions() { return companyOptions; }

    /** Convenience getters for each form field — let the JSP read {@code ${model.companyName}}. */
    public String getCompanyName() { return prop.getProperty("company_name", ""); }
    public String getAttention() { return prop.getProperty("attention", ""); }
    public String getId() { return prop.getProperty("id", ""); }
    public String getAddress() { return prop.getProperty("address", ""); }
    public String getCity() { return prop.getProperty("city", ""); }
    public String getProvince() { return prop.getProperty("province", ""); }
    public String getPostcode() { return prop.getProperty("postcode", ""); }
    public String getTelephone() { return prop.getProperty("telephone", ""); }
    public String getFax() { return prop.getProperty("fax", ""); }

    public static final class Builder {
        private String message;
        private String action;
        private Properties prop;
        private List<Properties> companyOptions;

        public Builder message(String v) { this.message = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder prop(Properties v) { this.prop = v == null ? null : (Properties) v.clone(); return this; }
        public Builder companyOptions(List<Properties> v) { this.companyOptions = v == null ? null : List.copyOf(v); return this; }

        public OnAddEdit3rdAddrViewModel build() { return new OnAddEdit3rdAddrViewModel(this); }
    }
}
