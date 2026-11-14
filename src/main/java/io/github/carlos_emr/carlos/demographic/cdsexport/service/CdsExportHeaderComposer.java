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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import java.util.Objects;

/** Guide section 2 software/schema comment; bundle/profile versions do not rename the actual XSD. */
public final class CdsExportHeaderComposer {
    public String comment(String applicationVersion, CdsLogicalProfile profile) {
        Objects.requireNonNull(profile, "profile");
        if (applicationVersion == null || !applicationVersion.matches("[A-Za-z0-9][A-Za-z0-9._+~-]{0,80}")
                || applicationVersion.contains("--") || applicationVersion.endsWith("-")) {
            throw new IllegalArgumentException("CDS_EXPORT_APPLICATION_VERSION_INVALID");
        }
        return "Created by CARLOS EMR " + applicationVersion
                + "; schema EMR_Data_Migration_Schema.xsd version 1.0"
                + "; publication 2017-08-04; status Final"
                + "; logical profile " + profile.identifier();
    }
}
