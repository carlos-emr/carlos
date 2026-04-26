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

import io.github.carlos_emr.carlos.commn.model.ClinicLocation;

/**
 * Immutable view model for {@code manageBillingLocation.jsp}, the Ontario
 * billing clinic-location admin page.
 *
 * <p>Replaces the legacy inline {@code ClinicLocationDao.findByClinicNo(1)}
 * lookup the JSP body used to perform. {@link #isEmpty} mirrors the
 * legacy "failed!!!" banner for the empty-list case (kept for parity even
 * though it is unusual UX).</p>
 *
 * @since 2026-04-25
 */
public final class ManageBillingLocationViewModel {

    private final List<ClinicLocation> locations;
    private final String defaultView;
    private final String reportAction;
    private final String selectedClinicView;

    private ManageBillingLocationViewModel(Builder b) {
        this.locations = b.locations == null
                ? Collections.<ClinicLocation>emptyList()
                : Collections.unmodifiableList(b.locations);
        this.defaultView = nullToEmpty(b.defaultView);
        this.reportAction = nullToEmpty(b.reportAction);
        this.selectedClinicView = nullToEmpty(b.selectedClinicView);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public List<ClinicLocation> getLocations() { return locations; }
    public String getDefaultView() { return defaultView; }
    public String getReportAction() { return reportAction; }
    public String getSelectedClinicView() { return selectedClinicView; }

    /** True when the location list is empty (mirrors the legacy
     *  {@code clinicLocations.size() == 0} branch that printed
     *  "failed!!!"). */
    public boolean isEmpty() { return locations.isEmpty(); }

    public static final class Builder {
        private List<ClinicLocation> locations;
        private String defaultView;
        private String reportAction;
        private String selectedClinicView;

        public Builder locations(List<ClinicLocation> v) { this.locations = v; return this; }
        public Builder defaultView(String v) { this.defaultView = v; return this; }
        public Builder reportAction(String v) { this.reportAction = v; return this; }
        public Builder selectedClinicView(String v) { this.selectedClinicView = v; return this; }

        public ManageBillingLocationViewModel build() { return new ManageBillingLocationViewModel(this); }
    }
}
