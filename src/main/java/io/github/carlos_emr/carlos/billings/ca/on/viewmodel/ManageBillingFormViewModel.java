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
 * Immutable view model for {@code manageBillingform.jsp} and its three included
 * tab fragments: {@code manageBillingform_dx.jspf},
 * {@code manageBillingform_premium.jspf}, {@code manageBillingform_service.jspf}.
 *
 * <p>The parent JSP uses the {@code billingform} request parameter to choose
 * which tab fragment to render. The {@code reportAction} parameter further
 * differentiates between the service-code and dx-code subtabs. This view
 * model captures:</p>
 * <ul>
 *   <li>{@code clinicView} / {@code reportAction} — current selection (used to
 *       drive the radio + select state)</li>
 *   <li>{@code serviceTypes} — the dropdown options ({@code findServiceTypes}
 *       result)</li>
 *   <li>{@code dxCodes} — the 45-slot diag-code grid for the dx tab</li>
 *   <li>{@code premiumAddSlots} — the 10-slot empty add-row in the premium tab</li>
 *   <li>{@code premiumDeleteRows} — the 3-column delete grid in the premium tab</li>
 *   <li>{@code serviceGroups} — the 3-group, 20-row service-code grid in the
 *       service tab</li>
 *   <li>{@code currentServiceTypeName} — last-seen service group name (used by
 *       hidden {@code type} field)</li>
 *   <li>{@code requestParamEchoes} — request-parameter passthroughs the JSP
 *       echoes inline</li>
 * </ul>
 *
 * @since 2026-04-25
 */
public final class ManageBillingFormViewModel {

    /** Drop-down option for the service-type select. */
    public record ServiceTypeOption(String code, String name) { }

    /** Single row of the unique-service-types list rendered in the
     *  manageBillingform_add.jspf right-hand panel. */
    public record UniqueServiceTypeRow(String typeId, String typeName) { }

    /** One row of the 3-column dx-code grid (45 slots split into 3x15). */
    public record DxCodeSlot(int index, String code) { }

    /** One row of the premium delete grid (3 columns of code + description). */
    public record PremiumDeleteRow(
            String code1, String desc1,
            String code2, String desc2,
            String code3, String desc3) { }

    /** One service-group column in the service tab (group name + 20 slots). */
    public record ServiceGroup(int groupIndex, String name, List<ServiceSlot> slots) { }

    /** One row inside a {@link ServiceGroup}: a service code + its order. */
    public record ServiceSlot(int index, String code, String order) { }

    private final String clinicView;
    private final String reportAction;
    private final List<ServiceTypeOption> serviceTypes;
    private final List<DxCodeSlot> dxCodes;          // exactly 45 entries
    private final List<Integer> premiumAddSlots;     // 1..10
    private final List<PremiumDeleteRow> premiumDeleteRows;
    private final List<ServiceGroup> serviceGroups;
    private final String currentServiceTypeName;
    private final List<UniqueServiceTypeRow> uniqueServiceTypes;
    private final boolean uniqueServiceTypesLoaded;
    private final Map<String, String> requestParamEchoes;

    private ManageBillingFormViewModel(Builder b) {
        this.clinicView = BillingViewStrings.nullToEmpty(b.clinicView);
        this.reportAction = BillingViewStrings.nullToEmpty(b.reportAction);
        this.serviceTypes = b.serviceTypes == null
                ? Collections.emptyList() : List.copyOf(b.serviceTypes);
        this.dxCodes = b.dxCodes == null
                ? Collections.emptyList() : List.copyOf(b.dxCodes);
        this.premiumAddSlots = b.premiumAddSlots == null
                ? Collections.emptyList() : List.copyOf(b.premiumAddSlots);
        this.premiumDeleteRows = b.premiumDeleteRows == null
                ? Collections.emptyList() : List.copyOf(b.premiumDeleteRows);
        this.serviceGroups = b.serviceGroups == null
                ? Collections.emptyList() : List.copyOf(b.serviceGroups);
        this.currentServiceTypeName = BillingViewStrings.nullToEmpty(b.currentServiceTypeName);
        this.uniqueServiceTypes = b.uniqueServiceTypes == null
                ? Collections.emptyList() : List.copyOf(b.uniqueServiceTypes);
        this.uniqueServiceTypesLoaded = b.uniqueServiceTypesLoaded;
        this.requestParamEchoes = b.requestParamEchoes == null
                ? Collections.emptyMap() : Map.copyOf(b.requestParamEchoes);
    }

    public static Builder builder() { return new Builder(); }

    public String getClinicView() { return clinicView; }
    public String getReportAction() { return reportAction; }
    public List<ServiceTypeOption> getServiceTypes() { return serviceTypes; }
    public List<DxCodeSlot> getDxCodes() { return dxCodes; }
    public List<Integer> getPremiumAddSlots() { return premiumAddSlots; }
    public List<PremiumDeleteRow> getPremiumDeleteRows() { return premiumDeleteRows; }
    public List<ServiceGroup> getServiceGroups() { return serviceGroups; }
    public String getCurrentServiceTypeName() { return currentServiceTypeName; }
    public List<UniqueServiceTypeRow> getUniqueServiceTypes() { return uniqueServiceTypes; }
    /** True when the unique service-type lookup completed and rows can be rendered. */
    public boolean isUniqueServiceTypesLoaded() { return uniqueServiceTypesLoaded; }
    /** Request echoes support JSP hidden fields / banners without scriptlet reads from the raw request. */
    public Map<String, String> getRequestParamEchoes() { return requestParamEchoes; }

    public static final class Builder {
        private String clinicView;
        private String reportAction;
        private List<ServiceTypeOption> serviceTypes;
        private List<DxCodeSlot> dxCodes;
        private List<Integer> premiumAddSlots;
        private List<PremiumDeleteRow> premiumDeleteRows;
        private List<ServiceGroup> serviceGroups;
        private String currentServiceTypeName;
        private List<UniqueServiceTypeRow> uniqueServiceTypes;
        private boolean uniqueServiceTypesLoaded;
        private Map<String, String> requestParamEchoes;

        public Builder clinicView(String v) { this.clinicView = v; return this; }
        public Builder reportAction(String v) { this.reportAction = v; return this; }
        public Builder serviceTypes(List<ServiceTypeOption> v) {
            this.serviceTypes = v == null ? null : List.copyOf(v); return this;
        }
        public Builder dxCodes(List<DxCodeSlot> v) {
            this.dxCodes = v == null ? null : List.copyOf(v); return this;
        }
        public Builder premiumAddSlots(List<Integer> v) {
            this.premiumAddSlots = v == null ? null : List.copyOf(v); return this;
        }
        public Builder premiumDeleteRows(List<PremiumDeleteRow> v) {
            this.premiumDeleteRows = v == null ? null : List.copyOf(v); return this;
        }
        public Builder serviceGroups(List<ServiceGroup> v) {
            this.serviceGroups = v == null ? null : List.copyOf(v); return this;
        }
        public Builder currentServiceTypeName(String v) { this.currentServiceTypeName = v; return this; }
        public Builder uniqueServiceTypes(List<UniqueServiceTypeRow> v) {
            this.uniqueServiceTypes = v == null ? null : List.copyOf(v); return this;
        }
        public Builder uniqueServiceTypesLoaded(boolean v) { this.uniqueServiceTypesLoaded = v; return this; }
        public Builder requestParamEchoes(Map<String, String> v) {
            this.requestParamEchoes = v == null ? null : Map.copyOf(v); return this;
        }

        public ManageBillingFormViewModel build() { return new ManageBillingFormViewModel(this); }
    }
}
