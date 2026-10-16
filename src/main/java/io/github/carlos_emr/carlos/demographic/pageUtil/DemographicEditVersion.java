/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicCust;

/**
 * The version of a patient record that a Master Record edit form was rendered from (issue #4225).
 *
 * <p>The edit form posts every field, and {@link DemographicUpdate2Action} used to write all of
 * them back, so a form opened before another user's save silently put that user's changes back
 * (a cleared phone number), or, for a patient whose extension rows did not exist yet, inserted
 * them a second time and failed. The form now carries {@link #of this fingerprint} of the
 * patient row, its {@code demographicExt} rows and its {@code demographiccust} row as they were
 * rendered; the update compares it with the record under a row lock and refuses a stale form
 * instead of overwriting the newer record.</p>
 *
 * <p>The fingerprint is a SHA-256 over values, not a timestamp: {@code lastUpdateDate} is stored
 * to the second, so two saves within one second could not be told apart by it alone.</p>
 *
 * @since 2026-10-15
 */
public final class DemographicEditVersion {

    /** The request parameter, and hidden edit-form field, that carries the version. */
    public static final String PARAMETER = "demographicVersion";

    private static final String EDIT = "demographic.demographiceditdemographic.";
    private static final String ADD = "demographic.demographicaddrecordhtm.";

    /** A field the edit form writes, labelled by its bundle key; {@code value} reads it off the row. */
    private record Field(String labelKey, Function<Demographic, Object> value) { }

    /** The patient-row fields the edit form writes, in the order the conflict view lists them. */
    private static final List<Field> FIELDS = List.of(
            new Field(EDIT + "formLastName", Demographic::getLastName),
            new Field(EDIT + "formFirstName", Demographic::getFirstName),
            new Field(EDIT + "formMiddleNames", Demographic::getMiddleNames),
            // The form shows Name Used from the alias column; the save writes it to alias and prefName.
            new Field(ADD + "formNameUsed", Demographic::getAlias),
            new Field(EDIT + "msgDemoTitle", Demographic::getTitle),
            new Field(EDIT + "formSex", Demographic::getSex),
            new Field(ADD + "formGender", Demographic::getGender),
            new Field(ADD + "formPronouns", Demographic::getPronoun),
            new Field(EDIT + "formDOB", d -> String.join("-", text(d.getYearOfBirth()), text(d.getMonthOfBirth()),
                    text(d.getDateOfBirth()))),
            new Field(EDIT + "formAddr", Demographic::getAddress),
            new Field(EDIT + "formCity", Demographic::getCity),
            new Field(EDIT + "formProcvince", Demographic::getProvince),
            new Field(EDIT + "formPostal", Demographic::getPostal),
            new Field(EDIT + "formResidentialAddr", Demographic::getResidentialAddress),
            new Field(EDIT + "formResidentialCity", Demographic::getResidentialCity),
            new Field(EDIT + "formResidentialProvince", Demographic::getResidentialProvince),
            new Field(EDIT + "formResidentialPostal", Demographic::getResidentialPostal),
            new Field(EDIT + "formPhoneH", Demographic::getPhone),
            new Field(EDIT + "formPhoneW", Demographic::getPhone2),
            new Field(EDIT + "formEmail", Demographic::getEmail),
            new Field(EDIT + "consentToUseEmailForCare", Demographic::getConsentToUseEmailForCare),
            new Field(EDIT + "msgDemoLanguage", Demographic::getOfficialLanguage),
            new Field(EDIT + "msgSpoken", Demographic::getSpokenLanguage),
            new Field(EDIT + "msgCountryOfOrigin", Demographic::getCountryOfOrigin),
            new Field(EDIT + "formNewsLetter", Demographic::getNewsletter),
            new Field(EDIT + "formHin", Demographic::getHin),
            new Field(EDIT + "formVer", Demographic::getVer),
            new Field(EDIT + "formHCType", Demographic::getHcType),
            new Field(EDIT + "formEFFDate", Demographic::getEffDate),
            new Field(EDIT + "formHCRenewDate", Demographic::getHcRenewDate),
            new Field("demographic.editConflict.sin", Demographic::getSin),
            new Field(EDIT + "formChartNo", Demographic::getChartNo),
            new Field(EDIT + "formMRP", Demographic::getProviderNo),
            new Field(EDIT + "formRefDoc", Demographic::getFamilyDoctor),
            new Field(EDIT + "formRosterStatus", Demographic::getRosterStatus),
            new Field(EDIT + "DateJoined", Demographic::getRosterDate),
            new Field(EDIT + "RosterEnrolledTo", Demographic::getRosterEnrolledTo),
            new Field(EDIT + "RosterTerminationDate", Demographic::getRosterTerminationDate),
            new Field(EDIT + "RosterTerminationReason", Demographic::getRosterTerminationReason),
            new Field(EDIT + "formPatientStatus", Demographic::getPatientStatus),
            new Field(EDIT + "PatientStatusDate", Demographic::getPatientStatusDate),
            new Field(EDIT + "formDateJoined1", Demographic::getDateJoined),
            new Field(EDIT + "formEndDate", Demographic::getEndDate),
            new Field(EDIT + "formPCN", Demographic::getPcnIndicator));

    /**
     * Row values that are part of the version but not listed on their own: who saved last, and
     * when, and the preferred name. Any save through the form changes the first two, so even a
     * save that changed no listed field makes an older form stale. The form overwrites pref_name
     * with its Name Used entry but shows only the alias; {@code getPrefName()} returns the alias
     * when one is set and the pref_name column otherwise, so a change to the column behind a blank
     * alias also makes the form stale, without a second Name Used row in the conflict view.
     */
    private static final List<Function<Demographic, Object>> AUDIT = List.of(
            Demographic::getDemographicNo, Demographic::getLastUpdateUser, Demographic::getLastUpdateDate,
            Demographic::getPrefName);

    /** The extension keys every edit form writes, in the order {@link DemographicUpdate2Action} saves them. */
    private static final List<String> BASE_EXTENSION_KEYS = List.of(
            "demo_cell", "aboriginal", "hPhoneExt", "wPhoneExt", "cytolNum", "ethnicity", "area");

    /** Written when {@code FIRST_NATIONS_MODULE=true}. */
    private static final List<String> FIRST_NATIONS_KEYS = List.of("statusNum", "fNationCom", "labelfNationCom");

    /** Written when {@code FIRST_NATIONS_MODULE=true} and {@code showBandNumberOnly=false}. */
    private static final List<String> FIRST_NATIONS_FAMILY_KEYS = List.of("fNationFamilyPosition", "fNationFamilyNumber");

    private static final List<String> MORE_EXTENSION_KEYS = List.of(
            "given_consent", "rxInteractionWarningLevel", "primaryEMR", "phoneComment", "usSigned", "privacyConsent",
            "informedConsent", "paper_chart_archived", "paper_chart_archived_date", "paper_chart_archived_program",
            "HasPrimaryCarePhysician", "EmploymentStatus", "PHU");

    /** Labels of the extension keys the edit form writes; another key is listed by its name. */
    private static final Map<String, String> EXTENSION_LABELS = Map.of(
            "demo_cell", EDIT + "formPhoneC",
            "phoneComment", ADD + "formPhoneComment",
            "cytolNum", EDIT + "cytolNum",
            "rxInteractionWarningLevel", EDIT + "rxInteractionWarningLevel",
            "PHU", EDIT + "formPHU");

    /** The {@code demographiccust} fields the edit form writes, keyed by their label. */
    private static final Map<String, Function<DemographicCust, String>> CUSTOM = customFields();

    private static Map<String, Function<DemographicCust, String>> customFields() {
        Map<String, Function<DemographicCust, String>> fields = new LinkedHashMap<>();
        fields.put(EDIT + "formAlert", DemographicCust::getAlert);
        fields.put(EDIT + "formNotes", cust -> notesText(cust.getNotes()));
        fields.put(EDIT + "formNurse", DemographicCust::getNurse);
        fields.put(EDIT + "formResident", DemographicCust::getResident);
        fields.put(EDIT + "formMidwife", DemographicCust::getMidwife);
        return fields;
    }

    /**
     * One field whose value in the refused form differs from the record as it is now.
     *
     * @param labelKey the bundle key of the field's label, or null when {@code name} is shown
     * @param name the extension key, for an extension field with no label of its own
     * @param yours the value the refused form carried
     * @param current the value the record holds now
     */
    public record Difference(String labelKey, String name, String yours, String current) { }

    private DemographicEditVersion() { }

    /**
     * The {@code demographicExt} keys the Master Record edit form writes, in save order: the
     * standard keys, the First Nations keys when that module is on, and the clinic's own keys
     * from the {@code demographicExt} property ({@code |}-separated, spaces read as
     * underscores). Only these rows are part of the version, so a write of another extension
     * key elsewhere (a dashboard exclusion, for example) does not make an open form stale.
     *
     * @return the keys, without duplicates
     */
    public static List<String> editedExtensionKeys() {
        Properties properties = CarlosProperties.getInstance();
        LinkedHashSet<String> keys = new LinkedHashSet<>(BASE_EXTENSION_KEYS);
        if ("true".equals(properties.getProperty("FIRST_NATIONS_MODULE", "false"))) {
            keys.addAll(FIRST_NATIONS_KEYS);
            if ("false".equals(properties.getProperty("showBandNumberOnly", "true"))) {
                keys.addAll(FIRST_NATIONS_FAMILY_KEYS);
            }
        }
        keys.addAll(MORE_EXTENSION_KEYS);
        String custom = properties.getProperty("demographicExt");
        if (custom != null) {
            for (String key : custom.split("\\|")) {
                String normalized = key.replace(' ', '_');
                if (!normalized.isBlank()) {
                    keys.add(normalized);
                }
            }
        }
        return List.copyOf(keys);
    }

    /**
     * The version of a patient record.
     *
     * @param demographic the patient row
     * @param extensions its extension rows as {@code DemographicExtDao.getAllValuesForDemo} returns
     *        them (each key's value, and each key's row id under {@code key + "_id"}); may be null.
     *        Only the {@linkplain #editedExtensionKeys() keys the form writes} count.
     * @param cust its custom row; may be null
     * @return a lowercase hexadecimal SHA-256 fingerprint
     */
    public static String of(Demographic demographic, Map<String, String> extensions, DemographicCust cust) {
        Objects.requireNonNull(demographic, "demographic");
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            for (Function<Demographic, Object> value : AUDIT) {
                update(digest, value.apply(demographic));
            }
            for (Field field : FIELDS) {
                update(digest, field.value().apply(demographic));
            }
            Map<String, String> rows = extensions == null ? Map.of() : extensions;
            for (String key : editedExtensionKeys()) {
                update(digest, key);
                update(digest, rows.get(key));
                update(digest, rows.get(key + "_id"));
            }
            update(digest, cust == null ? null : "cust");
            if (cust != null) {
                for (Function<DemographicCust, String> value : CUSTOM.values()) {
                    update(digest, value.apply(cust));
                }
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Required SHA-256 digest is unavailable", e);
        }
    }

    /**
     * Whether a posted version names a version at all. A form without one (an older page, or a
     * request built without the edit form) is saved without the stale-form check.
     *
     * @param postedVersion the submitted {@link #PARAMETER}
     * @return whether it is a well-formed version
     */
    public static boolean isPresent(String postedVersion) {
        return postedVersion != null && postedVersion.matches("[0-9a-f]{64}");
    }

    /**
     * The fields whose value in a refused form differs from the record as it is now, so the
     * conflict page can show the user both their entries and the newer values before they
     * decide how to continue.
     *
     * @param yours the patient row as the refused form would have saved it
     * @param yourExtensions the extension values the refused form carried, by key
     * @param yourCust the custom row as the refused form would have saved it
     * @param current the patient row now
     * @param currentExtensions the extension rows now, as {@code getAllValuesForDemo} returns them
     * @param currentCust the custom row now; may be null
     * @return the differing fields, in form order
     */
    public static List<Difference> differences(Demographic yours, Map<String, String> yourExtensions,
                                               DemographicCust yourCust, Demographic current,
                                               Map<String, String> currentExtensions, DemographicCust currentCust) {
        List<Difference> differences = new ArrayList<>();
        for (Field field : FIELDS) {
            add(differences, field.labelKey(), null, field.value().apply(yours), field.value().apply(current));
        }
        Map<String, String> now = currentExtensions == null ? Map.of() : currentExtensions;
        for (Map.Entry<String, String> extension : yourExtensions.entrySet()) {
            String label = EXTENSION_LABELS.get(extension.getKey());
            add(differences, label, label == null ? extension.getKey() : null, extension.getValue(), now.get(extension.getKey()));
        }
        for (Map.Entry<String, Function<DemographicCust, String>> field : CUSTOM.entrySet()) {
            add(differences, field.getKey(), null, yourCust == null ? null : field.getValue().apply(yourCust),
                    currentCust == null ? null : field.getValue().apply(currentCust));
        }
        return differences;
    }

    private static void add(List<Difference> differences, String labelKey, String name, Object yours, Object current) {
        String yourText = display(yours);
        String currentText = display(current);
        if (!yourText.equals(currentText)) {
            differences.add(new Difference(labelKey, name, yourText, currentText));
        }
    }

    private static String display(Object value) {
        if (value instanceof Date date) {
            return new SimpleDateFormat("yyyy-MM-dd").format(date);
        }
        String text = value == null ? "" : value.toString().trim();
        return "null".equals(text) ? "" : text;
    }

    private static String text(String value) {
        return value == null ? "" : value;
    }

    /** The clinician-entered text of a {@code <unotes>}-wrapped custom notes value. */
    private static String notesText(String notes) {
        if (notes == null) {
            return null;
        }
        return notes.replace("<unotes>", "").replace("</unotes>", "");
    }

    private static void update(MessageDigest digest, Object value) {
        if (value == null) {
            digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(-1).array());
            return;
        }
        String text = value instanceof Date date ? Long.toString(date.getTime()) : value.toString();
        byte[] bytes = text.getBytes(StandardCharsets.UTF_8);
        digest.update(ByteBuffer.allocate(Integer.BYTES).putInt(bytes.length).array());
        digest.update(bytes);
    }
}
