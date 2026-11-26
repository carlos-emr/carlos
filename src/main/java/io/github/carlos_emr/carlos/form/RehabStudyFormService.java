/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.forms.RehabStudyFormDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.RehabStudyFormType.Prefill;
import io.github.carlos_emr.carlos.form.model.rehab.RehabStudyAssessment;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Patient-bound load and transactional version save for the 2004 rehabilitation-study
 * questionnaires (issue #5693).
 *
 * <p>Reached only through its Spring proxy, so every save runs in a transaction and joins an
 * enclosing one. Each save inserts a new version attributed to the authenticated provider; the
 * version it was opened from is checked to be the same patient's and left unchanged.</p>
 *
 * @since 2026-11-26
 */
@Service
public class RehabStudyFormService {
    private static final String NOT_ENROLLED = "N/A";

    private final RehabStudyFormDao forms;
    private final DemographicManager demographics;
    private final SecurityInfoManager security;

    /**
     * @param forms the patient-bound questionnaire DAO
     * @param demographics reads the master record a new form copies from
     * @param security checks form and patient-record access
     */
    public RehabStudyFormService(RehabStudyFormDao forms, DemographicManager demographics,
            SecurityInfoManager security) {
        this.forms = forms;
        this.demographics = demographics;
        this.security = security;
    }

    /**
     * Loads a patient's saved version, or the values a new form starts with: the patient number,
     * today's date ({@code yyyy/MM/dd}), the questionnaire's master-record prefill and the study
     * ID ({@code "N/A"} when the patient is not enrolled). A new form reads the master record,
     * which may write a health-care-team audit, so this transaction permits writes.
     *
     * @param actor the authenticated caller
     * @param type the questionnaire
     * @param patient the positive demographic number
     * @param id the saved version ID, or a non-positive value for a new form
     * @return the legacy form properties
     * @throws SecurityException if the actor lacks access, or a positive ID is missing or another patient's
     * @throws IllegalArgumentException if the patient number is not positive
     */
    @Transactional
    public Properties load(LoggedInInfo actor, RehabStudyFormType<?> type, int patient, int id) {
        require(actor, patient, "r");
        if (id > 0) {
            return savedValues(type, patient, id);
        }
        Properties values = new Properties();
        Demographic demographic = demographics.getDemographic(actor, patient);
        if (demographic != null) {
            values.setProperty("demographic_no", Integer.toString(patient));
            values.setProperty("formCreated",
                    UtilDateUtilities.DateToString(new Date(), RehabStudyFormValues.DATE_FORMAT));
            if (type.prefill().contains(Prefill.BIRTH_DATE)) {
                Date birth = UtilDateUtilities.calcDate(StringUtils.trimToEmpty(demographic.getYearOfBirth()),
                        StringUtils.trimToEmpty(demographic.getMonthOfBirth()),
                        StringUtils.trimToEmpty(demographic.getDateOfBirth()));
                values.setProperty("dob", UtilDateUtilities.DateToString(birth, RehabStudyFormValues.DATE_FORMAT));
            }
            if (type.prefill().contains(Prefill.SEX)) {
                values.setProperty("sex", StringUtils.trimToEmpty(demographic.getSex()));
            }
            if (type.prefill().contains(Prefill.PHONE)) {
                values.setProperty("phone", StringUtils.trimToEmpty(demographic.getPhone()));
            }
        }
        values.setProperty("studyID", forms.findStudyId(patient).orElse(NOT_ENROLLED));
        return values;
    }

    /**
     * Refuses, before a questionnaire page renders, what {@link #load} would refuse while it
     * renders: no form or patient-record access, or a saved version that is missing or another
     * patient's. The view gate calls this so a refusal is answered as one, not as a page failure.
     *
     * @param actor the authenticated caller
     * @param type the questionnaire
     * @param patient the positive demographic number
     * @param id the saved version ID, or a non-positive value for a new form
     * @throws SecurityException if the actor lacks access, or a positive ID is missing or another patient's
     * @throws IllegalArgumentException if the patient number is not positive
     */
    @Transactional(readOnly = true)
    public void requireReadable(LoggedInInfo actor, RehabStudyFormType<?> type, int patient, int id) {
        require(actor, patient, "r");
        if (id > 0) requireVersion(type, patient, id);
    }

    /**
     * Appends a version without changing the source. Joins an existing transaction when present;
     * the optional XML copy is written only after commit.
     *
     * @param actor the authenticated caller whose provider number is stored
     * @param type the questionnaire
     * @param patient the positive demographic number
     * @param sourceId the patient's source version, or a non-positive value for a new form
     * @param values the posted form fields; submitted patient, provider and ID values are not trusted
     * @return the generated version ID (subject to rollback of any enclosing transaction)
     * @throws SecurityException if the actor lacks access, or a positive source ID is missing or another patient's
     * @throws IllegalArgumentException if the patient number is not positive, the post has no study ID,
     *         or the provider number is not numeric
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveVersion(LoggedInInfo actor, RehabStudyFormType<?> type, int patient, int sourceId,
            Properties values) {
        require(actor, patient, "w");
        return insert(actor, type, patient, sourceId, values);
    }

    private <E extends RehabStudyAssessment> int insert(LoggedInInfo actor, RehabStudyFormType<E> type, int patient,
            int sourceId, Properties values) {
        if (sourceId > 0) requireVersion(type, patient, sourceId);
        // Every page posts its studyID; the column is NOT NULL and the retired records failed without it.
        if (values.getProperty("studyID") == null) throw new IllegalArgumentException("A study identifier is required");
        // Never the same second as the patient's latest version of this questionnaire (even one
        // saved earlier in this transaction): the edit time is the version's XML archive identity.
        Date edited = FormXmlArchive.nextEditTime(forms.findLatestEdited(type.entity(), patient));
        E form = RehabStudyFormValues.fromProperties(type, values, edited);
        form.setDemographicNo(patient);
        form.setProviderNo(Integer.valueOf(actor.getLoggedInProviderNo()));
        forms.persist(form);
        forms.flush();
        archiveAfterCommit(type, patient, form.getId(), edited);
        return form.getId();
    }

    private <E extends RehabStudyAssessment> Properties savedValues(RehabStudyFormType<E> type, int patient, int id) {
        return RehabStudyFormValues.toProperties(type, requireVersion(type, patient, id));
    }

    private <E extends RehabStudyAssessment> E requireVersion(RehabStudyFormType<E> type, int patient, int id) {
        E form = forms.findForPatient(type.entity(), patient, id);
        if (form == null) throw new SecurityException("missing required sec object (_form)");
        return form;
    }

    private void require(LoggedInInfo actor, int patient, String right) {
        if (patient <= 0) throw new IllegalArgumentException("A positive patient identifier is required");
        PatientRecordAccess.require(security, actor, Integer.toString(patient), "_form", right);
        // Saving a clinical form does not edit the patient's demographic profile.
        PatientRecordAccess.require(security, actor, Integer.toString(patient), "_demographic", "r");
    }

    private void archiveAfterCommit(RehabStudyFormType<?> type, int patient, int id, Date edited) {
        if (!Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("save_as_xml", "false"))) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    // Named for this version's stored edit time, never replacing another version's copy.
                    FormXmlArchive.write(type.table(), patient, edited, forms.readArchive(type.entity(), patient, id));
                } catch (Exception failure) {
                    // The exception class says where it failed (path, permission, XML, read); its
                    // message and trace can carry clinical values or the patient's file name.
                    MiscUtils.getLogger().error("Unable to archive committed rehabilitation study form XML ({});"
                            + " continuing without XML copy", failure.getClass().getSimpleName());
                }
            }
        });
    }
}
