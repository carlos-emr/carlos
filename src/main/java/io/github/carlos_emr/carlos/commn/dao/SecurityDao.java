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

import io.github.carlos_emr.carlos.commn.model.Security;

public interface SecurityDao extends AbstractDao<Security> {
    List<Security> findAllOrderBy(String columnName);

    List<Security> findByProviderNo(String providerNo);

    List<Security> findByLikeProviderNo(String providerNo);

    List<Security> findByUserName(String userName);

    List<Security> findByOneIdKey(String ssoKey);

    void updateOneIdKey(Security securityRecord);

    /**
     * Compare-and-set for the stored PIN.
     *
     * <p>Writes the new hash only while the row still holds {@code expectedPin}. PIN migration is
     * deferred to the end of a login, so the record may have been changed in between by a
     * self-service PIN change, an admin edit, or a concurrent migration on another node. A plain
     * merge of the object read at authentication time would roll that newer value back to a hash of
     * the old PIN, so the guard is applied in the UPDATE itself rather than read-then-write.</p>
     *
     * <p>This is a bulk update and therefore bypasses the persistence context; callers must
     * synchronise any in-memory copy themselves.</p>
     *
     * @param securityNo    The security row id.
     * @param expectedPin   The stored PIN value the caller validated against; must not be null.
     * @param newPinHash    The replacement hash.
     * @param pinUpdateDate The timestamp to record against the change.
     * @return The number of rows updated: 1 on success, 0 when the stored PIN no longer matches.
     */
    int updatePinHashIfUnchanged(Integer securityNo, String expectedPin, String newPinHash, Date pinUpdateDate);

    List<Security> findByLikeUserName(String userName);

    Security getByProviderNo(String providerNo);

    List<Object[]> findProviders();

    List<Security> findByProviderSite(String providerNo);
}
