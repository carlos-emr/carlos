/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import java.util.Date;
import java.util.Map;

import io.github.carlos_emr.carlos.commn.model.EncounterForm;

public interface EncounterFormDao extends AbstractDao<EncounterForm> {

    /** A form identifier is invalid or absent from the registry; no history SQL was executed. */
    final class UnknownTableException extends IllegalArgumentException {
        public UnknownTableException() {
            super("Unknown encounter form table");
        }
    }

    /** Scalar history from an approved, registry-owned physical form table. */
    record HistoryRow(int id, int demographicNo, Date created, Date edited) { }

    List<HistoryRow> findPatientHistory(String table, String demographicNo, boolean grouped);

    HistoryRow findNewestPatientHistory(String table, String demographicNo);

    String findLatestPatientFormId(String table, String demographicNo, String legacyFormName, boolean byEdited);

    /** Raw column values retain SQL NULL and whitespace for uneditable form history. */
    Map<String, String> findPatientRecord(String table, int id, int demographicNo);

    boolean importedRowExists(String table, String demographicNo, String edited);

    /** Validates physical column names and binds every archive value; returns the generated ID. */
    long insertImportedRow(String table, String demographicNo, String edited, Map<String, String> values);

    public List<EncounterForm> findAll();

    public List<EncounterForm> findAllNotHidden();

    public List<EncounterForm> findByFormName(String formName);

    public List<EncounterForm> findByFormTable(String formTable);

}
