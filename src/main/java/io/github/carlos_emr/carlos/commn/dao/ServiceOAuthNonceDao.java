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

public interface ServiceOAuthNonceDao extends AbstractDao<ServiceOAuthNonce> {

    /**
     * @return the stored nonce whose canonical-tuple hash matches, or
     *         {@code null} when this nonce has not been seen before.
     */
    ServiceOAuthNonce findByNonceKeyHash(String nonceKeyHash);

    /**
     * Removes nonces whose oauth_timestamp is older than the given cutoff so the
     * table only retains entries that could still be replayed.
     *
     * @return the number of rows deleted.
     */
    int deleteOlderThan(long oauthTimestampCutoff);
}
