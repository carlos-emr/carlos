/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
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
import io.github.carlos_emr.carlos.commn.model.Icd9;

/**
 * DAO interface for ICD diagnostic code operations.
 *
 * @since 2001
 */

public interface Icd9Dao extends AbstractCodeSystemDao<Icd9> {
    /**
     * Get Icd9 Code.
     *
     * @param icdCode String the icdCode
     * @return List<Icd9>
     */
    List<Icd9> getIcd9Code(String icdCode);

    /**
     * Get Icd9.
     *
     * @param query String the query
     * @return List<Icd9>
     */
    List<Icd9> getIcd9(String query);

    /**
     * Find By Code.
     *
     * @param code String the code
     * @return Icd9
     */
    Icd9 findByCode(String code);

    /**
     * Search Code.
     *
     * @param term String the term
     * @return List<Icd9>
     */
    List<Icd9> searchCode(String term);

    /**
     * Find By Coding System.
     *
     * @param codingSystem String the codingSystem
     * @return AbstractCodeSystemModel<?>
     */
    AbstractCodeSystemModel<?> findByCodingSystem(String codingSystem);
}
