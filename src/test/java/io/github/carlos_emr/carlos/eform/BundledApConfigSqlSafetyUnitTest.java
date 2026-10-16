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
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import jakarta.xml.bind.JAXBContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.eform.data.DatabaseAP;
import io.github.carlos_emr.carlos.eform.data.EFormApConfig;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;

/**
 * Every database AP CARLOS ships must pass the gate it is executed through.
 *
 * <p>{@code EFormUtil.getValuesOrNull} validates each AP query with {@link EFormSqlSafety} before it
 * reaches JDBC, and a refused query surfaces as a blank field plus the "could not be filled in"
 * banner. When that validator started refusing every backslash inside a quoted literal, the
 * {@code "\n"} separators in the shipped history, allergy and prescription keys were refused with
 * it, and the Rich Text Letter's side-panel buttons stopped pulling in any of that chart data.
 * Parsing the real {@code apconfig.xml} keeps the next validator change from doing the same.</p>
 *
 * @since 2026-10-16
 */
@Tag("unit")
@Tag("eform")
@DisplayName("Bundled eForm AP SQL safety")
class BundledApConfigSqlSafetyUnitTest extends CarlosUnitTestBase {

    private static final String BUNDLED_AP_CONFIG = "oscar/eform/apconfig.xml";

    @Test
    @DisplayName("should pass every bundled AP query through the eForm SQL safety gate")
    void shouldPassSafetyGate_forEveryBundledApQuery() throws Exception {
        List<DatabaseAP> aps = loadBundledAps();
        // Request-backed placeholders become bind parameters, exactly as EForm.parameterizeAllFields
        // and APExecute do; the remaining ${name} output aliases are folded to literals by
        // DatabaseAP.parserClean.
        Map<String, Object> replacements = Map.of("demographic", "1", "appt_no", "1", "provider", "999998");

        List<String> refused = new ArrayList<>();
        for (DatabaseAP ap : aps) {
            String sql = DatabaseAP.parameterizeSql(ap.getApSQL(), replacements).getSql();
            try {
                EFormSqlSafety.validateLegacySqlSafety(sql);
            } catch (SecurityException e) {
                refused.add(ap.getApName() + " (" + e.getMessage() + ")");
            }
        }

        assertThat(aps).as("bundled AP definitions").hasSizeGreaterThan(100);
        assertThat(refused).as("bundled AP queries refused by EFormSqlSafety").isEmpty();
    }

    @Test
    @DisplayName("should ship every AP key the Rich Text Letter side panel requests")
    void shouldDefineApKeys_forRichTextLetterSidePanel() throws Exception {
        List<String> names = new ArrayList<>();
        for (DatabaseAP ap : loadBundledAps()) {
            names.add(ap.getApName());
        }

        // The buttons in the stored Rich Text Letter's side panel (and the editor's derived
        // mappings) print these keys; an undefined one is reported as a lookup failure.
        assertThat(names).contains("social_family_history", "medical_history", "ongoingconcerns",
                "reminders", "allergies_des", "druglist", "other_medications_history",
                "risk_factors_ext", "family_history", "label", "first_last_name", "current_user",
                "doctor", "referral_name", "referral_address", "referral_phone", "referral_fax");
    }

    private static List<DatabaseAP> loadBundledAps() throws Exception {
        try (InputStream in = BundledApConfigSqlSafetyUnitTest.class.getClassLoader()
                .getResourceAsStream(BUNDLED_AP_CONFIG)) {
            assertThat(in).as(BUNDLED_AP_CONFIG + " on the classpath").isNotNull();
            EFormApConfig config = (EFormApConfig) JAXBContext.newInstance(EFormApConfig.class)
                    .createUnmarshaller()
                    .unmarshal(XmlUtils.createSecureJaxbSource(in));
            return config.getDatabaseAPs();
        }
    }
}
