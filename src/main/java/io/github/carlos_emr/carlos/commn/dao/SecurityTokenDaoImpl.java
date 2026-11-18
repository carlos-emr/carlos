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

import java.util.Date;
import java.util.List;

import jakarta.persistence.Query;

import io.github.carlos_emr.carlos.commn.model.SecurityToken;
import org.springframework.stereotype.Repository;

@Repository
public class SecurityTokenDaoImpl extends AbstractDaoImpl<SecurityToken> implements SecurityTokenDao {

    public SecurityTokenDaoImpl() {
        super(SecurityToken.class);
    }

    @Override
    public SecurityToken findSoapToken(String tokenHash) {
        if (tokenHash == null || !tokenHash.matches("soap1:[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SOAP token hash");
        var rows = entityManager.createQuery("SELECT t FROM SecurityToken t WHERE t.token = :token", SecurityToken.class)
                .setParameter("token", tokenHash).setMaxResults(2).getResultList();
        if (rows.size() > 1) throw new IllegalStateException("Ambiguous SOAP token storage");
        return rows.isEmpty() ? null : rows.getFirst();
    }

    @Override
    public int revokeSoapTokens(String ownerPrefix) {
        if (ownerPrefix == null || !ownerPrefix.matches("soap1:[1-9][0-9]*:")) throw new IllegalArgumentException("Invalid SOAP token owner");
        return entityManager.createQuery("DELETE FROM SecurityToken t WHERE t.token LIKE 'soap1:%' "
                        + "AND t.data LIKE :owner")
                .setParameter("owner", ownerPrefix + "%").executeUpdate();
    }

    @Override
    public SecurityToken getByTokenAndExpiry(String token, Date expiry) {
        Query query = entityManager.createQuery("select t from SecurityToken t where t.token=?1 and t.expiry >= ?2 and t.token NOT LIKE 'soap1:%' ");
        query.setParameter(1, token);
        query.setParameter(2, expiry);

        @SuppressWarnings("unchecked")
        List<SecurityToken> results = query.getResultList();

        if (results.size() > 0) {
            return results.get(0);
        }
        return null;
    }
}
