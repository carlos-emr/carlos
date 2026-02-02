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
 * Modifications made by Magenta Health in 2024.
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Date;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.DemographicData;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Manages the various consents required from patients for participation in specific programs
 * or to share health information with other providers.
 */
public interface PatientConsentManager {

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
    void setConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean consented);

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
    void addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId);

    void addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit);

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
    boolean addConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit, boolean optOut);

    /**
     * Creates a new demographic consent record for the consent policy
     * identified by consentTypeId if one doesn't already exist, or updates
     * the existing demographic consent record if a record already does exist
     *
     * @param loggedinInfo   the user information for the current OSCAR user
     * @param demographic_no the demographic number of the patient
     * @param consentTypeId  the unique identifier of the consent form/policy
     * @param explicit       did the patient give explicit consent, or is consent implied?
     * @param optOut         is the patient refusing this consent policy/form or agreeing to it? A null value indicates the absence of a decision
     * @return true if the consent record was either added or updated, false otherwise
     */
    boolean addEditConsentRecord(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId, boolean explicit, boolean optOut);

    /**
     * Used for removing consent from a patient Consent that was previously consented.
     * Ignored if the patient has never consented.
     * The normal state for consent is FALSE
     */
    void optoutConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId);

    /**
     * Used for removing consent from a patient that previously consented.
     */
    void optoutConsent(LoggedInInfo loggedinInfo, Consent consent);

    /**
     * Used for removing consent from a patient that previously consented. For a Consent object.
     */
    void optoutConsent(LoggedInInfo loggedinInfo, int consentId);

    ConsentType addConsentType(LoggedInInfo loggedinInfo, ConsentType consentType);


    /**
     * Returns a list of all the patient consent types currently active.
     */
    List<ConsentType> getConsentTypes();

    /**
     * @return the list of consent types that are currently marked as active
     */
    List<ConsentType> getActiveConsentTypes();

    /**
     * Returns a consent type by the consent type id.
     * This can be used to determine the consent program for a consent type id.
     */
    ConsentType getConsentTypeByConsentTypeId(int consentTypeId);

    /**
     * Returns a ConsentType by the consent program type. Used to get the id of a ConsentType
     * by its program name.
     */
    ConsentType getConsentType(String type);

    /**
     * Returns a list of patient consents given by a specified patient for a specific ConsentType ID.
     */
    Consent getConsentByDemographicAndConsentType(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId);

    /**
     * Returns a list of patient consents given by a specified patient for a specific ConsentType program.
     * Find the ConsentType object first with getConsentType( String type )
     */
    Consent getConsentByDemographicAndConsentType(LoggedInInfo loggedinInfo, int demographic_no, ConsentType consentType);

    /**
     * Returns a list of all the consentTypes/programs this patient has consented.
     */
    List<Consent> getAllConsentsByDemographic(LoggedInInfo loggedinInfo, int demographic_no);

    /**
     * A boolean determination for if the patient has consented to the given ConsentType/program.
     * A consent is when the consent object exists AND if the patient has not Opted out.
     */
    boolean hasPatientConsented(int demographic_no, ConsentType consentType);

    /**
     * Get all consents by the type indicated that were edited after the date given.
     */
    List<Consent> getConsentsByTypeAndEditDate(LoggedInInfo loggedinInfo, ConsentType consentType, Date editedAfter);

    /**
     * Update Consent status to "deleted".
     * Just in case someone clicks the "Clear" button in the demographic interface because they changed their mind or
     * entered the Opt-in or Opt-out consent by mistake.
     * It is assumed that a record of this should be kept. So this method will delete the consent and update the edit date.
     * A new entry will be inserted into the table should the user change their mind again.
     */
    void deleteConsent(LoggedInInfo loggedinInfo, int demographic_no, int consentTypeId);

    boolean hasProviderSpecificConsent(LoggedInInfo loggedInInfo);

    ConsentType getProviderSpecificConsent(LoggedInInfo loggedInInfo);

    List<? extends DemographicData> filterProviderSpecificConsent(LoggedInInfo loggedInInfo, List<? extends DemographicData> demographicResults);

    List<Integer> getAllDemographicsWithOptinConsentByType(LoggedInInfo loggedinInfo, ConsentType consentTypeId);
}
