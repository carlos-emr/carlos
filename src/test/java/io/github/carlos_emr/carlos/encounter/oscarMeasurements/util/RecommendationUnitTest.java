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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import java.util.List;

import org.jdom2.Element;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.drools.RuleBaseFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link Recommendation}, which generates clinical measurement
 * recommendation rules for the flowsheet system.
 *
 * <p>{@code Recommendation} represents a clinical guideline alert that fires when
 * a patient's measurement data meets certain conditions. Unlike {@link TargetColour}
 * (which colors the current measurement cell), recommendations provide actionable
 * guidance to clinicians, such as "A1C measurement is overdue by 6 months" or
 * "Blood pressure consistently elevated &mdash; consider medication review".</p>
 *
 * <h3>Strength levels</h3>
 * <p>Each recommendation has a {@code strength} that determines how it is rendered
 * in the UI and what consequence code is generated in the DRL:</p>
 * <ul>
 *   <li><strong>hidden</strong>: Hides the measurement from the flowsheet
 *       (generates {@code m.addHidden("BP", true)})</li>
 *   <li><strong>warning</strong>: Shows a prominent warning alert
 *       (generates {@code m.addWarning("BP", "...")})</li>
 *   <li><strong>recommendation</strong> (default): Shows a standard guideline reminder
 *       (generates {@code m.addRecommendation("BP", "...")})</li>
 * </ul>
 *
 * <h3>Fact class</h3>
 * <p>Recommendations use {@code MeasurementInfo} as the Drools fact class (not
 * {@code MeasurementDSHelper} used by {@link TargetColour}). This is because
 * recommendations operate on measurement metadata (last recorded date, frequency)
 * rather than raw measurement values.</p>
 *
 * <p>Tests are organized into three nested classes:</p>
 * <ul>
 *   <li>{@link GetRuleBaseElement} &mdash; DRL rule text generation for each strength level</li>
 *   <li>{@link GetFlowsheetXML} &mdash; JDOM2 XML serialization</li>
 *   <li>{@link Constructors} &mdash; XML deserialization and programmatic construction</li>
 * </ul>
 *
 * @see Recommendation
 * @see RecommendationCondition
 * @see io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("Recommendation")
class RecommendationUnitTest {

    /**
     * Flush the KieBase cache before each test to ensure that any compilation
     * side effects from one test don't leak into another.
     */
    @BeforeEach
    void setUp() {
        RuleBaseFactory.flushAllCached();
    }

    /**
     * Tests for {@link Recommendation#getRuleBaseElement(String, String)}, which
     * generates DRL rule text based on the recommendation's strength and conditions.
     *
     * <p>The generated DRL uses {@code MeasurementInfo} as the fact class and
     * includes strength-specific consequence code in the {@code then} block.</p>
     */
    @Nested
    @DisplayName("getRuleBaseElement")
    class GetRuleBaseElement {

        /**
         * Verifies that a "hidden" strength recommendation generates a DRL
         * consequence that hides the measurement from the flowsheet display.
         *
         * <p>Hidden recommendations are used when a measurement is not applicable
         * to the patient (e.g., hiding pregnancy-related measurements for male
         * patients).</p>
         */
        @Test
        @DisplayName("should generate hidden consequence when strength is hidden")
        void shouldGenerateHiddenConsequence_whenStrengthIsHidden() {
            Recommendation rec = new Recommendation();
            rec.setStrength("hidden");

            // Add a month-range condition: measurement overdue by more than 12 months
            RecommendationCondition cond = new RecommendationCondition();
            cond.setType("monthrange");
            cond.setValue(">12");
            rec.getRecommendationCondition().add(cond);

            String drl = rec.getRuleBaseElement("HIDE_RULE", "BP");

            // Hidden strength generates addHidden() call, not addWarning/addRecommendation
            assertThat(drl).contains("m.addHidden(\"BP\", true);");
        }

        /**
         * Verifies that a "warning" strength recommendation generates a DRL
         * consequence that adds a prominent warning alert with the message text.
         *
         * <p>Warnings are used for clinically significant conditions that require
         * immediate attention (e.g., "A1C critically elevated at 12%").</p>
         */
        @Test
        @DisplayName("should generate warning consequence when strength is warning")
        void shouldGenerateWarningConsequence_whenStrengthIsWarning() {
            Recommendation rec = new Recommendation();
            rec.setStrength("warning");
            rec.setText("Overdue measurement");

            // Add a month-range condition: measurement overdue by more than 6 months
            RecommendationCondition cond = new RecommendationCondition();
            cond.setType("monthrange");
            cond.setValue(">6");
            rec.getRecommendationCondition().add(cond);

            String drl = rec.getRuleBaseElement("WARN_RULE", "A1C");

            // Warning strength generates addWarning() with the measurement name and text
            assertThat(drl).contains("m.addWarning(\"A1C\"");
            assertThat(drl).contains("Overdue measurement");
        }

        /**
         * Verifies that the default "recommendation" strength generates a DRL
         * consequence that adds a standard guideline reminder.
         *
         * <p>Recommendations are the most common strength level, used for routine
         * clinical reminders (e.g., "Consider scheduling A1C test").</p>
         */
        @Test
        @DisplayName("should generate recommendation consequence by default")
        void shouldGenerateRecommendationConsequence_byDefault() {
            Recommendation rec = new Recommendation();
            rec.setStrength("recommendation");
            rec.setText("Please check BP");

            RecommendationCondition cond = new RecommendationCondition();
            cond.setType("monthrange");
            cond.setValue(">3");
            rec.getRecommendationCondition().add(cond);

            String drl = rec.getRuleBaseElement("REC_RULE", "BP");

            // Default strength generates addRecommendation() with measurement and text
            assertThat(drl).contains("m.addRecommendation(\"BP\"");
            assertThat(drl).contains("Please check BP");
        }

        /**
         * Verifies that the {@code $NUMMONTHS} placeholder in the message text is
         * replaced with a Drools expression that dynamically calculates the number
         * of months since the last recorded measurement.
         *
         * <p>This allows recommendation messages like "A1C overdue by 8 months"
         * to display the actual overdue period, not a static value.</p>
         */
        @Test
        @DisplayName("should substitute $NUMMONTHS placeholder in message text")
        void shouldSubstituteNumMonthsPlaceholder_inMessageText() {
            Recommendation rec = new Recommendation();
            rec.setStrength("recommendation");
            rec.setText("Overdue by $NUMMONTHS months");

            RecommendationCondition cond = new RecommendationCondition();
            cond.setType("monthrange");
            cond.setValue(">6");
            rec.getRecommendationCondition().add(cond);

            String drl = rec.getRuleBaseElement("REC_RULE", "A1C");

            // The placeholder should be replaced with a dynamic method call expression
            assertThat(drl).contains("m.getLastDateRecordedInMonths(\"A1C\")");
            // The literal "$NUMMONTHS" should no longer appear in the DRL
            assertThat(drl).doesNotContain("$NUMMONTHS");
        }

        /**
         * Verifies that the generated DRL imports the correct fact class
         * ({@code MeasurementInfo}, not {@code MeasurementDSHelper}) and uses
         * its simple name in the pattern-matching clause.
         *
         * <p>Recommendations use {@code MeasurementInfo} because they operate on
         * measurement metadata (dates, frequencies) rather than raw values.</p>
         */
        @Test
        @DisplayName("should use MeasurementInfo FQCN as fact class")
        void shouldUseMeasurementInfoFqcn_asFactClass() {
            Recommendation rec = new Recommendation();
            rec.setStrength("recommendation");
            rec.setText("Check this");

            RecommendationCondition cond = new RecommendationCondition();
            cond.setType("monthrange");
            cond.setValue(">3");
            rec.getRecommendationCondition().add(cond);

            String drl = rec.getRuleBaseElement("TEST_RULE", "BP");

            // FQCN in import, simple name in pattern match
            assertThat(drl).contains("import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo");
            assertThat(drl).contains("m : MeasurementInfo()");
        }
    }

    /**
     * Tests for {@link Recommendation#getFlowsheetXML()}, which serializes the
     * recommendation to a JDOM2 XML element for flowsheet configuration persistence.
     *
     * <p>The XML format is:</p>
     * <pre>
     *   &lt;recommendation strength="warning" message="Overdue A1C"&gt;
     *       &lt;condition type="monthrange" value=">6"/&gt;
     *   &lt;/recommendation&gt;
     * </pre>
     */
    @Nested
    @DisplayName("getFlowsheetXML")
    class GetFlowsheetXML {

        /**
         * Verifies that the XML element has the correct tag name and includes
         * both the {@code strength} and {@code message} attributes.
         */
        @Test
        @DisplayName("should serialize to XML with strength and message attributes")
        void shouldSerializeToXml_withStrengthAndMessageAttributes() {
            Recommendation rec = new Recommendation();
            rec.setStrength("warning");
            rec.setText("Check A1C");

            Element xml = rec.getFlowsheetXML();

            assertThat(xml.getName()).isEqualTo("recommendation");
            assertThat(xml.getAttributeValue("strength")).isEqualTo("warning");
            assertThat(xml.getAttributeValue("message")).isEqualTo("Check A1C");
        }

        /**
         * Verifies that each {@link RecommendationCondition} produces a
         * {@code <condition>} child element in the serialized XML.
         */
        @Test
        @DisplayName("should include condition children in XML")
        void shouldIncludeConditionChildren_inXml() {
            Recommendation rec = new Recommendation();
            rec.setStrength("recommendation");

            // Add two conditions to verify both are serialized
            RecommendationCondition cond1 = new RecommendationCondition();
            cond1.setType("monthrange");
            cond1.setValue("3-6");
            RecommendationCondition cond2 = new RecommendationCondition();
            cond2.setType("isMale");
            rec.getRecommendationCondition().add(cond1);
            rec.getRecommendationCondition().add(cond2);

            Element xml = rec.getFlowsheetXML();

            // Both conditions should appear as child elements
            List<Element> conditions = xml.getChildren("condition");
            assertThat(conditions).hasSize(2);
        }
    }

    /**
     * Tests for the various {@link Recommendation} constructors: XML-based
     * deserialization and programmatic construction with measurement parameters.
     */
    @Nested
    @DisplayName("Constructors")
    class Constructors {

        /**
         * Verifies that the XML constructor reads the {@code strength} and
         * {@code message} attributes and creates {@link RecommendationCondition}
         * objects for each {@code <condition>} child element.
         */
        @Test
        @DisplayName("should parse attributes and conditions from XML element")
        void shouldParseAttributesAndConditions_fromXmlElement() {
            // Manually build an XML element simulating stored flowsheet config
            Element recElement = new Element("recommendation");
            recElement.setAttribute("strength", "warning");
            recElement.setAttribute("message", "Overdue");
            Element cond = new Element("condition");
            cond.setAttribute("type", "monthrange");
            cond.setAttribute("value", ">6");
            recElement.addContent(cond);

            Recommendation rec = new Recommendation(recElement);

            // All attributes and child conditions should be parsed
            assertThat(rec.getStrength()).isEqualTo("warning");
            assertThat(rec.getText()).isEqualTo("Overdue");
            assertThat(rec.getRecommendationCondition()).hasSize(1);
        }

        /**
         * Verifies that the four-argument constructor (measurement, monthValue,
         * strength, text) creates a single "monthrange" condition automatically.
         *
         * <p>This constructor is a convenience for the common case where a
         * recommendation is triggered by time elapsed since the last measurement.</p>
         */
        @Test
        @DisplayName("should create month range condition from programmatic constructor")
        void shouldCreateMonthRangeCondition_fromProgrammaticConstructor() {
            // Create: measurement "A1C", overdue after 12 months, warning, with message
            Recommendation rec = new Recommendation("A1C", "12", "warning", "A1C is overdue");

            assertThat(rec.getStrength()).isEqualTo("warning");
            assertThat(rec.getText()).isEqualTo("A1C is overdue");
            // Should auto-create a monthrange condition with the specified value
            assertThat(rec.getRecommendationCondition()).hasSize(1);
            assertThat(rec.getRecommendationCondition().get(0).getType()).isEqualTo("monthrange");
            assertThat(rec.getRecommendationCondition().get(0).getValue()).isEqualTo("12");
        }

        /**
         * Verifies that the three-argument XML constructor (element, ruleName,
         * measurement) stores the rule name and measurement, which are then used
         * by the no-argument {@link Recommendation#getRuleBaseElement()} method.
         *
         * <p>This constructor is used during flowsheet XML loading when the rule
         * name and measurement context are known from the parent XML structure.</p>
         */
        @Test
        @DisplayName("should store ruleName and measurement from three-arg XML constructor")
        void shouldStoreRuleNameAndMeasurement_fromThreeArgXmlConstructor() {
            Element recElement = new Element("recommendation");
            recElement.setAttribute("strength", "recommendation");
            recElement.setAttribute("message", "Check BP");

            // Construct with explicit rule name and measurement context
            Recommendation rec = new Recommendation(recElement, "BP_REC_1", "BP");

            assertThat(rec.getStrength()).isEqualTo("recommendation");
            assertThat(rec.getText()).isEqualTo("Check BP");

            // The no-arg getRuleBaseElement() should use the stored rule name
            String drl = rec.getRuleBaseElement();
            assertThat(drl).contains("rule \"BP_REC_1\"");
        }
    }
}
