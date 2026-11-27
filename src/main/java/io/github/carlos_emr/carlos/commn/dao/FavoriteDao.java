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
import io.github.carlos_emr.carlos.commn.model.Favorite;

public interface FavoriteDao extends AbstractDao<Favorite> {
    List<Favorite> findByProviderNo(String providerNo);
    /**
     * Finds an existing favorite with every persisted prescribing value equal.
     *
     * @param candidate Favorite the candidate favorite to match against persisted rows
     * @return Favorite the earliest matching persisted favorite, or {@code null} if none matches
     */
    Favorite findDuplicate(Favorite candidate);

    /**
     * Persists a new favorite or returns an existing matching favorite for the same provider under a provider row lock.
     *
     * <p>Callers must read the returned {@link Favorite} for its persisted identifier rather than assuming
     * {@code candidate} was inserted, because an existing matching row may be returned unchanged.</p>
     *
     * @param candidate Favorite the favorite candidate to persist or match
     * @return Favorite the newly persisted or pre-existing matching favorite
     * @throws IllegalArgumentException if {@code candidate.getProviderNo()} is {@code null} or does not reference an existing provider
     */
    Favorite createOrFind(Favorite candidate);
    Favorite findByEverything(String providerNo, String favoriteName, String bn, String gcn_SEQNO, String customName, float takeMin, float takeMax, String frequencyCode, String duration, String durationUnit, String quantity, int repeat, boolean nosubsInt, boolean prnInt, String parsedSpecial, String gn, String unitName, boolean customInstr);
}
