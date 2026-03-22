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

import io.github.carlos_emr.carlos.commn.model.AbstractCodeSystemModel;
import io.github.carlos_emr.carlos.commn.model.Ichppccode;

/**
 * DAO interface for ICHPPC classification code operations.
 *
 * @since 2001
 */

public interface IchppccodeDao extends AbstractDao<Ichppccode> {
    /**
     * Find All.
     * @return List<Ichppccode>
     */
    List<Ichppccode> findAll();

    /**
     * Get Ichppccode Code.
     *
     * @param term String the term
     * @return List<Ichppccode>
     */
    List<Ichppccode> getIchppccodeCode(String term);

    /**
     * Get Ichppccode.
     *
     * @param query String the query
     * @return List<Ichppccode>
     */
    List<Ichppccode> getIchppccode(String query);

    /**
     * Search Code.
     *
     * @param term String the term
     * @return List<Ichppccode>
     */
    List<Ichppccode> searchCode(String term);

    /**
     * Find By Code.
     *
     * @param code String the code
     * @return Ichppccode
     */
    Ichppccode findByCode(String code);

    /**
     * Find By Coding System.
     *
     * @param codingSystem String the codingSystem
     * @return AbstractCodeSystemModel<?>
     */
    AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem);

    /**
     * Search_research_code.
     *
     * @param code String the code
     * @param code1 String the code1
     * @param code2 String the code2
     * @param desc String the desc
     * @param desc1 String the desc1
     * @param desc2 String the desc2
     * @return List<Ichppccode>
     */
    List<Ichppccode> search_research_code(String code, String code1, String code2, String desc, String desc1, String desc2);
}
