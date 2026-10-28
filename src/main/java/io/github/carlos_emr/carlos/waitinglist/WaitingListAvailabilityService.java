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
package io.github.carlos_emr.carlos.waitinglist;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.WaitingListNameDao;
import io.github.carlos_emr.carlos.commn.model.ProviderPreference;
import io.github.carlos_emr.carlos.commn.model.WaitingListName;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Decides whether the Master Record offers waiting lists, and which ones.
 *
 * <p>The Master Record renders its waiting-list controls from this service and
 * {@code DemographicUpdate} / {@code waitinglist/Add2WaitingList} accept a submitted
 * list only if this service still offers it, so the page and the write path cannot
 * disagree about what "available" means (issue #4229: the pages had inverted the
 * {@value #FEATURE_PROPERTY} switch and made every list read-only when it was on).</p>
 *
 * <p>A list is selectable when the feature is switched on, the list is current
 * ({@code is_history='N'}), and it belongs to the logged-in provider's schedule group
 * ({@link ProviderPreference#getMyGroupNo()}). The group comes from the session copy of
 * the preference because that is the copy the waiting-list management page uses to
 * create list names, and the preference editor refreshes it whenever the group changes.</p>
 *
 * @since 2026-10-15
 */
@Service
public class WaitingListAvailabilityService {

    /** {@code carlos.properties} switch for the patient waiting-list feature. */
    public static final String FEATURE_PROPERTY = "DEMOGRAPHIC_WAITING_LIST";

    private final WaitingListNameDao waitingListNameDao;

    public WaitingListAvailabilityService(WaitingListNameDao waitingListNameDao) {
        this.waitingListNameDao = waitingListNameDao;
    }

    /**
     * Whether the waiting-list feature is switched on.
     *
     * <p>Uses the same exact {@code "true"} test as
     * {@code <oscar:oscarPropertiesCheck property="DEMOGRAPHIC_WAITING_LIST" value="true">},
     * which decides whether the Master Record renders the waiting-list section at all;
     * a looser test here would accept writes from a section the page never shows.</p>
     *
     * @return {@code true} when {@value #FEATURE_PROPERTY} is exactly {@code true}
     */
    public boolean isFeatureEnabled() {
        return "true".equals(CarlosProperties.getInstance().getProperty(FEATURE_PROPERTY));
    }

    /**
     * Whether the Master Record should link to the patient's waiting-list page.
     *
     * <p>The page lists the patient's entries on every current list, whatever group owns
     * it, so the link only needs some current list to exist.</p>
     *
     * @return {@code true} when the feature is on and at least one current list exists
     */
    public boolean hasCurrentLists() {
        return isFeatureEnabled() && waitingListNameDao.countActiveWatingListNames() > 0;
    }

    /**
     * The current lists the provider may add a patient to, ordered by name.
     *
     * @param preference the logged-in provider's preference; may be {@code null}
     * @return the current lists of the provider's schedule group; empty when the feature
     *         is off or the provider has no preference or group
     */
    public List<WaitingListName> findSelectableLists(ProviderPreference preference) {
        if (!isFeatureEnabled() || preference == null || preference.getMyGroupNo() == null) {
            return List.of();
        }
        return waitingListNameDao.findCurrentByGroup(preference.getMyGroupNo());
    }

    /**
     * Whether a submitted list is one {@link #findSelectableLists} offers right now.
     *
     * <p>Write paths call this at submission time rather than trusting the rendered form:
     * the list may have been archived or moved, or the provider's group changed, since
     * the page was drawn.</p>
     *
     * @param preference the logged-in provider's preference; may be {@code null}
     * @param listId     the submitted {@code waitingListName.ID}
     * @return {@code true} when the list is currently selectable for this provider
     */
    public boolean isSelectable(ProviderPreference preference, int listId) {
        if (listId <= 0) {
            return false;
        }
        for (WaitingListName list : findSelectableLists(preference)) {
            if (list.getId() != null && list.getId() == listId) {
                return true;
            }
        }
        return false;
    }
}
