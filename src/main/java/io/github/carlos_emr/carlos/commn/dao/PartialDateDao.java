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

import java.util.Date;

import io.github.carlos_emr.carlos.commn.model.PartialDate;

/**
 * DAO interface for partial date operations.
 *
 * @since 2001
 */

public interface PartialDateDao extends AbstractDao<PartialDate> {
    /**
     * Get Partial Date.
     *
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     * @return PartialDate
     */
    PartialDate getPartialDate(Integer tableName, Integer tableId, Integer fieldName);

    /**
     * Get Date Partial.
     *
     * @param fieldDate Date the fieldDate
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     * @return String
     */
    String getDatePartial(Date fieldDate, Integer tableName, Integer tableId, Integer fieldName);

    /**
     * Get Date Partial.
     *
     * @param fieldDate String the fieldDate
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     * @return String
     */
    String getDatePartial(String fieldDate, Integer tableName, Integer tableId, Integer fieldName);

    /**
     * Get Date Partial.
     *
     * @param partialDate Date the partialDate
     * @param format String the format
     * @return String
     */
    String getDatePartial(Date partialDate, String format);

    /**
     * Get Date Partial.
     *
     * @param dateString String the dateString
     * @param format String the format
     * @return String
     */
    String getDatePartial(String dateString, String format);

    /**
     * Set Partial Date.
     *
     * @param fieldDate String the fieldDate
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     */
    void setPartialDate(String fieldDate, Integer tableName, Integer tableId, Integer fieldName);

    /**
     * Set Partial Date.
     *
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     * @param format String the format
     */
    void setPartialDate(Integer tableName, Integer tableId, Integer fieldName, String format);

    /**
     * Get Format.
     *
     * @param tableName Integer the tableName
     * @param tableId Integer the tableId
     * @param fieldName Integer the fieldName
     * @return String
     */
    String getFormat(Integer tableName, Integer tableId, Integer fieldName);

    /**
     * Get Format.
     *
     * @param dateValue String the dateValue
     * @return String
     */
    String getFormat(String dateValue);

    /**
     * Get Full Date.
     *
     * @param partialDate String the partialDate
     * @return String
     */
    String getFullDate(String partialDate);

    /**
     * String To Date.
     *
     * @param partialDate String the partialDate
     * @return Date
     */
    Date StringToDate(String partialDate);
}
