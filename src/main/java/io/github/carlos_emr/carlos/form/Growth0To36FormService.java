/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.forms.Growth0To36FormDao;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.form.model.Growth0To36Form;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.JDBCUtil;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;
import java.nio.file.Path;
import java.util.Date;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;
import org.owasp.encoder.Encode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

/** Patient-scoped Growth form load/save boundary reached through Spring's transactional proxy. */
@Service
public class Growth0To36FormService {
    private final Growth0To36FormDao forms;
    private final DemographicDao demographics;
    private final SecurityInfoManager security;

    /** Uses the existing demographic DAO and form/patient privilege checks. */
    public Growth0To36FormService(Growth0To36FormDao forms, DemographicDao demographics, SecurityInfoManager security) {
        this.forms = forms;
        this.demographics = demographics;
        this.security = security;
    }

    /** Loads a patient's version or the demographic defaults of a new form. */
    @Transactional(readOnly = true)
    public Properties load(LoggedInInfo actor, int demographicNo, int id) {
        requireAccess(actor, demographicNo, "r");
        Properties values;
        if (id > 0) {
            Growth0To36Form form = forms.findForPatient(demographicNo, id);
            if (form == null) throw new SecurityException("missing required sec object (_form)");
            values = Growth0To36FormFields.toProperties(form);
        } else {
            values = new Properties();
        }
        Demographic patient = demographics.getDemographic(Integer.toString(demographicNo));
        if (patient != null) {
            if (id <= 0) {
                values.setProperty("demographic_no", Integer.toString(demographicNo));
                String today = UtilDateUtilities.DateToString(new Date(), Growth0To36FormFields.DATE_FORMAT);
                values.setProperty("formCreated", today);
                values.setProperty("formEdited", today);
                values.setProperty("patientName", StringUtils.trimToEmpty(patient.getFirstName()) + " "
                        + StringUtils.trimToEmpty(patient.getLastName()));
            }
            Date birth = UtilDateUtilities.calcDate(patient.getYearOfBirth(), patient.getMonthOfBirth(), patient.getDateOfBirth());
            values.setProperty("patientSex", StringUtils.trimToEmpty(patient.getSex()));
            values.setProperty("dateOfBirth", UtilDateUtilities.DateToString(birth, Growth0To36FormFields.DATE_FORMAT));
        }
        return values;
    }

    /**
     * Inserts a new version, ignoring submitted provider and ID values. The proxy commits before
     * the action receives the identifier, logs success, or navigates. XML remains a best-effort
     * copy and is written only after commit, never for a rolled-back insert.
     */
    @Transactional(rollbackFor = Exception.class)
    public int saveVersion(LoggedInInfo actor, Properties values) {
        int patient = Integer.parseInt(values.getProperty("demographic_no"));
        requireAccess(actor, patient, "w");
        Growth0To36Form form = Growth0To36FormFields.fromProperties(values, patient,
                Integer.parseInt(actor.getLoggedInProviderNo()));
        forms.persist(form);
        forms.flush();
        // Refresh applies the database timestamp precision before preparing the XML snapshot.
        forms.refresh(form);
        if (Boolean.parseBoolean(CarlosProperties.getInstance().getProperty("save_as_xml", "false"))) {
            Map<String, String> columns = Growth0To36FormFields.columns(form);
            String fileName = "formGrowth0_36_" + patient + "_"
                    + UtilDateUtilities.DateToString(new Date(), "yyyyMMddHHmmss") + ".xml";
            String directory = CarlosProperties.getInstance().getProperty("form_record_path", "/root");
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { archive(columns, directory, fileName); }
            });
        }
        return form.getId();
    }

    private void requireAccess(LoggedInInfo actor, int patient, String right) {
        if (!security.hasPrivilege(actor, "_form", right, patient)) {
            throw new SecurityException("missing required sec object (_form)");
        }
        if (!security.hasPrivilege(actor, "_demographic", right, patient)) {
            throw new SecurityException("missing required sec object (_demographic)");
        }
    }

    private static void archive(Map<String, String> columns, String directory, String fileName) {
        try {
            Path file = Path.of(directory).resolve(fileName);
            Document document = XmlUtils.createSecureDocumentBuilderFactory().newDocumentBuilder().newDocument();
            Element results = document.createElement("Results");
            document.appendChild(results);
            Element row = document.createElement("Row");
            results.appendChild(row);
            columns.forEach((name, value) -> {
                Element column = document.createElement(name);
                // Keep JDBCUtil.toDocument's existing archive encoding for legacy imports.
                column.appendChild(document.createTextNode(Encode.forXml(StringUtils.trimToEmpty(value))));
                row.appendChild(column);
            });
            JDBCUtil.saveAsXML(document, file.toString());
        } catch (Exception failure) {
            MiscUtils.getLogger().error("Unable to archive committed Growth form XML; continuing without XML copy");
        }
    }
}
