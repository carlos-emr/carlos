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
package io.github.carlos_emr.carlos.messenger.util;

import io.github.carlos_emr.carlos.commn.dao.OscarCommLocationsDao;
import io.github.carlos_emr.carlos.commn.model.MessageList;
import java.util.HashSet;
import java.util.Set;

/**
 * Identifies this clinic's message recipients without confusing provider numbers reused remotely.
 *
 * @since 2026-10-24
 */
public final class LocalMessageRecipients {
    private LocalMessageRecipients() { }

    /**
     * Resolves all currently configured clinic locations and the legacy local location zero.
     * Call before changing recipient rows so a configuration lookup failure leaves them intact.
     * Non-current locations can describe remote address books; the schema does not distinguish
     * them from former clinic locations, so they must not confer local mailbox ownership.
     *
     * @param locations OscarCommLocationsDao the clinic location repository
     * @return Set of Integer location identifiers belonging to the local clinic
     */
    public static Set<Integer> locationIds(OscarCommLocationsDao locations) {
        Set<Integer> local = new HashSet<>();
        local.add(0);
        locations.findByCurrent1(1).forEach(location -> local.add(location.getId()));
        return local;
    }

    /**
     * Checks whether a provider's recipient row addresses this clinic, including legacy rows.
     * The caller must first select rows for the authenticated provider and requested message.
     *
     * @param recipient MessageList the provider-specific recipient row
     * @param localLocations Set of Integer location identifiers resolved by {@link #locationIds}
     * @return boolean true only for a local location without a remote destination facility
     */
    public static boolean includes(MessageList recipient, Set<Integer> localLocations) {
        return recipient.getDestinationFacilityId() < 1 && localLocations.contains(recipient.getRemoteLocation());
    }
}
