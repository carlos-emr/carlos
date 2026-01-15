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
 */

package ca.openosp.openo.commn.dao;

import java.util.List;
import javax.persistence.Query;
import javax.persistence.TypedQuery;

import ca.openosp.openo.commn.model.CtlSpecialInstructions;
import org.springframework.stereotype.Repository;

@Repository
public class CtlSpecialInstructionsDaoImpl extends AbstractDaoImpl<CtlSpecialInstructions> implements CtlSpecialInstructionsDao {

    public CtlSpecialInstructionsDaoImpl() {
        super(CtlSpecialInstructions.class);
    }

    @Override
    public List<CtlSpecialInstructions> findAll() {

        String sql = "select x from CtlSpecialInstructions x";
        Query query = entityManager.createQuery(sql);

        @SuppressWarnings("unchecked")
        List<CtlSpecialInstructions> results = query.getResultList();
        return results;
    }

	/**
	 * Finds a list of descriptions from the CtlSpecialInstructions table that match a given query string.
	 * The search is case-insensitive and uses a "like" operator with wildcards.
	 *
	 * @param descQuery The query string to search for within the descriptions.
	 *                  The search will look for descriptions that contain this string.
	 * @return A list of strings representing the matching descriptions.
	 */
	@Override
	public List<String> findDescriptionsMatching(String descQuery) {
		String query = "SELECT x.description FROM CtlSpecialInstructions x WHERE " +
				"x.description IS NOT NULL AND x.description LIKE :searchString";

		TypedQuery<String> typedQuery = super.entityManager.createQuery(query, String.class);
		typedQuery.setParameter("searchString", "%" + descQuery + "%");

		return typedQuery.getResultList();
	}
}
