/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.ServiceOAuthNonce;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

@Repository
public class ServiceOAuthNonceDaoImpl extends AbstractDaoImpl<ServiceOAuthNonce> implements ServiceOAuthNonceDao {

    public ServiceOAuthNonceDaoImpl() {
        super(ServiceOAuthNonce.class);
    }

    @Override
    public ServiceOAuthNonce findByNonceKeyHash(String nonceKeyHash) {
        Query query = this.entityManager.createQuery(
                "SELECT x FROM ServiceOAuthNonce x WHERE x.nonceKeyHash = ?1",
                ServiceOAuthNonce.class);
        query.setParameter(1, nonceKeyHash);
        return this.getSingleResultOrNull(query);
    }

    @Override
    public int deleteOlderThan(long oauthTimestampCutoff) {
        Query query = this.entityManager.createQuery(
                "DELETE FROM ServiceOAuthNonce x WHERE x.oauthTimestamp < ?1");
        query.setParameter(1, oauthTimestampCutoff);
        return query.executeUpdate();
    }
}
