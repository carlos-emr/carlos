//CHECKSTYLE:OFF
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
 */

package io.github.carlos_emr.carlos.commn.dao;

import java.util.List;
import javax.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.ClientLink;
import org.springframework.stereotype.Repository;

@Repository
public class ClientLinkDaoImpl extends AbstractDaoImpl<ClientLink> implements ClientLinkDao {

    public ClientLinkDaoImpl() {
        super(ClientLink.class);
    }

    public List<ClientLink> findByFacilityIdClientIdType(Integer facilityId, Integer clientId, Boolean currentlyLinked, ClientLink.Type type) {
        // build sql string
        StringBuilder sqlCommand = new StringBuilder();
        sqlCommand.append("select x from ClientLink x where x.facilityId=?1 and x.clientId=?2");
        if (type != null) sqlCommand.append(" and x.linkType=?3");
        if (currentlyLinked != null) {
            if (currentlyLinked) sqlCommand.append(" and x.unlinkProviderNo is null");
            else sqlCommand.append(" and x.unlinkProviderNo is not null");
        }

        // set parameters
        Query query = entityManager.createQuery(sqlCommand.toString());
        query.setParameter(1, facilityId);
        query.setParameter(2, clientId);
        if (type != null) query.setParameter(3, type);

        // run query
        @SuppressWarnings("unchecked")
        List<ClientLink> results = query.getResultList();

        return (results);
    }
}
