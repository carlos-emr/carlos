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
package io.github.carlos_emr.carlos.demographic.cdsexport.engine;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;

/** Per-attempt capabilities; identity/lease ownership never substitutes for current disclosure authority. */
public interface CdsExportRunControl {
    /** Outside source transactions; cheap local checks may throttle durable metadata probes. */
    void checkpoint();
    /** Inside an explicit read-only source transaction; metadata control uses an independent transaction. */
    void captureCheckpoint();
    /** Before/after capture, outside its admission barrier; no stale source-snapshot permission check. */
    void authorizePatient(int patient);
    /** The preferred pharmacy is a demographic wire field backed by the separate native Rx read privilege. */
    default void authorizePharmacy(int patient) { throw new SecurityException("CDS_EXPORT_PHARMACY_AUTHORIZATION_REQUIRED"); }

    /** Actual sensitive laboratory occurrences must explicitly require their additional domain privilege. */
    void authorizeCategory(int patient, CdsCategory category, boolean sensitiveLaboratory);
}
