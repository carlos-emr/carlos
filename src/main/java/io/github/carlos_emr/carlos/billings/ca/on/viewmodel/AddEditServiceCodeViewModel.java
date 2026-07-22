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
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Immutable view model for {@code addEditServiceCode.jsp}, the
 * service-code admin page (search / add / edit OHIP service codes).
 *
 * <p>The legacy JSP scriptlet ran a state machine across three submit
 * paths (Search / Save / "Add Service Code") with two mutation DAOs
 * ({@link io.github.carlos_emr.carlos.commn.dao.BillingServiceDao}
 * and {@link io.github.carlos_emr.carlos.billing.CA.ON.dao.BillingPercLimitDao})
 * plus a CSS-styles dropdown lookup. The action layer now performs mutations
 * through {@code ServiceCodePersister}; the assembler hands the JSP this
 * read-only model.</p>
 *
 * <p>Eliminates the 4 inline {@code SpringUtils.getBean} lookups
 * (BillingServiceDao, BillingPercLimitDao, plus CSSStylesDAO twice).</p>
 *
 * @since 2026-04-26
 */
public final class AddEditServiceCodeViewModel {

    private final String alert;
    private final String message;
    private final String action;
    private final String action2;
    /** Form-field values keyed by the names the JSP renders (service_code,
     *  description, value, percentage, billingservice_date, sliFlag,
     *  termination_date, displaystyle, min, max). */
    private final Properties prop;
    /** Date → billingservice_no map for the multi-date dropdown. Linked-hash
     *  preserved insertion order so the dropdown matches DB row order. */
    private final Map<String, String> codes;
    private final List<CssStyleEntry> cssStyles;

    public record CssStyleEntry(String id, String name, String style) {}

    private AddEditServiceCodeViewModel(Builder b) {
        this.alert = b.alert == null ? "info" : b.alert;
        this.message = b.message == null ? "" : b.message;
        this.action = b.action == null ? "search" : b.action;
        this.action2 = b.action2 == null ? "" : b.action2;
        this.prop = b.prop == null ? new Properties() : (Properties) b.prop.clone();
        this.codes = b.codes == null ? Collections.emptyMap() : new LinkedHashMap<>(b.codes);
        this.cssStyles = b.cssStyles == null ? Collections.emptyList() : List.copyOf(b.cssStyles);
    }

    public static Builder builder() { return new Builder(); }

    public String getAlert() { return alert; }
    public String getMessage() { return message; }
    public String getAction() { return action; }
    public String getAction2() { return action2; }
    /** Returns a defensive copy — callers can read freely without affecting the model. */
    public Properties getProp() { return (Properties) prop.clone(); }
    public String getProp(String key, String def) { return prop.getProperty(key, def); }
    public Map<String, String> getCodes() { return Collections.unmodifiableMap(codes); }
    public List<CssStyleEntry> getCssStyles() { return cssStyles; }

    /** Pre-computed sliFlag checkbox state for the JSP. The legacy scriptlet
     *  inspected the {@code sliFlag} property (string "1" / "true" / other)
     *  and emitted the literal {@code checked} HTML attribute. Surfacing it
     *  as a boolean lets the JSP render a {@code c:if} instead. */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    public boolean isSliFlagChecked() {
        String v = prop.getProperty("sliFlag", "0");
        return "1".equals(v) || "true".equalsIgnoreCase(v);
    }

    /** Returns the {@code termination_date} property value with the
     *  legacy default {@code 9999-12-31} when missing/blank. The JSP
     *  uses this directly so it doesn't have to reproduce the default. */
    public String getTerminationDateOrDefault() {
        String v = prop.getProperty("termination_date");
        return v == null || v.isEmpty() ? "9999-12-31" : v;
    }

    public static final class Builder {
        private String alert;
        private String message;
        private String action;
        private String action2;
        private Properties prop;
        private Map<String, String> codes;
        private List<CssStyleEntry> cssStyles;

        public Builder alert(String v) { this.alert = v; return this; }
        public Builder message(String v) { this.message = v; return this; }
        public Builder action(String v) { this.action = v; return this; }
        public Builder action2(String v) { this.action2 = v; return this; }
        public Builder prop(Properties v) { this.prop = v == null ? null : (Properties) v.clone(); return this; }
        public Builder codes(Map<String, String> v) { this.codes = v == null ? null : new LinkedHashMap<>(v); return this; }
        public Builder cssStyles(List<CssStyleEntry> v) { this.cssStyles = v == null ? null : List.copyOf(v); return this; }

        public AddEditServiceCodeViewModel build() { return new AddEditServiceCodeViewModel(this); }
    }
}
