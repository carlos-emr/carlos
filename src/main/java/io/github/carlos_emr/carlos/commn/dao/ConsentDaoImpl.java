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

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import jakarta.persistence.LockModeType;
import jakarta.persistence.Query;
import jakarta.persistence.TemporalType;
import jakarta.persistence.TypedQuery;

import io.github.carlos_emr.carlos.commn.model.Consent;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class ConsentDaoImpl extends AbstractDaoImpl<Consent> implements ConsentDao {

    protected ConsentDaoImpl() {
        super(Consent.class);
    }

    /**
     * Returns the deciding live record. The table has no unique key on patient and type, so
     * several live records can exist (#3845).
     *
     * @param demographic_no the demographic ID
     * @param consentTypeId the consent type ID
     * @return the deciding record per {@link ConsentRecords#effective}, or null if not found
     */
    @Override
    public Consent findByDemographicAndConsentTypeId(int demographic_no, int consentTypeId) {
        return ConsentRecords.effective(findLiveByDemographicAndConsentTypeId(demographic_no, consentTypeId));
    }

    @Override
    public List<Consent> findLiveByDemographicAndConsentTypeId(int demographic_no, int consentTypeId) {
        return mostRecentFirst(liveQuery(demographic_no, consentTypeId));
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void lockPatientForConsentChange(int demographic_no) {
        // Native and scalar, so no Demographic entity or its eager associations are loaded or locked.
        entityManager.createNativeQuery("SELECT demographic_no FROM demographic WHERE demographic_no = ?1 FOR UPDATE")
                .setParameter(1, demographic_no)
                .getResultList();
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public List<Consent> findLiveByDemographicAndConsentTypeIdForUpdate(int demographic_no, int consentTypeId) {
        TypedQuery<Consent> query = liveQuery(demographic_no, consentTypeId);
        query.setLockMode(LockModeType.PESSIMISTIC_WRITE);
        List<Consent> locked = mostRecentFirst(query);
        // A locking query hands back an entity this transaction already loaded with the state it
        // read before the lock. Re-read each, so the caller edits what the locked row now holds.
        for (Consent consent : locked) {
            entityManager.refresh(consent);
        }
        // Again, now on what the rows hold: a re-read may have changed an edit date.
        locked.sort(ConsentRecords.MOST_RECENT_FIRST);
        return locked;
    }

    private TypedQuery<Consent> liveQuery(int demographic_no, int consentTypeId) {
        TypedQuery<Consent> query = entityManager.createQuery(
                "select x from Consent x where x.demographicNo=?1 and x.consentTypeId=?2 AND x.deleted=false",
                Consent.class);
        query.setParameter(1, demographic_no);
        query.setParameter(2, consentTypeId);
        return query;
    }

    /**
     * Deleted records and inactive types are excluded, as in the manager's lookups by id: DHIR
     * immunization submissions use this lookup, and neither may authorize one.
     */
    @Override
    public Consent findByDemographicAndConsentType(int demographic_no, String consentType) {
        TypedQuery<Consent> query = entityManager.createQuery(
                "select x from Consent x where x.demographicNo=?1 and x.consentType.type=?2"
                        + " AND x.consentType.active=true AND x.deleted=false", Consent.class);
        query.setParameter(1, demographic_no);
        query.setParameter(2, consentType);
        return ConsentRecords.effective(query.getResultList());
    }

    private static List<Consent> mostRecentFirst(TypedQuery<Consent> query) {
        List<Consent> consents = new ArrayList<>(query.getResultList());
        // Newest first, for display and for callers that walk the list. The deciding record is
        // chosen by ConsentRecords.effective, which also weighs opt-out and explicit: it is not
        // necessarily the first element here.
        consents.sort(ConsentRecords.MOST_RECENT_FIRST);
        return consents;
    }

    @Override
    public List<Consent> findByDemographic(int demographic_no) {
        String sql = "select x from " + modelClass.getSimpleName() + " x where x.demographicNo=?1 AND x.deleted=false";
        Query query = entityManager.createQuery(sql);
        query.setParameter(1, demographic_no);

        @SuppressWarnings("unchecked")
        List<Consent> consent = query.getResultList();
        return consent;
    }

    @Override
    public List<Consent> findLastEditedByConsentTypeId(int consentTypeId, Date lastEditDate) {
        String sql = "SELECT x FROM "
                + modelClass.getSimpleName()
                + " x WHERE x.consentTypeId = ?1"
                + " AND x.editDate  > ?2 AND x.deleted=false";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, consentTypeId);
        query.setParameter(2, lastEditDate, TemporalType.TIMESTAMP);

        @SuppressWarnings("unchecked")
        List<Consent> consents = query.getResultList();
        return consents;
    }

    /**
     * Returns all demographic ids that have consented (opt-in) to the given consent
     * type id. Each patient appears once, and a patient with a live opt-out on any record of the
     * type is left out, as in {@link ConsentRecords#effective}.
     *
     * @param consentTypeId the consent type id
     * @return the ids of the consented patients; empty when there are none
     */
    @Override
    public List<Integer> findAllDemoIdsConsentedToType(int consentTypeId) {
        String sql = "SELECT DISTINCT x.demographicNo FROM Consent x WHERE x.consentTypeId = ?1"
                + " AND x.optout = false AND x.deleted = false"
                // A live opt-out on a duplicate record wins, as in ConsentRecords.effective.
                + " AND NOT EXISTS (SELECT y FROM Consent y"
                + " WHERE y.demographicNo = x.demographicNo AND y.consentTypeId = ?1"
                + " AND y.optout = true AND y.deleted = false)";

        Query query = entityManager.createQuery(sql);
        query.setParameter(1, consentTypeId);

        @SuppressWarnings("unchecked")
        List<Integer> consents = query.getResultList();
        if (consents == null) {
            consents = Collections.emptyList();
        }
        return consents;
    }

}
