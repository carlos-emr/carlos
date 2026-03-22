/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 * <p>
 * Modifications made by Magenta Health in 2024.
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.login.DBHelp;

import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * DAO interface for pregnancy form lookups.
 * <p>
 * Provides static utility methods to retrieve the latest ONAR (Ontario Antenatal Record)
 * form identifiers by pregnancy episode or patient demographic number.
 *
 * @since 2001
 */
public interface PregnancyFormsDao {

    /**
     * Returns the most recent ONAR Enhanced Record form ID for the given pregnancy episode.
     *
     * @param episodeId Integer the pregnancy episode identifier
     * @return Integer the most recent form ID, or 0 if not found or on error
     */
    public static Integer getLatestFormIdByPregnancy(Integer episodeId) {
        String sql = "SELECT id from formONAREnhancedRecord WHERE episodeId=" + episodeId + " ORDER BY formEdited DESC";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        try {
            if (rs.next()) {
                Integer id = rs.getInt("id");
                return id;
            }
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
            return 0;
        }
        return 0;
    }

    /**
     * Returns the most recent ONAR Enhanced Record form ID for the given patient.
     *
     * @param demographicNo Integer the patient demographic number
     * @return Integer the most recent form ID, or 0 if not found or on error
     */
    public static Integer getLatestFormIdByDemographicNo(Integer demographicNo) {
        String sql = "SELECT id from formONAREnhancedRecord WHERE demographic_no=" + demographicNo + " ORDER BY formEdited DESC";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        try {
            if (rs.next()) {
                Integer id = rs.getInt("id");
                return id;
            }
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
            return 0;
        }
        return 0;
    }

    public static Integer getLatestAR2005FormIdByDemographicNo(Integer demographicNo) {
        String sql = "SELECT id from formONAR WHERE demographic_no=" + demographicNo + " ORDER BY formEdited DESC";
        ResultSet rs = DBHelp.searchDBRecord(sql);
        try {
            if (rs.next()) {
                Integer id = rs.getInt("id");
                return id;
            }
        } catch (SQLException e) {
            MiscUtils.getLogger().error("Error", e);
            return 0;
        }
        return 0;
    }
}
