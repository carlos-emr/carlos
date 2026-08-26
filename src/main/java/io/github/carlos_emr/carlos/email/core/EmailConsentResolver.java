package io.github.carlos_emr.carlos.email.core;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Consent;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.commn.model.EmailLog.EmailConsentStatus;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.util.StringUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Resolves the configured patient email-consent type and the patient's current consent state.
 *
 * @since 2026-07-06
 */
@Service
public class EmailConsentResolver {
    private final UserPropertyDAO userPropertyDAO;
    private final PatientConsentManager patientConsentManager;

    /**
     * Creates a resolver backed by the email-consent configuration and patient-consent service.
     *
     * @param userPropertyDAO retrieves the configured email consent type
     * @param patientConsentManager retrieves consent types and patient consent records
     */
    public EmailConsentResolver(UserPropertyDAO userPropertyDAO, PatientConsentManager patientConsentManager) {
        this.userPropertyDAO = userPropertyDAO;
        this.patientConsentManager = patientConsentManager;
    }

    /**
     * Resolves a patient's current email consent, returning {@code NOT_CONFIGURED} when no active
     * consent type is configured and {@code UNKNOWN} when the type exists but the patient has no row.
     *
     * @param loggedInInfo the current provider session
     * @param demographicId the patient demographic identifier
     * @return the current consent decision and its audit identifiers
     */
    public EmailConsentResult resolve(LoggedInInfo loggedInInfo, Integer demographicId) {
        ConsentType consentType = getConfiguredConsentType();
        if (consentType == null) {
            return new EmailConsentResult("", EmailConsentStatus.NOT_CONFIGURED, null, null);
        }

        Consent consent = patientConsentManager.getConsentByDemographicAndConsentType(loggedInInfo, demographicId, consentType);
        if (consent == null) {
            return new EmailConsentResult(consentType.getName(), EmailConsentStatus.UNKNOWN, null, null);
        }

        EmailConsentStatus status = consent.getPatientConsented() ? EmailConsentStatus.OPT_IN : EmailConsentStatus.OPT_OUT;
        return new EmailConsentResult(consentType.getName(), status, consent.getId(), consent.getEditDate());
    }

    /**
     * Reports whether an active email consent type is configured.
     *
     * @return {@code true} when the configured consent type exists and is active
     */
    public boolean isConfigured() {
        return getConfiguredConsentType() != null;
    }

    private ConsentType getConfiguredConsentType() {
        UserProperty userProperty = userPropertyDAO.getProp(UserProperty.EMAIL_COMMUNICATION);
        if (userProperty == null || StringUtils.isNullOrEmpty(userProperty.getValue())) {
            return null;
        }

        String property = getFirstConsentTypeName(userProperty.getValue());
        if (StringUtils.isNullOrEmpty(property)) {
            return null;
        }
        ConsentType consentType = patientConsentManager.getConsentType(property);
        if (consentType == null || !Boolean.TRUE.equals(consentType.isActive())) {
            return null;
        }
        return consentType;
    }

    private String getFirstConsentTypeName(String propertyValue) {
        String[] parts = propertyValue.split("[,;\\s()]+");
        for (String part : parts) {
            if (!StringUtils.isNullOrEmpty(part)) {
                return part;
            }
        }
        return null;
    }
}
