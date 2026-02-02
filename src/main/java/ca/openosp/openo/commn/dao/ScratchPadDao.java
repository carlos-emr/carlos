//CHECKSTYLE:OFF
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
 */
package ca.openosp.openo.commn.dao;

import ca.openosp.openo.commn.model.ScratchPad;

import java.util.List;

/**
 * Data Access Object interface for managing ScratchPad entities.
 *
 * <p>ScratchPad provides a temporary note-taking area for healthcare providers
 * to jot down quick notes, reminders, or temporary information during clinical workflows.
 * This DAO supports operations for checking, retrieving, and managing scratch pad entries
 * with version history tracking.</p>
 *
 * @since 2022-12-16
 */
public interface ScratchPadDao extends AbstractDao<ScratchPad> {

    /**
     * Checks if a provider has any non-empty scratch pad content.
     *
     * <p>This method determines whether the provider's current scratch pad
     * contains any text. Empty or whitespace-only content is considered unfilled.</p>
     *
     * @param providerNo String the unique provider identifier
     * @return boolean true if the scratch pad contains non-empty text, false otherwise
     */
    boolean isScratchFilled(String providerNo);

    /**
     * Retrieves the most recent active scratch pad entry for a provider.
     *
     * <p>Returns the latest scratch pad record with status=true for the specified provider.
     * If no active scratch pad exists, returns null.</p>
     *
     * @param providerNo String the unique provider identifier
     * @return ScratchPad the most recent active scratch pad entry, or null if none exists
     */
    ScratchPad findByProviderNo(String providerNo);

    /**
     * Retrieves all historical scratch pad entries for a provider, filtered by date logic.
     *
     * <p>Returns scratch pad entries matching specific criteria:</p>
     * <ul>
     *   <li>All entries from today (current date)</li>
     *   <li>From past dates, only the most recent entry per day (based on dateTime and id)</li>
     * </ul>
     *
     * <p>This provides a historical view of the provider's scratch pad usage while avoiding
     * multiple versions from the same day in the past. Results are ordered by dateTime descending.</p>
     *
     * @param providerNo String the unique provider identifier
     * @return List&lt;ScratchPad&gt; list of filtered scratch pad entries, ordered by date descending
     */
    List<ScratchPad> findAllDatesByProviderNo(String providerNo);
}
