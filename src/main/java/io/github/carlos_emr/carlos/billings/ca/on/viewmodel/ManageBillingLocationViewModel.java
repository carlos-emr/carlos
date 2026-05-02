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

/**
 * Immutable view model for {@code manageBillingLocation.jsp}, the Ontario
 * billing clinic-location admin page.
 *
 * <p>{@link #isEmpty} controls the empty-list banner shown when no clinic
 * locations are available.</p>
 *
 * @since 2026-04-25
 */
public final class ManageBillingLocationViewModel {

    private final List<ClinicLocationRow> locations;
    private final String defaultView;
    private final String reportAction;
    private final String selectedClinicView;

    private ManageBillingLocationViewModel(Builder b) {
        this.locations = b.locations == null
                ? Collections.<ClinicLocationRow>emptyList()
                : Collections.unmodifiableList(b.locations);
        this.defaultView = BillingViewStrings.nullToEmpty(b.defaultView);
        this.reportAction = BillingViewStrings.nullToEmpty(b.reportAction);
        this.selectedClinicView = BillingViewStrings.nullToEmpty(b.selectedClinicView);
    }

    public static Builder builder() { return new Builder(); }

    public List<ClinicLocationRow> getLocations() { return locations; }
    public String getDefaultView() { return defaultView; }
    public String getReportAction() { return reportAction; }
    public String getSelectedClinicView() { return selectedClinicView; }

    /** True when the location list is empty. */
    public boolean isEmpty() { return locations.isEmpty(); }

    public record ClinicLocationRow(String clinicLocationNo, String clinicLocationName) {
        public ClinicLocationRow {
            clinicLocationNo = BillingViewStrings.nullToEmpty(clinicLocationNo);
            clinicLocationName = BillingViewStrings.nullToEmpty(clinicLocationName);
        }

        public String getClinicLocationNo() { return clinicLocationNo; }
        public String getClinicLocationName() { return clinicLocationName; }
    }

    public static final class Builder {
        private List<ClinicLocationRow> locations;
        private String defaultView;
        private String reportAction;
        private String selectedClinicView;

        public Builder locations(List<ClinicLocationRow> v) { this.locations = v; return this; }
        public Builder defaultView(String v) { this.defaultView = v; return this; }
        public Builder reportAction(String v) { this.reportAction = v; return this; }
        public Builder selectedClinicView(String v) { this.selectedClinicView = v; return this; }

        public ManageBillingLocationViewModel build() { return new ManageBillingLocationViewModel(this); }
    }
}
