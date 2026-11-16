/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.forms.FormGrowth0To36Dao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.FormGrowth0To36;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PatientRecordAccess;
import java.nio.file.Path;
import java.util.Date;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Patient-bound load and transactional version save for the Growth 0–36 month form.
 *
 * @since 2026-11-06
 */
@Service
public class Growth0To36FormService {
    private final FormGrowth0To36Dao forms;
    private final DemographicManager demographics;
    private final SecurityInfoManager security;

    /** Creates the service with the patient-bound DAO and demographic/access managers. */
    public Growth0To36FormService(FormGrowth0To36Dao forms, DemographicManager demographics, SecurityInfoManager security) {
        this.forms = forms;
        this.demographics = demographics;
        this.security = security;
    }

    /**
     * Loads a patient-owned version, or new-form defaults with current demographic prefill.
     * Demographic lookup may write a health-care-team audit, so this transaction permits writes.
     *
     * @param actor the authenticated caller
     * @param patient the positive demographic number
     * @param id the saved version ID, or a non-positive value for a new form
     * @return the legacy form properties with current sex and date of birth
     * @throws SecurityException if the actor lacks access, or a positive ID is missing or foreign
     * @throws IllegalArgumentException if the patient number is not positive
     */
    @Transactional
    public Properties load(LoggedInInfo actor, int patient, int id) {
        require(actor, patient, "r");
        Properties values;
        if (id > 0) {
            values = Growth0To36FormValues.toProperties(requireVersion(patient, id));
        } else {
            values = new Properties();
        }
        Demographic demographic = demographics.getDemographic(actor, patient);
        if (demographic != null) {
            if (id <= 0) {
                values.setProperty("demographic_no", Integer.toString(patient));
                String today = UtilDateUtilities.DateToString(new Date(), Growth0To36FormValues.DATE_FORMAT);
                values.setProperty("formCreated", today);
                values.setProperty("formEdited", today);
                values.setProperty("patientName", StringUtils.trimToEmpty(demographic.getFirstName()) + " "
                        + StringUtils.trimToEmpty(demographic.getLastName()));
            }
            values.setProperty("patientSex", StringUtils.trimToEmpty(demographic.getSex()));
            Date birth = UtilDateUtilities.calcDate(demographic.getYearOfBirth(), demographic.getMonthOfBirth(), demographic.getDateOfBirth());
            values.setProperty("dateOfBirth", UtilDateUtilities.DateToString(birth, Growth0To36FormValues.DATE_FORMAT));
        }
        return values;
    }

    /**
     * Appends a version attributed to the authenticated provider without changing the source.
     * Joins an existing transaction when present; optional XML is published only after commit.
     *
     * @param actor the authenticated caller whose provider number is stored
     * @param patient the positive demographic number
     * @param sourceId the patient-owned source version, or a non-positive value for a new form
     * @param values the legacy form properties; submitted patient/provider/ID values are not trusted
     * @return the generated version ID (subject to rollback of any enclosing transaction)
     * @throws SecurityException if the actor lacks access, or a positive source ID is missing or foreign
     * @throws IllegalArgumentException if the patient number is not positive or the provider number is invalid
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveVersion(LoggedInInfo actor, int patient, int sourceId, Properties values) {
        require(actor, patient, "w");
        if (sourceId > 0) requireVersion(patient, sourceId);
        FormGrowth0To36 form = Growth0To36FormValues.fromProperties(values);
        form.setDemographicNo(patient);
        form.setProviderNo(Integer.valueOf(actor.getLoggedInProviderNo()));
        forms.persist(form);
        forms.flush();
        archiveAfterCommit(patient, form.getId());
        return form.getId();
    }

    private FormGrowth0To36 requireVersion(int patient, int id) {
        FormGrowth0To36 form = forms.findForPatient(patient, id);
        if (form == null) throw new SecurityException("missing required sec object (_form)");
        return form;
    }

    private void require(LoggedInInfo actor, int patient, String right) {
        if (patient <= 0) throw new IllegalArgumentException("A positive patient identifier is required");
        PatientRecordAccess.require(security, actor, Integer.toString(patient), "_form", right);
        // Saving a clinical form does not edit the patient's demographic profile.
        PatientRecordAccess.require(security, actor, Integer.toString(patient), "_demographic", "r");
    }

    private void archiveAfterCommit(int patient, int id) {
        if (!Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("save_as_xml", "false"))) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    String now = UtilDateUtilities.DateToString(new Date(), "yyyyMMddHHmmss");
                    Path file = Path.of(CarlosProperties.getInstance().getProperty("form_record_path", "/root"))
                            .resolve("formGrowth0_36_" + patient + "_" + now + ".xml");
                    // JDBCUtil validates the final path under the configured archive directory.
                    JDBCUtil.saveAsXML(forms.readArchive(patient, id), file.toString());
                } catch (Exception _) {
                    MiscUtils.getLogger().error("Unable to archive committed Growth form XML; continuing without XML copy");
                }
            }
        });
    }
}
