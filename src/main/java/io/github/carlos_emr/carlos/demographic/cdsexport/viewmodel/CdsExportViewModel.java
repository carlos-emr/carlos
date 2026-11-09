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
package io.github.carlos_emr.carlos.demographic.cdsexport.viewmodel;

import java.util.List;

/** One immutable presentation model for the asynchronous demographic export JSP. */
public record CdsExportViewModel(
        boolean enabled,
        boolean dispatcherRunning,
        String operatorKey,
        String providerNo,
        String facilityId,
        List<Option> profiles,
        List<Option> categories) {
    public CdsExportViewModel {
        profiles = List.copyOf(profiles);
        categories = List.copyOf(categories);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isDispatcherRunning() {
        return dispatcherRunning;
    }

    public String getOperatorKey() {
        return operatorKey;
    }

    public String getProviderNo() {
        return providerNo;
    }

    public String getFacilityId() {
        return facilityId;
    }

    public List<Option> getProfiles() {
        return profiles;
    }

    public List<Option> getCategories() {
        return categories;
    }

    public record Option(String value, String label) {
        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }
}
