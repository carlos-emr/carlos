/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.scratch;

import io.github.carlos_emr.carlos.commn.dao.ScratchPadDao;
import io.github.carlos_emr.carlos.commn.model.ScratchPad;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Data access helper for provider scratch pad (notepad) operations.
 *
 * <p>Provides methods to retrieve and persist scratch pad entries for individual providers.
 * Each provider can have multiple versioned scratch pad entries, with the most recent
 * active entry being the current note.</p>
 *
 * <p>Delegates to {@link ScratchPadDao} for database persistence.</p>
 *
 * @see ScratchPadDao
 * @see ScratchPad
 * @since 2026-03-17
 */
public class ScratchData {

	/** Creates a new instance of ScratchData */
	public ScratchData() {
	}
	
	/**
	 * Retrieves all scratch pad entry dates for a specific provider.
	 *
	 * @param providerNo String the provider number to query
	 * @return List of {@link ScratchPad} entries with date information
	 */
	public List<ScratchPad> getAllDates(String providerNo) {
		ScratchPadDao dao = SpringUtils.getBean(ScratchPadDao.class);
		return dao.findAllDatesByProviderNo(providerNo);
	}

	/**
	 * Retrieves the most recent scratch pad entry for a provider as a key-value map.
	 *
	 * @param providerNo String the provider number to query
	 * @return Map with keys "id", "text", and "date"; or {@code null} if no entry exists
	 */
	public Map<String, String> getLatest(String providerNo) {
		ScratchPadDao dao = SpringUtils.getBean(ScratchPadDao.class);
		ScratchPad scratchPad = dao.findByProviderNo(providerNo);
		if (scratchPad == null) return null;

		Map<String, String> retval = new HashMap<String, String>();
		retval.put("id", scratchPad.getId().toString());
		retval.put("text", scratchPad.getText());
		retval.put("date", ConversionUtils.toDateString(scratchPad.getDateTime()));
		return retval;
	}

	/**
	 * Persists a new scratch pad entry with the current timestamp.
	 *
	 * @param providerNo String the provider number to associate with the entry
	 * @param text String the scratch pad text content
	 * @return String the generated ID of the new entry as a string
	 */
	public String insert2(String providerNo, String text) {
		ScratchPad scratchPad = new ScratchPad();
		scratchPad.setProviderNo(providerNo);
		scratchPad.setText(text);
		scratchPad.setDateTime(new Date());

		ScratchPadDao dao = SpringUtils.getBean(ScratchPadDao.class);
		dao.persist(scratchPad);
		return scratchPad.getId().toString();
	}

	/**
	 * Inserts a new scratch pad entry. Delegates to {@link #insert2(String, String)}.
	 *
	 * @param providerNo String the provider number to associate with the entry
	 * @param text String the scratch pad text content
	 * @return String the generated ID of the new entry as a string
	 */
	public String insert(String providerNo, String text) {
		return insert2(providerNo, text);
	}

}
