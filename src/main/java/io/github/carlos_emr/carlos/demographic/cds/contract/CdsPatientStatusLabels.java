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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import java.util.Map;

/**
 * The meaning of CARLOS's built-in patient statuses that have no CDS PersonStatus enumeration
 * value, shared by both transfer directions. The exporter writes the label as
 * PersonStatusAsPlainText so another EMR sees "Moved", not "MO"; the importer maps the label
 * back to the CARLOS code. Clinic-defined statuses are not listed and pass through unchanged.
 */
public final class CdsPatientStatusLabels {
    private CdsPatientStatusLabels() {}

    /** CARLOS code to CDS plain-text label (the add/edit forms' MO - Moved, FI - Fired). */
    public static final Map<String, String> LABELS = Map.of("MO", "Moved", "FI", "Fired");

    /** CDS plain-text label to CARLOS code. */
    public static final Map<String, String> CODES = Map.of("Moved", "MO", "Fired", "FI");
}
