/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.form.model.FormGrowth0To36;
import io.github.carlos_emr.carlos.form.model.Growth0To36Measurement;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import java.util.Date;
import java.util.Properties;

/** The existing Properties contract at the legacy form/graph boundary. */
final class Growth0To36FormValues {
    static final String DATE_FORMAT = "yyyy/MM/dd";

    private Growth0To36FormValues() { }

    static FormGrowth0To36 fromProperties(Properties values) {
        FormGrowth0To36 form = new FormGrowth0To36();
        // Every save creates a version. Never bind the submitted ID to the new entity.
        form.setFormCreated(date(values.getProperty("formCreated")));
        form.setFormEdited(new Date());
        form.setPatientName(values.getProperty("patientName"));
        form.setRecordNo(values.getProperty("recordNo"));
        form.setMotherStature(values.getProperty("motherStature"));
        form.setGestationalAge(values.getProperty("gestationalAge"));
        form.setFatherStature(values.getProperty("fatherStature"));
        form.setEdc(values.getProperty("edc"));
        for (int slot = 1; slot <= 20; slot++) {
            Growth0To36Measurement measurement = new Growth0To36Measurement();
            measurement.setDate(date(values.getProperty("date_" + slot)));
            measurement.setAge(values.getProperty("age_" + slot));
            measurement.setWeight(values.getProperty("weight_" + slot));
            measurement.setLength(values.getProperty("length_" + slot));
            measurement.setHeadCirc(values.getProperty("headCirc_" + slot));
            measurement.setComment(values.getProperty("comment_" + slot));
            form.setMeasurement(slot, measurement);
        }
        return form;
    }

    static Properties toProperties(FormGrowth0To36 form) {
        Properties values = new Properties();
        put(values, "ID", form.getId());
        put(values, "demographic_no", form.getDemographicNo());
        put(values, "provider_no", form.getProviderNo());
        put(values, "formCreated", formatted(form.getFormCreated()));
        put(values, "formEdited", UtilDateUtilities.DateToString(form.getFormEdited(), "yyyy/MM/dd HH:mm:ss"));
        put(values, "patientName", form.getPatientName());
        put(values, "recordNo", form.getRecordNo());
        put(values, "motherStature", form.getMotherStature());
        put(values, "gestationalAge", form.getGestationalAge());
        put(values, "fatherStature", form.getFatherStature());
        put(values, "edc", form.getEdc());
        for (int slot = 1; slot <= 20; slot++) {
            Growth0To36Measurement measurement = form.getMeasurement(slot);
            put(values, "date_" + slot, formatted(measurement.getDate()));
            put(values, "age_" + slot, measurement.getAge());
            put(values, "weight_" + slot, measurement.getWeight());
            put(values, "length_" + slot, measurement.getLength());
            put(values, "headCirc_" + slot, measurement.getHeadCirc());
            put(values, "comment_" + slot, measurement.getComment());
        }
        return values;
    }

    private static Date date(String value) {
        return UtilDateUtilities.StringToDate(value, value == null || value.contains("/") ? DATE_FORMAT : "yyyy-MM-dd");
    }

    private static String formatted(Date value) {
        return UtilDateUtilities.DateToString(value, DATE_FORMAT);
    }

    private static void put(Properties values, String name, Object value) {
        // Misc.getString previously coalesced nullable text and numbers to empty strings.
        values.setProperty(name, value == null ? "" : value.toString());
    }
}
