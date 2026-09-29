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
package io.github.carlos_emr.carlos.managers;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.ListIterator;

import io.github.carlos_emr.carlos.commn.dao.ConsentDao;
import io.github.carlos_emr.carlos.commn.dao.ConsentRecords;
import io.github.carlos_emr.carlos.commn.dao.ConsentTypeDao;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.DemographicData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.log.LogAction;

/**
 * Manages the various consents required from patients for participation in specific programs
 * or to share health information with other providers.
 */
// Class-level so that every write entry point, including the overloads that call each other,
// runs the consent edit, the retirement of duplicate records and their audit rows in one
// transaction: a change to a patient's consent does not commit without its audit row. A
// method-level annotation is bypassed when another method of this class calls in (SonarCloud
// S2229). Reads are SUPPORTS: as before, they run without a transaction of their own, so a search
// does not dirty-check every record it has loaded before each query, and a failed audit insert
// does not fail the read.
//
// Writes run at READ COMMITTED, and writes to a patient's consent records first lock the patient's
// row (ConsentDao.lockPatientForConsentChange), as lab routing does. Under MariaDB's default
// REPEATABLE READ a concurrent save failed instead of waiting: two first saves each took only a gap
// lock and deadlocked on insert (1213), and with innodb_snapshot_isolation=ON (the default from
// 11.6) a save that waited on another's lock then failed with 1020 on reading the rows that one had
// changed. Under READ COMMITTED the waiting save reads what the first one committed. Call the write
// methods outside any existing transaction: joined to one, they run at the caller's isolation, and
// at REPEATABLE READ those failures come back.
@Service
@Transactional(isolation = Isolation.READ_COMMITTED)
public class PatientConsentManagerImpl implements PatientConsentManager {

    /** Audit-log content name for entries about one consent record. */
    private static final String CONSENT_LOG_CONTENT = "consent";
    private static final String LOG_CONSENT_ID = " ConsentId: ";
    private static final String LOG_CONSENT_TYPE_ID = " ConsentTypeId: ";

    @Autowired
    private ConsentDao consentDao;

    @Autowired
    private ConsentTypeDao consentTypeDao;

    @Autowired
    private SecurityInfoManager securityInfoManager;

    public PatientConsentManagerImpl() {
        // default constructor.
    }

    /**
     * Set a patient consent based on the demographic, consent type and boolean consent.
     * The "consented" parameter will not accept NULL.
     * <p>
     * True = patient fully consents
     * False = patient has opted out (revoked)
     * <p>
     * This method first assumes that a Consent entry already exists for the given patient.
     * If a Consent exists: then the Consent will be set according to the given boolean parameter.
     * If a Consent does not exist: and the consented parameter is true - a new "consented" Consent will be added - a new
     * consent object will not be added if consented parameter is false.
     * <p>
     * This method sets the boolean "explicit" ( patient gave direct consent = true; patient consent was implied or assumed = false)
     * to a default TRUE.
     */
    public void setConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean consented) {
        if (consented) {
            addConsent(loggedinInfo, demographic_no, consentTypeId, consented, Boolean.FALSE);
        } else {
            optoutConsent(loggedinInfo, demographic_no, consentTypeId);
        }
    }

    /**
     * Add a new Consent from a consenting patient by consentType id.
     * The given consent type id is cross checked in the available consentTypes.
     * This method first assumes that a Consent entry already exists for the given patient and
     * then updates it as required.
     * <p>
     * The default state for Consent is FALSE. This means that a patient is assume non-consenting until
     * a Consent is set. Therefore there is no need to set this if a patient has not expressed consent.
     * <p>
     * This method sets the boolean "explicit" ( patient gave direct consent = true; patient consent was implied or assumed = false)
     * to a default TRUE.
     * <p>
     * Sets default optout to FALSE.
     */
    public void addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId) {
        addConsent(loggedinInfo, demographic_no, consentTypeId, Boolean.TRUE);
    }

    public void addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit) {
        addConsent(loggedinInfo, demographic_no, consentTypeId, explicit, Boolean.FALSE);
    }

    /**
     * Add a new Consent from a consenting patient by consentType id.
     * The given consent type id is cross checked in the available consentTypes.
     * The default state for Consent is FALSE. This means that a patient is assume non-consenting until
     * a Consent is set.
     * <p>
     * The extra parameter: explicit can be used to determine if this consent was implied (explicit=false) by the patient
     * or if the consent was explicit (explicit=true).  In most cases the patient will always be required to give
     * a verbal or written explicit consent.
     * <p>
     * The Explicit parameter will not accept a null value.
     * EXPLICIT CONSENT: patient gave direct consent. explicit = true;
     * IMPLIED CONSENT: patient consent was implied or assumed. explicit = false
     */
    public boolean addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit, boolean optOut) {

        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        return addEditConsentRecord(loggedinInfo, demographic_no, consentTypeId, explicit, optOut);

    }

    /**
     * Creates a new demographic consent record for the consent policy
     * identified by consentTypeId if one doesn't already exist, or updates
     * the existing demographic consent record if a record already does exist.
     * <p>
     * When the patient has several live records of this type, the deciding one
     * ({@link ConsentRecords#effective}) is updated and the others are soft-deleted, each
     * audit-logged, so one live record remains.
     *
     * @param loggedinInfo   the user information for the current OSCAR user
     * @param demographic_no the demographic number of the patient
     * @param consentTypeId  the unique identifier of the consent form/policy
     * @param explicit       did the patient give explicit consent, or is consent implied?
     * @param optOut         is the patient refusing this consent policy/form or agreeing to it? A null value indicates the absence of a decision
     * @return true if the consent record was either added or updated, false otherwise
     */
    public boolean addEditConsentRecord(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit, boolean optOut) {
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.createConsent", " Demographic: " + demographic_no);

        boolean addOrUpdateDbComplete = false;

        ConsentType consentType = getConsentTypeByConsentTypeId(consentTypeId);

        if (consentType != null && consentType.isActive()) {
            // Edit the deciding record, the one staff were shown, and retire any other live
            // duplicates below so the chart ends with exactly one record (#3845).
            // The patient lock is held until commit, so a concurrent save, clear or opt-out of this
            // patient's consent waits, then reads this one's result.
            consentDao.lockPatientForConsentChange(demographic_no);
            List<Consent> live = consentDao.findLiveByDemographicAndConsentTypeIdForUpdate(demographic_no, consentType.getId());
            Consent consent = ConsentRecords.effective(live);
            Date currentDate = null;
            // What the record said before this save, for the audit entry: consent keeps no history.
            String priorChoice = consent == null ? "none" : describeChoice(consent.isOptout());

            if (consent == null) {
                consent = new Consent();
                consent.setConsentType(consentType);
                consent.setExplicit(explicit);
                consent.setDemographicNo(demographic_no);
            }

            // This is to ensure that the dates and user entry id are not being updated on EVERY post.
            if (optOut != consent.isOptout() || consent.getId() == null) {
                currentDate = new Date(System.currentTimeMillis());
                consent.setOptout(optOut);
            }

            if (optOut && currentDate != null) {
                consent.setOptoutDate(currentDate);
            } else if (currentDate != null) {
                consent.setConsentDate(currentDate);
            }

            if (currentDate != null) {
                consent.setEditDate(currentDate);
                consent.setLastEnteredBy(loggedinInfo.getLoggedInProviderNo());
            }

            if (consent.getId() == null) {
                consentDao.persist(consent);
                addOrUpdateDbComplete = true;
            } else if (consent.getId() > 0) {
                consentDao.merge(consent);
                addOrUpdateDbComplete = true;
            }
            if (currentDate != null && addOrUpdateDbComplete) {
                // Only a save that changed the decision: every chart save re-posts the shown choice.
                LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.changeConsent", CONSENT_LOG_CONTENT,
                        String.valueOf(consent.getId()), demographic_no, " Demographic: " + demographic_no
                                + LOG_CONSENT_TYPE_ID + consentType.getId() + LOG_CONSENT_ID + consent.getId()
                                + " Choice: " + priorChoice + "->" + describeChoice(optOut));
            }
            retireDuplicates(loggedinInfo, demographic_no, consentType.getId(), live, consent);
        }

        return addOrUpdateDbComplete;
    }

    private static String describeChoice(boolean optOut) {
        return optOut ? "opt-out" : "opt-in";
    }

    /**
     * Soft-deletes every live record other than {@code kept}. Only {@code deleted} is set: each
     * row keeps its own author and edit date, since no one chose to change it, and the audit log
     * records who retired it and when.
     */
    private void retireDuplicates(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, List<Consent> live, Consent kept) {
        for (Consent duplicate : live) {
            if (duplicate == kept || duplicate.getId() == null || duplicate.getId().equals(kept.getId())) {
                continue;
            }
            duplicate.setDeleted(Boolean.TRUE);
            consentDao.merge(duplicate);
            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.retireDuplicateConsent", CONSENT_LOG_CONTENT,
                    String.valueOf(duplicate.getId()), demographic_no, " Demographic: " + demographic_no
                            + LOG_CONSENT_TYPE_ID + consentTypeId + LOG_CONSENT_ID + duplicate.getId() + " KeptConsentId: " + kept.getId());
        }
    }

    /**
     * Used for removing consent from a patient Consent that was previously consented.
     * Ignored if the patient has never consented.
     * The normal state for consent is FALSE
     * <p>
     * Goes through {@link #addEditConsentRecord}, so the deciding record opts out and any other live
     * duplicates are retired, as a chart save does. Requires write privilege on the patient.
     */
    public void optoutConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId) {
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        // Lock before the first read, so the records read here are the ones the opt-out then edits.
        consentDao.lockPatientForConsentChange(demographic_no);

        Consent consent = getConsentByDemographicAndConsentType(loggedinInfo, demographic_no, consentTypeId);

        if (consent != null) {

            addEditConsentRecord(loggedinInfo, demographic_no, consentTypeId, consent.isExplicit(), true);

            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.optoutConsent[demographic_no, consentID]", " Changing to Opt Out for Consent ConsentTypeId: "
                    + consentTypeId + " Demographic: " + demographic_no);
        }

    }

    /**
     * Used for removing consent from a patient that previously consented.
     */
    public void optoutConsent(LoggedInInfo loggedinInfo, Consent consent) {

        if (consent == null) {
            return;
        }

        optoutConsent(loggedinInfo, consent.getId());

    }

    /**
     * Used for removing consent from a patient that previously consented. For a Consent object.
     * A record that has been deleted or retired is left as it is.
     */
    public void optoutConsent(LoggedInInfo loggedinInfo, int consentId) {

        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        Consent consent = consentDao.find(consentId);

        if (consent == null) {
            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.optoutConsent[consentID]",
                    LOG_CONSENT_ID + consentId + " skipped: no live record");
            return;
        }
        if (consent.getDemographicNo() == null) {
            // A record without a patient cannot be checked against one or locked, so it is left alone.
            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.optoutConsent[consentID]",
                    LOG_CONSENT_ID + consentId + " skipped: record has no patient");
            return;
        }
        Integer demographicNo = consent.getDemographicNo();
        // The patient is known now, so honour per-patient restrictions as the other writes do.
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, demographicNo)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        // Lock, then re-read the row: a concurrent clear or save may have changed or retired it
        // since find(), and merging the stale copy would write the old values back.
        consentDao.lockPatientForConsentChange(demographicNo);
        consentDao.refresh(consent);

        if (consent.isDeleted()) {
            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.optoutConsent[consentID]", CONSENT_LOG_CONTENT,
                    String.valueOf(consentId), demographicNo, LOG_CONSENT_ID + consentId + " skipped: no live record");
            return;
        }

        Date date = new Date(System.currentTimeMillis());
        consent.setOptout(Boolean.TRUE);
        consent.setOptoutDate(date);
        consent.setEditDate(date);
        consent.setLastEnteredBy(loggedinInfo.getLoggedInProviderNo());
        consentDao.merge(consent);
        LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.optoutConsent[consentID]", CONSENT_LOG_CONTENT,
                String.valueOf(consentId), demographicNo, LOG_CONSENT_ID + consentId);
    }

    /**
     * Creates a consent type. Consent types are clinic configuration, so this requires write
     * privilege on {@code _admin}.
     */
    public ConsentType addConsentType(LoggedInInfo loggedinInfo, ConsentType consentType) {
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_admin", SecurityInfoManager.WRITE, null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        LogAction.addLog(loggedinInfo.getLoggedInProviderNo(), "PatientConsentManager.addConsentType", consentType.getType(), consentType.toString());

        consentTypeDao.persist(consentType);
        return consentType;
    }


    /**
     * Returns a list of all the patient consent types currently active.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ConsentType> getConsentTypes() {

        List<ConsentType> consentTypeList = null;
        int count = consentTypeDao.getCountAll();
        if (count > 0) {
            consentTypeList = consentTypeDao.findAll(0, count);
        }

        return consentTypeList;
    }

    /**
     * @return the list of consent types that are currently marked as active
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<ConsentType> getActiveConsentTypes() {
        return consentTypeDao.findAllActive();
    }

    /**
     * Returns a consent type by the consent type id.
     * This can be used to determine the consent program for a consent type id.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public ConsentType getConsentTypeByConsentTypeId(int consentTypeId) {
        return consentTypeDao.find(consentTypeId);
    }

    /**
     * Returns a ConsentType by the consent program type. Used to get the id of a ConsentType
     * by its program name.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public ConsentType getConsentType(String type) {
        return consentTypeDao.findConsentType(type);
    }

    /**
     * Returns a list of patient consents given by a specified patient for a specific ConsentType ID.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Consent getConsentByDemographicAndConsentType(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId) {
        Consent consent = null;
        ConsentType consentType = getConsentTypeByConsentTypeId(consentTypeId);
        if (consentType != null && consentType.isActive()) {
            consent = getConsentByDemographicAndConsentType(loggedinInfo, demographic_no, consentType);
        }
        return consent;
    }

    /**
     * Returns a list of patient consents given by a specified patient for a specific ConsentType program.
     * Find the ConsentType object first with getConsentType( String type )
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public Consent getConsentByDemographicAndConsentType(LoggedInInfo loggedinInfo, int demographic_no, ConsentType consentType) {
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.READ, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        if (consentType == null) {
            return null;
        }

        LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.getConsentByDemographicAndConsentType",
                " Demographic: " + demographic_no + LOG_CONSENT_TYPE_ID + consentType.getId());

        return consentDao.findByDemographicAndConsentTypeId(demographic_no, consentType.getId());
    }

    /**
     * Returns the deciding record for each consent type the patient has a live record for.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Consent> getAllConsentsByDemographic(LoggedInInfo loggedinInfo, int demographic_no) {

        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.READ, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        // The chart shows these and writes the shown choice back on every save, so it must show the
        // record that decides. Showing a duplicate opt-in would silently reverse an opt-out (#3845).
        List<Consent> consent = ConsentRecords.effectivePerType(consentDao.findByDemographic(demographic_no));

        LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.getAllConsentsByDemographic",
                " Demographic: " + demographic_no);

        return consent;
    }

    /**
     * A boolean determination for if the patient has consented to the given ConsentType/program.
     * A consent is when the consent object exists AND if the patient has not Opted out.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public boolean hasPatientConsented(int demographic_no, ConsentType consentType) {

        Consent consent = null;
        boolean consented = Boolean.FALSE;

        if (consentType != null && consentType.isActive()) {
            consent = consentDao.findByDemographicAndConsentTypeId(demographic_no, consentType.getId());
        }

        if (consent != null) {
            consented = consent.getPatientConsented();
        }

        return consented;
    }

    /**
     * Get all consents by the type indicated that were edited after the date given.
     * <p>
     * Returns every live record edited since then, duplicates included; its caller,
     * {@code DemographicWs}, uses only the patient ids. Empty when there is no consent type.
     */
    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Consent> getConsentsByTypeAndEditDate(LoggedInInfo loggedinInfo, ConsentType consentType, Date editedAfter) {
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.READ, null)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
        if (consentType == null) {
            return new ArrayList<>();
        }

        List<Consent> consentList = consentDao.findLastEditedByConsentTypeId(consentType.getId(), editedAfter);

        LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.getConsentsByTypeAndEditDate",
                LOG_CONSENT_TYPE_ID + consentType.getId());

        return consentList;
    }

    /**
     * Update Consent status to "deleted".
     * Just in case someone clicks the "Clear" button in the demographic interface because they changed their mind or
     * entered the Opt-in or Opt-out consent by mistake.
     * It is assumed that a record of this should be kept. So this method soft-deletes every live record of the
     * type, duplicates included, setting its edit date and author, and audit-logs each one.
     * A new entry will be inserted into the table should the user change their mind again.
     * Requires write privilege on the patient. An unknown or inactive consent type changes nothing.
     */
    public void deleteConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId) {
        // Clearing a consent changes the chart, so it needs write privilege on the patient, as saving one does.
        if (!securityInfoManager.hasPrivilege(loggedinInfo, "_demographic", SecurityInfoManager.WRITE, demographic_no)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }

        ConsentType consentType = getConsentTypeByConsentTypeId(consentTypeId);
        if (consentType == null || !consentType.isActive()) {
            return;
        }
        // Delete every live record: with duplicates, deleting only one left the others deciding.
        consentDao.lockPatientForConsentChange(demographic_no);
        Date now = new Date(System.currentTimeMillis());
        for (Consent consent : consentDao.findLiveByDemographicAndConsentTypeIdForUpdate(demographic_no, consentTypeId)) {
            consent.setDeleted(Boolean.TRUE);
            consent.setEditDate(now);
            consent.setLastEnteredBy(loggedinInfo.getLoggedInProviderNo());
            consentDao.merge(consent);
            LogAction.addLogSynchronous(loggedinInfo, "PatientConsentManager.deleteConsent()", CONSENT_LOG_CONTENT,
                    String.valueOf(consent.getId()), demographic_no,
                    " Demographic: " + demographic_no + LOG_CONSENT_TYPE_ID + consentTypeId + LOG_CONSENT_ID + consent.getId());
        }
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public boolean hasProviderSpecificConsent(LoggedInInfo loggedInInfo) {
        ConsentType conType = consentTypeDao.findConsentTypeForProvider(ConsentType.PROVIDER_CONSENT_FILTER, loggedInInfo.getLoggedInProviderNo());
        if (conType == null) {
            return false;
        }
        return true;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public ConsentType getProviderSpecificConsent(LoggedInInfo loggedInInfo) {
        ConsentType conType = consentTypeDao.findConsentTypeForProvider(ConsentType.PROVIDER_CONSENT_FILTER, loggedInInfo.getLoggedInProviderNo());
        return conType;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<? extends DemographicData> filterProviderSpecificConsent(LoggedInInfo loggedInInfo, List<? extends DemographicData> demographicResults) {
        ConsentType consentType = getProviderSpecificConsent(loggedInInfo);
        if (consentType != null) {
            ListIterator<? extends DemographicData> iter = demographicResults.listIterator();
            while (iter.hasNext()) {
                int demographicNo = iter.next().getDemographicNo();
                if (!hasPatientConsented(demographicNo, consentType)) {
                    iter.remove();
                }
            }
        }

        return demographicResults;
    }

    @Transactional(propagation = Propagation.SUPPORTS)
    public List<Integer> getAllDemographicsWithOptinConsentByType(LoggedInInfo loggedinInfo, ConsentType consentTypeId) {
        return consentDao.findAllDemoIdsConsentedToType(consentTypeId.getId());
    }
}
