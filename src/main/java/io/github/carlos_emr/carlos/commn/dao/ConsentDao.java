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

import io.github.carlos_emr.carlos.commn.model.Consent;

public interface ConsentDao extends AbstractDao<Consent> {

    /**
     * @return the deciding live record for the patient and type: any opt-out wins, otherwise the
     *     most recently edited; see {@link ConsentRecords}. {@code null} when there is none.
     */
    public Consent findByDemographicAndConsentTypeId(int demographic_no, int consentTypeId);

    /** @return every live record for the patient and type, most recently edited first */
    public List<Consent> findLiveByDemographicAndConsentTypeId(int demographic_no, int consentTypeId);

    /**
     * Write-locks the patient's {@code demographic} row until the caller's transaction ends.
     * Every consent save, clear and opt-out takes this lock before it reads the patient's
     * consent records, so changes to one patient's consent run one after another. That includes
     * the first save, when there is no consent record to lock yet. It locks one row by primary
     * key, so it takes no gap lock and does not block other patients. Requires an existing
     * transaction. Nothing is locked when the patient does not exist.
     */
    public void lockPatientForConsentChange(int demographic_no);

    /**
     * As {@link #findLiveByDemographicAndConsentTypeId}, write-locking the rows until the caller's
     * transaction ends. Take {@link #lockPatientForConsentChange} first: these row locks alone do
     * not serialise a first save, which has no row to lock. Requires an existing transaction.
     */
    public List<Consent> findLiveByDemographicAndConsentTypeIdForUpdate(int demographic_no, int consentTypeId);

    /**
     * @return the deciding live record, per {@link ConsentRecords#effective}, among the patient's
     *     records of every active consent type with this type key ({@code ConsentType.type}, not
     *     its display name). {@code null} when there is none.
     */
    public Consent findByDemographicAndConsentType(int demographic_no, String consentType);

    public List<Consent> findByDemographic(int demographic_no);

    public List<Consent> findLastEditedByConsentTypeId(int consentTypeId, Date lastEditDate);

    public List<Integer> findAllDemoIdsConsentedToType(int consentTypeId);

}
