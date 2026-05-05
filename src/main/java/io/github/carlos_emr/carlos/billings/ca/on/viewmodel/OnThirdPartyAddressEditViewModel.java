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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Immutable view model for {@code billing/CA/ON/onAddEdit3rdAddr.jsp},
 * the add/edit 3rd-party billing-address admin form.
 *
 * <p>The legacy JSP scriptlet ran a state machine across two submit paths
 * (Search / Save) plus an in-page row-list dropdown rendered from
 * {@link BillingThirdPartyService#get3rdAddrNameList()}. The action now does all
 * mutation, search, and assembly so the JSP body becomes pure EL/JSTL.</p>
 *
 * @since 2026-04-25
 */
public final class OnThirdPartyAddressEditViewModel {

    /** Banner message; JSP owns all markup and escaping. */
    private final String message;
    /** Hidden-input "action" value: "search", "edit&lt;name&gt;", or "add&lt;name&gt;". */
    private final String action;
    /** Existing form-field values keyed by name (id, attention, company_name, etc). */
    private final Properties prop;
    /** Immutable string-only view for JSP EL; avoids exposing mutable {@link Properties} to the page. */
    private final Map<String, String> propView;
    /** Existing 3rd-party address rows for the company-name dropdown. */
    private final List<Properties> companyOptions;

    private OnThirdPartyAddressEditViewModel(Builder b) {
        this.message = b.message == null ? "" : b.message;
        this.action = b.action == null ? "search" : b.action;
        this.prop = b.prop == null ? new Properties() : (Properties) b.prop.clone();
        this.propView = Collections.unmodifiableMap(toStringMap(this.prop));
        this.companyOptions = b.companyOptions == null ? Collections.emptyList() : List.copyOf(b.companyOptions);
    }

    public static Builder builder() { return new Builder(); }

    public String getMessage() { return message; }
    public String getAction() { return action; }
    public Map<String, String> getProp() { return propView; }
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

        public OnThirdPartyAddressEditViewModel build() { return new OnThirdPartyAddressEditViewModel(this); }
    }

    private static Map<String, String> toStringMap(Properties properties) {
        Map<String, String> values = new HashMap<>();
        for (String name : properties.stringPropertyNames()) {
            values.put(name, properties.getProperty(name, ""));
        }
        return values;
    }
}
