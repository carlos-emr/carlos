/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2005, 2009 IBM Corporation and others.
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
 * Contributors:
 * <Quatro Group Software Systems inc.>  <OSCAR Team>
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.services;

import java.sql.SQLException;
import java.util.List;

import io.github.carlos_emr.carlos.model.LookupCodeValue;
import io.github.carlos_emr.carlos.model.LookupTableDefValue;

/**
 * Service interface for managing configurable lookup tables and code values
 * in the CARLOS EMR system.
 *
 * <p>Lookup tables provide a generic mechanism for defining and maintaining
 * code-value pairs used across the application, such as clinical codes,
 * organizational classifications, and administrative reference data.</p>
 *
 * @see LookupManagerImpl
 * @see io.github.carlos_emr.carlos.model.LookupCodeValue
 * @see io.github.carlos_emr.carlos.model.LookupTableDefValue
 * @since 2026-03-17
 */
public interface LookupManager {

    /**
     * Loads a filtered list of code values from a specified lookup table.
     *
     * @param tableId String the identifier of the lookup table
     * @param activeOnly boolean if true, returns only active code entries
     * @param code String filter by code value, or null for no code filter
     * @param codeDesc String filter by code description, or null for no description filter
     * @return List of matching code values from the lookup table
     */
    List LoadCodeList(String tableId, boolean activeOnly, String code, String codeDesc);

    /**
     * Loads a filtered list of code values from a specified lookup table,
     * additionally filtering by a parent code for hierarchical lookups.
     *
     * @param tableId String the identifier of the lookup table
     * @param activeOnly boolean if true, returns only active code entries
     * @param parentCode String the parent code to filter by in hierarchical lookups
     * @param code String filter by code value, or null for no code filter
     * @param codeDesc String filter by code description, or null for no description filter
     * @return List of matching code values from the lookup table
     */
    List LoadCodeList(String tableId, boolean activeOnly, String parentCode, String code, String codeDesc);

    /**
     * Retrieves the table definition for a specified lookup table.
     *
     * @param tableId String the identifier of the lookup table
     * @return LookupTableDefValue the table definition including metadata and field definitions
     */
    LookupTableDefValue GetLookupTableDef(String tableId);

    /**
     * Retrieves a single lookup code entry from a specified table.
     *
     * @param tableId String the identifier of the lookup table
     * @param code String the code value to retrieve
     * @return LookupCodeValue the matching code entry, or null if not found
     */
    LookupCodeValue GetLookupCode(String tableId, String code);

    /**
     * Loads the field definitions for a specified lookup table.
     *
     * @param tableId String the identifier of the lookup table
     * @return List of field definition objects describing the table's columns
     */
    List LoadFieldDefList(String tableId);

    /**
     * Retrieves field values for a specific code within a lookup table.
     *
     * @param tableDef LookupTableDefValue the table definition
     * @param code String the code whose field values are retrieved
     * @return List of field values for the specified code
     */
    List GetCodeFieldValues(LookupTableDefValue tableDef, String code);

    /**
     * Retrieves all field values for a lookup table definition.
     *
     * @param tableDef LookupTableDefValue the table definition
     * @return List of all field values in the lookup table
     */
    List GetCodeFieldValues(LookupTableDefValue tableDef);

    /**
     * Saves or updates a code value entry in a lookup table.
     *
     * @param isNew boolean true if creating a new entry, false if updating an existing one
     * @param tableDef LookupTableDefValue the table definition for the target lookup table
     * @param fieldDefList List of field definitions with the values to save
     * @return String the code identifier of the saved entry
     * @throws SQLException if a database access error occurs during the save operation
     */
    String SaveCodeValue(boolean isNew, LookupTableDefValue tableDef, List fieldDefList) throws SQLException;

    /**
     * Returns the count of active clients associated with an organization code.
     *
     * @param orgCd String the organization code to query
     * @return int the number of active clients for the specified organization
     * @throws SQLException if a database access error occurs
     */
    int getCountOfActiveClient(String orgCd) throws SQLException;
}
