/**
 * Copyright (c) 2021 WELL EMR Group Inc.
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "gnu.org/licenses/gpl-2.0.html".
 */
package io.github.carlos_emr.carlos.commn.model.enumerator;

import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <code>DemographicExtKey</code> contains key name constants for demographicExt table
 */
public enum DemographicExtKey {
    ABORIGINAL("aboriginal"),
    ALERT_ALLERGY("alertForAllergy", "allergyalert"),
    ALERT_BOOKING("bookingAlert"),
    AREA("AREA"),
    CONSENT_GIVEN("given_consent"),
    CONSULT_INCLUDE_EMAIL("includeEmailOnConsults", CustomParameter.CHECKBOX, "false"),
    CONSENT_INFORMED("informedConsent"),
    CONSENT_PRIVACY("privacyConsent"),
    CONSENT_PATIENT_EMAIL("patientEmailConsent"),
    CONSENT_PATIENT_MERGE("patientMergeConsent"),
    CYTOLOGY_NUMBER("cytolNum"),
    DEMOGRAPHIC_MISC_ID("demographicMiscId"),
    DOCTOR_ROSTER("familyDoctorId", "r_doctor_id"),
    DOCTOR_FAMILY("familyPhysicianId", "f_doctor_id"),
    EMPLOYMENT_STATUS("EmploymentStatus"),
    ENABLE_MAILING("enableMailing"),
    ENROLLMENT_PROVIDER("enrollmentProvider"),
    ETHNICITY("ethnicity"),
    FIRST_NATION_COMMUNITY("fNationCom"),
    FIRST_NATION_FAMILY_NUMBER("fNationFamilyNumber"),
    FIRST_NATION_FAMILY_POSITION("fNationFamilyPosition"),
    FORMER_NAME("former_name"),
    INSURANCE_COMPANY("insurance_company"),
    INSURANCE_NUMBER("insurance_number"),
    MAILING_ADDRESS("address_mailing"),
    MAILING_CITY("city_mailing"),
    MAILING_POSTAL("postal_mailing"),
    MAILING_PROVINCE("province_mailing"),
    MIDWIFE("midwife"),
    NOT_MRP("notMrp"),
    NOTES("notes", true, "unotes"),
    NURSE("nurse"),
    PAPER_CHART_ARCHIVED("paper_chart_archived"),
    PAPER_CHART_ARCHIVED_DATE("paper_chart_archived_date"),
    PAPER_CHART_ARCHIVED_PROGRAM("paper_chart_archived_program"),
    PATIENT_PORTAL_LAST_REQUEST_DATE_TIME("patient_portal_last_request_date_time"),
    PATIENT_PORTAL_REGISTERED_DATE_TIME("patient_portal_registered_date_time"),
    PATIENT_PORTAL_REGISTRATION_REQUEST_ID("patient_portal_registration_id"),
    PATIENT_PORTAL_REQUESTER_NAME("patient_portal_requester_name"),
    PATIENT_PORTAL_TERMINATION_DATE_TIME("patient_portal_termination_date_time"),
    PATIENT_PORTAL_TERMINATOR_NAME("patient_portal_terminator_name"),
    PATIENT_PORTAL_TERMINATION_REASON("patient_portal_termination_reason"),
    PATIENT_TYPE("patientType"),
    PHONE_CELL("demo_cell"),
    PHONE_COMMENT("phoneComment"),
    PHONE_EXT_HOME("hPhoneExt"),
    PHONE_EXT_WORK("wPhoneExt"),
    PHONE_PREFERENCE("phone_preference"),
    PHU("PHU"),
    PRESCRIBE_IT_OPT_OUT("opt_out_prescribeit"),
    PRIMARY_EMR("primaryEMR"),
    PRIMARY_CARE_PHYSICIAN_EXISTS("HasPrimaryCarePhysician"),
    REFERRAL_DATE("referralDate", "referral-date"),
    REFERRAL_SOURCE("referral_source", "referralSource"),
    REFERRAL_SOURCE_CUSTOM("referral_source", "referralSourceCustom"),
    REMINDER_ALLOW_REMINDERS("allow_appointment_reminders"),
    REMINDER_CELL("reminder_cell", CustomParameter.ON, "true"),
    REMINDER_EMAIL("reminder_email", CustomParameter.ON, "true"),
    REMINDER_PHONE("reminder_phone", CustomParameter.ON, "true"),
    REMINDER_PREFERENCE_ID("reminder_preference"),
    RESIDENT("resident"),
    RX_INTERACTION_WARNING_LEVEL("rxInteractionWarningLevel"),
    STATUS_NUMBER("statusNum"),
    US_SIGNED("usSigned");

    private final String key;
    private String requestParameter;
    private String defaultValue;
    private CustomParameter customParameter = CustomParameter.NONE;
    private boolean xml = false;
    private String xmlTag = null;

    DemographicExtKey(String key, String requestParameter) {
        this.key = key;
        this.requestParameter = requestParameter;
    }

    DemographicExtKey(String key, String requestParameter, String defaultValue) {
        this.key = key;
        this.requestParameter = requestParameter;
        this.defaultValue = defaultValue;
    }

    DemographicExtKey(
            String key,
            CustomParameter customParameter,
            String defaultValue
    ) {
        this.key = key;
        this.customParameter = customParameter;
        this.defaultValue = defaultValue;
    }

    DemographicExtKey(
            String key,
            boolean hasXml,
            String xmlTag
    ) {
        this.key = key;
        this.xml = hasXml;
        this.xmlTag = xmlTag;
    }

    DemographicExtKey(String key) {
        this.key = key;
    }

    public boolean hasXml() {
        return xml;
    }

    public String getKey() {
        return this.key;
    }


    private String getDefaultValue() {
        return this.defaultValue;
    }

    @NotNull
    public static Map<String, DemographicExtKey> demographicExtKeyMap() {
        Map map = new HashMap<String, DemographicExtKey>();
        for (DemographicExtKey key : DemographicExtKey.values()) {
            map.put(key.getKey(), key);
        }
        return map;
    }

    @NotNull
    public static List<DemographicExtKey> keysWithDefaultValues() {
        List keys = new ArrayList<DemographicExtKey>();
        for (DemographicExtKey key : DemographicExtKey.values()) {
            if (key.getDefaultValue() != null) {
                keys.add(key);
            }
        }
        return keys;
    }


    public enum CustomParameter {
        CHECKBOX("true"),
        NONE(null),
        ON("on");

        final String condition;

        CustomParameter(String condition) {
            this.condition = condition;
        }
    }
}
