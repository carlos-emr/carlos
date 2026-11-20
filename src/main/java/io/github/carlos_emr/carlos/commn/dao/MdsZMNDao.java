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

import java.util.List;

import io.github.carlos_emr.carlos.commn.model.MdsZMN;

public interface MdsZMNDao extends AbstractDao<MdsZMN> {
    MdsZMN findBySegmentIdAndReportName(Integer id, String reportName);

    MdsZMN findBySegmentIdAndResultMnemonic(Integer id, String rm);

    /**
     * Returns the result codes the report filed under one report group.
     *
     * @param id the report's segmentID
     * @param reportSequence the report group id ({@code mdsZRG.reportGroupID}) the codes belong to
     * @return the non-null result codes, in database order; empty when the group has none
     */
    List<String> findResultCodes(Integer id, String reportSequence);

    /**
     * Returns every mnemonic row of one report as scalar columns.
     *
     * <p>The MDS detail tables have no primary key and the entity maps the shared segmentID as its
     * id, so this deliberately does not return {@link MdsZMN} entities: Hibernate would resolve all
     * rows of the report to the first one.</p>
     *
     * @param id the report's segmentID
     * @return rows of {@code [resultMnemonic, reportName, units, referenceRange]}, all {@code String}
     */
    List<Object[]> findMnemonicsBySegmentId(Integer id);
}
