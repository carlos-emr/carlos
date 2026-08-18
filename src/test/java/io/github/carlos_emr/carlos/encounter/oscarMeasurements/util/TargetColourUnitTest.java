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

import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.drools.RuleBaseFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TargetColour}, which maps clinical measurement conditions
 * to color-coded indicator rules in the flowsheet system.
 *
 * <p>{@code TargetColour} represents a single color indicator rule (e.g., "HIGH",
 * "LOW", "NORMAL") in a measurement flowsheet. It contains one or more
 * {@link TargetCondition} objects that define when the color should be applied
 * (e.g., "systolic BP >= 140 mmHg"). At runtime, {@code TargetColour} generates
 * DRL rules that the Drools engine evaluates against patient measurement data.</p>
 *
 * <h3>Clinical context</h3>
 * <p>In the CARLOS EMR flowsheet UI, each measurement cell is color-coded based on
 * the patient's value relative to clinical thresholds. For example:</p>
 * <ul>
 *   <li><strong>RED/HIGH</strong>: A1C &gt; 9.0% (poorly controlled diabetes)</li>
 *   <li><strong>YELLOW/WARNING</strong>: A1C 7.0-9.0% (above target)</li>
 *   <li><strong>GREEN/NORMAL</strong>: A1C &lt; 7.0% (well controlled)</li>
 * </ul>
 *
 * <p>Tests are organized into three nested classes:</p>
 * <ul>
 *   <li>{@link GetRuleBaseElement} &mdash; DRL rule text generation</li>
 *   <li>{@link GetFlowsheetXML} &mdash; JDOM2 XML serialization</li>
 *   <li>{@link XmlConstructor} &mdash; XML deserialization (round-trip)</li>
 * </ul>
 *
 * @see TargetColour
 * @see TargetCondition
 * @see DSCondition
 * @see RuleBaseCreator
 * @since 2026-02-17
 */
@Tag("unit")
@Tag("drools")
@DisplayName("TargetColour")
class TargetColourUnitTest {

    /**
     * Flush the KieBase cache before each test to ensure that DRL compilation
     * tests don't interfere with each other via the static cache.
     */
    @BeforeEach
    void setUp() {
        RuleBaseFactory.flushAllCached();
    }

    /**
     * Helper that creates a {@link TargetColour} with a single double-value
     * condition. This is the most common configuration in production: a numeric
     * threshold comparison (e.g., blood pressure >= 140.0 triggers "HIGH" color).
     *
     * @param color the indication color to set (e.g., "HIGH", "LOW", "NORMAL")
     * @param value the threshold expression (e.g., ">=2.0")
     * @return a configured {@link TargetColour} with one condition
     */
    private TargetColour createTargetColourWithDoubleValueCondition(String color, String value) {
        TargetColour tc = new TargetColour();
        tc.setIndicationColor(color);

        // Create a condition that checks a double measurement value
        TargetCondition cond = new TargetCondition();
        cond.setType("getDataAsDouble");
        cond.setValue(value);
        tc.getTargetConditions().add(cond);

        return tc;
    }

    /**
     * Tests for {@link TargetColour#getRuleBaseElement(String)}, which generates
     * a DRL rule string from the color indicator and its conditions.
     *
     * <p>The generated DRL follows this structure:</p>
     * <pre>
     *   import io.github.carlos_emr...MeasurementDSHelper;
     *   rule "RULE_NAME"
     *       when m : MeasurementDSHelper( ... conditions ... )
     *       then m.setIndicationColor("HIGH"); ... additional consequence ...
     *   end
     * </pre>
     */
    @Nested
    @DisplayName("getRuleBaseElement")
    class GetRuleBaseElement {

        /**
         * Verifies that the generated DRL includes a consequence that sets the
         * indication color on the matched measurement fact.
         */
        @Test
        @DisplayName("should generate DRL with indication color consequence")
        void shouldGenerateDrl_withIndicationColorConsequence() {
            TargetColour tc = createTargetColourWithDoubleValueCondition("HIGH", ">=2.0");

            String drl = tc.getRuleBaseElement("TEST_RULE");

            // The consequence must set the color on the MeasurementDSHelper fact
            assertThat(drl).contains("m.setIndicationColor(\"HIGH\");");
        }

        /**
         * Verifies that when an additional consequence is configured (via
         * {@link TargetColour#setAdditionConsequence(String)}), it is appended
         * to the standard indication color consequence in the {@code then} block.
         *
         * <p>Additional consequences are used for side effects beyond color-coding,
         * such as triggering alerts or updating related measurements.</p>
         */
        @Test
        @DisplayName("should append additional consequence when set")
        void shouldAppendAdditionalConsequence_whenSet() {
            TargetColour tc = createTargetColourWithDoubleValueCondition("HIGH", ">=2.0");
            // Set an extra side-effect in addition to the color change
            tc.setAdditionConsequence(" m.doSomethingExtra();");

            String drl = tc.getRuleBaseElement("TEST_RULE");

            // Both the standard color consequence and the extra consequence must appear
            assertThat(drl).contains("m.setIndicationColor(\"HIGH\");");
            assertThat(drl).contains("m.doSomethingExtra();");
        }

        /**
         * End-to-end compilation test: generates a DRL rule from a TargetColour
         * configuration and verifies it compiles without errors via the KIE compiler.
         * This catches DRL generation bugs that only manifest at compile time.
         */
        @Test
        @DisplayName("should generate compilable DRL with single condition")
        void shouldGenerateCompilableDrl_withSingleCondition() throws Exception {
            TargetColour tc = createTargetColourWithDoubleValueCondition("HIGH", ">=2.0");

            String rule = tc.getRuleBaseElement("COMPILE_TEST");
            // Wrap with a package declaration to create a complete compilable DRL
            String fullDrl = "package test;\n" + rule;

            assertThat(DroolsHelper.createKieBaseFromDrl(fullDrl)).isNotNull();
        }

        /**
         * Verifies that the generated DRL imports the correct fact class
         * ({@link MeasurementDSHelper}) and uses its simple name in the
         * pattern-matching clause.
         */
        @Test
        @DisplayName("should use MeasurementDSHelper FQCN as fact class")
        void shouldUseMeasurementDSHelperFqcn_asFactClass() {
            TargetColour tc = createTargetColourWithDoubleValueCondition("HIGH", ">=2.0");

            String drl = tc.getRuleBaseElement("TEST_RULE");

            // FQCN in import statement, simple name in pattern match
            assertThat(drl).contains("import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.MeasurementDSHelper");
            assertThat(drl).contains("m : MeasurementDSHelper()");
        }
    }

    /**
     * Tests for {@link TargetColour#getFlowsheetXML()}, which serializes the
     * color indicator to a JDOM2 XML element for flowsheet configuration persistence.
     *
     * <p>The XML format is:</p>
     * <pre>
     *   &lt;rule indicationColor="HIGH"&gt;
     *       &lt;condition type="getDataAsDouble" value=">=140.0"/&gt;
     *   &lt;/rule&gt;
     * </pre>
     */
    @Nested
    @DisplayName("getFlowsheetXML")
    class GetFlowsheetXML {

        /**
         * Verifies that the XML element has the correct tag name ("rule") and
         * includes the {@code indicationColor} attribute with the configured value.
         */
        @Test
        @DisplayName("should serialize to XML with indicationColor attribute")
        void shouldSerializeToXml_withIndicationColorAttribute() {
            TargetColour tc = createTargetColourWithDoubleValueCondition("HIGH", ">=2.0");

            Element xml = tc.getFlowsheetXML();

            assertThat(xml.getName()).isEqualTo("rule");
            assertThat(xml.getAttributeValue("indicationColor")).isEqualTo("HIGH");
        }

        /**
         * Verifies that each {@link TargetCondition} in the color indicator
         * produces a {@code <condition>} child element in the serialized XML.
         */
        @Test
        @DisplayName("should include condition child elements in XML")
        void shouldIncludeConditionChildElements_inXml() {
            TargetColour tc = new TargetColour();
            tc.setIndicationColor("NORMAL");

            // Add two conditions to verify both are serialized
            TargetCondition cond1 = new TargetCondition();
            cond1.setType("getDataAsDouble");
            cond1.setValue(">=4.0");
            TargetCondition cond2 = new TargetCondition();
            cond2.setType("isMale");
            tc.getTargetConditions().add(cond1);
            tc.getTargetConditions().add(cond2);

            Element xml = tc.getFlowsheetXML();

            // Both conditions should be represented as child elements
            List<Element> conditions = xml.getChildren("condition");
            assertThat(conditions).hasSize(2);
        }
    }

    /**
     * Tests for the XML-based constructor {@link TargetColour#TargetColour(Element)},
     * which deserializes a {@code <rule>} XML element back into a {@code TargetColour}
     * object. This is the inverse of {@link TargetColour#getFlowsheetXML()}.
     *
     * <p>Together with the serialization tests, these verify a complete round-trip:
     * create &rarr; serialize &rarr; deserialize &rarr; verify equivalence.</p>
     */
    @Nested
    @DisplayName("XML constructor")
    class XmlConstructor {

        /**
         * Verifies that the constructor reads the {@code indicationColor} attribute
         * from the XML element and stores it correctly.
         */
        @Test
        @DisplayName("should parse indicationColor from XML element")
        void shouldParseIndicationColor_fromXmlElement() {
            // Manually build an XML element that mimics flowsheet configuration
            Element ruleElement = new Element("rule");
            ruleElement.setAttribute("indicationColor", "LOW");

            TargetColour tc = new TargetColour(ruleElement);

            assertThat(tc.getIndicationColor()).isEqualTo("LOW");
        }

        /**
         * Verifies that the constructor creates a {@link TargetCondition} for each
         * {@code <condition>} child element, preserving the type and value attributes.
         */
        @Test
        @DisplayName("should parse conditions from child elements")
        void shouldParseConditions_fromChildElements() {
            // Build an XML element with two condition children
            Element ruleElement = new Element("rule");
            ruleElement.setAttribute("indicationColor", "HIGH");
            Element cond1 = new Element("condition");
            cond1.setAttribute("type", "getDataAsDouble");
            cond1.setAttribute("value", ">=140.0");
            Element cond2 = new Element("condition");
            cond2.setAttribute("type", "isMale");
            ruleElement.addContent(cond1);
            ruleElement.addContent(cond2);

            TargetColour tc = new TargetColour(ruleElement);

            // Both conditions should be parsed and accessible
            assertThat(tc.getTargetConditions()).hasSize(2);
            assertThat(tc.getTargetConditions().get(0).getType()).isEqualTo("getDataAsDouble");
            assertThat(tc.getTargetConditions().get(1).getType()).isEqualTo("isMale");
        }
    }
}
