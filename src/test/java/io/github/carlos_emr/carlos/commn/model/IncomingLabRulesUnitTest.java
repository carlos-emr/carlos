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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the lab-type to forwarding-category mapping every routing path shares (issue #4120).
 *
 * @since 2026-10-01
 */
@Tag("unit")
@Tag("inbox")
class IncomingLabRulesUnitTest {

    private static IncomingLabRules rule(String... types) {
        IncomingLabRules rule = new IncomingLabRules();
        for (String type : types) rule.addForwardType(type);
        return rule;
    }

    @ParameterizedTest
    @ValueSource(strings = {"HL7", "CML", "MDS", "BCP", "DOC", "HRM"})
    @DisplayName("should apply to every lab type when the rule has no type rows")
    void shouldApplyToEveryLabType_whenRuleHasNoTypeRows(String labType) {
        assertThat(rule().appliesToLabType(labType)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {"HL7", "CML", "MDS", "BCP"})
    @DisplayName("should treat every lab source as HL7")
    void shouldTreatLabSourcesAsHl7_forHl7ScopedRule(String labType) {
        assertThat(rule("HL7").appliesToLabType(labType)).isTrue();
        assertThat(rule("DOC", "HRM").appliesToLabType(labType)).isFalse();
    }

    @Test
    @DisplayName("should apply to documents only when the rule includes DOC")
    void shouldApplyToDocuments_onlyWhenRuleIncludesDoc() {
        assertThat(rule("DOC").appliesToLabType("DOC")).isTrue();
        assertThat(rule("HL7", "HRM").appliesToLabType("DOC")).isFalse();
    }

    @Test
    @DisplayName("should apply to HRM reports only when the rule includes HRM")
    void shouldApplyToHrm_onlyWhenRuleIncludesHrm() {
        assertThat(rule("HRM").appliesToLabType("HRM")).isTrue();
        assertThat(rule("HL7", "DOC").appliesToLabType("HRM")).isFalse();
    }
}
