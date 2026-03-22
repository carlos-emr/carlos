/**
 * Copyright (c) 2013-2015. Department of Computer Science, University of Victoria. All Rights Reserved.
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
 * Department of Computer Science
 * LeadLab
 * University of Victoria
 * Victoria, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;

import java.util.List;

/**
 * Interface for drug reference database search operations in the CARLOS EMR system.
 *
 * <p>Provides search capabilities against the drug reference database including
 * partial name search, full search, element-based search, and detailed drug
 * information retrieval.</p>
 *
 * @see DrugLookUpManager
 * @see io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1
 * @since 2026-03-17
 */
public interface DrugLookUp {

    /**
     * Searches for drugs by partial name match.
     *
     * @param s String the search term
     * @return List of DrugSearchTo1 matching drug results
     */
    public List<DrugSearchTo1> search(String s);

    /**
     * Performs a comprehensive drug search across all searchable fields.
     *
     * @param s String the search term
     * @return List of DrugSearchTo1 matching drug results
     */
    public List<DrugSearchTo1> fullSearch(String s);

    /**
     * Searches for drugs by active ingredient or element name.
     *
     * @param s String the element/ingredient search term
     * @return List of DrugSearchTo1 matching drug results
     */
    public List<DrugSearchTo1> searchByElement(String s);

    /**
     * Retrieves detailed information for a specific drug by its identifier.
     *
     * @param id String the drug identifier
     * @return DrugSearchTo1 the detailed drug information
     * @throws Exception if the drug cannot be found or a lookup error occurs
     */
    public DrugSearchTo1 details(String id) throws Exception;

}
