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

import io.github.carlos_emr.carlos.commn.model.SecurityToken;

public interface SecurityTokenDao extends AbstractDao<SecurityToken> {
    /** The SOAP service validates absolute epoch expiry from its namespaced metadata. */
    default SecurityToken findSoapToken(String tokenHash) {
        if (tokenHash == null || !tokenHash.matches("soap1:[0-9a-f]{64}")) throw new IllegalArgumentException("Invalid SOAP token hash");
        SecurityToken match = null;
        int offset = 0;
        while (true) {
            var page = findAll(offset, 500);
            for (SecurityToken row : page) {
                if (row.getToken() == null || !java.security.MessageDigest.isEqual(
                        tokenHash.getBytes(java.nio.charset.StandardCharsets.US_ASCII),
                        row.getToken().getBytes(java.nio.charset.StandardCharsets.US_ASCII))) continue;
                if (match != null) throw new IllegalStateException("Ambiguous SOAP token storage");
                match = row;
            }
            if (page.size() < 500) return match;
            offset = Math.addExact(offset, page.size());
        }
    }

    /** Compatibility implementation for custom DAOs; bounded pages retain unrelated token purposes. */
    default int revokeSoapTokens(String ownerPrefix) {
        if (ownerPrefix == null || !ownerPrefix.matches("soap1:[1-9][0-9]*:")) throw new IllegalArgumentException("Invalid SOAP token owner");
        int removed = 0;
        int offset = 0;
        while (true) {
            var rows = java.util.List.copyOf(findAll(offset, 500));
            int pageRemoved = 0;
            for (SecurityToken row : rows) {
                if (row.getToken() != null && row.getToken().startsWith("soap1:")
                        && row.getData() != null && row.getData().startsWith(ownerPrefix)) {
                    remove(row);
                    pageRemoved++;
                }
            }
            removed = Math.addExact(removed, pageRemoved);
            if (rows.size() < 500) return removed;
            offset = Math.addExact(offset, rows.size() - pageRemoved);
        }
    }

    SecurityToken getByTokenAndExpiry(String token, Date expiry);
}
