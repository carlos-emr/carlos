/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.model.Growth0To36Form;
import io.github.carlos_emr.carlos.form.model.Growth0To36Visit;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import java.sql.Timestamp;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.apache.commons.lang3.StringUtils;

/** Maps the existing JSP and XML field names without accepting SQL or table identifiers. */
final class Growth0To36FormFields {
    static final String DATE_FORMAT = "yyyy/MM/dd";

    private Growth0To36FormFields() { }

    static Growth0To36Form fromProperties(Properties values, int demographicNo, int providerNo) {
        Growth0To36Form form = new Growth0To36Form();
        // ID/formId are source version identifiers. A save always inserts a new version.
        form.setDemographicNo(demographicNo);
        form.setProviderNo(providerNo);
        form.setFormCreated(parseDate(values.getProperty("formCreated")));
        form.setFormEdited(new Date());
        form.setPatientName(values.getProperty("patientName"));
        form.setRecordNo(values.getProperty("recordNo"));
        form.setMotherStature(values.getProperty("motherStature"));
        form.setFatherStature(values.getProperty("fatherStature"));
        form.setGestationalAge(values.getProperty("gestationalAge"));
        form.setEdc(values.getProperty("edc"));
        for (int i = 1; i <= Growth0To36Form.VISIT_COUNT; i++) {
            Growth0To36Visit visit = new Growth0To36Visit();
            visit.setDate(parseDate(values.getProperty("date_" + i)));
            visit.setAge(values.getProperty("age_" + i));
            visit.setWeight(values.getProperty("weight_" + i));
            visit.setLength(values.getProperty("length_" + i));
            visit.setHeadCirc(values.getProperty("headCirc_" + i));
            visit.setComment(values.getProperty("comment_" + i));
            form.setVisit(i, visit);
        }
        return form;
    }

    static Date parseDate(String value) {
        return UtilDateUtilities.StringToDate(value,
                value == null || value.contains("/") ? DATE_FORMAT : "yyyy-MM-dd");
    }

    static Properties toProperties(Growth0To36Form form) {
        Properties values = new Properties();
        columns(form).forEach((key, value) -> values.setProperty(key, StringUtils.trimToEmpty(value)));
        values.setProperty("formCreated", UtilDateUtilities.DateToString(form.getFormCreated(), DATE_FORMAT));
        values.setProperty("formEdited", UtilDateUtilities.DateToString(form.getFormEdited(), "yyyy/MM/dd HH:mm:ss"));
        for (int i = 1; i <= Growth0To36Form.VISIT_COUNT; i++) {
            Growth0To36Visit visit = form.getVisit(i);
            values.setProperty("date_" + i, UtilDateUtilities.DateToString(visit == null ? null : visit.getDate(), DATE_FORMAT));
        }
        return values;
    }

    /** JDBC XML archives used SQL's date/timestamp spelling and trimmed null-to-empty strings. */
    static Map<String, String> columns(Growth0To36Form form) {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("ID", form.getId().toString());
        values.put("demographic_no", form.getDemographicNo().toString());
        values.put("provider_no", form.getProviderNo() == null ? null : form.getProviderNo().toString());
        values.put("formCreated", sqlDate(form.getFormCreated()));
        values.put("formEdited", form.getFormEdited() == null ? null : new Timestamp(form.getFormEdited().getTime()).toString());
        values.put("patientName", form.getPatientName());
        values.put("recordNo", form.getRecordNo());
        values.put("motherStature", form.getMotherStature());
        values.put("gestationalAge", form.getGestationalAge());
        values.put("fatherStature", form.getFatherStature());
        values.put("edc", form.getEdc());
        for (int i = 1; i <= Growth0To36Form.VISIT_COUNT; i++) {
            Growth0To36Visit visit = form.getVisit(i);
            values.put("date_" + i, sqlDate(visit == null ? null : visit.getDate()));
            values.put("age_" + i, visit == null ? null : visit.getAge());
            values.put("weight_" + i, visit == null ? null : visit.getWeight());
            values.put("length_" + i, visit == null ? null : visit.getLength());
            values.put("headCirc_" + i, visit == null ? null : visit.getHeadCirc());
            values.put("comment_" + i, visit == null ? null : visit.getComment());
        }
        return values;
    }

    private static String sqlDate(Date date) {
        return date == null ? null : new java.sql.Date(date.getTime()).toString();
    }
}
