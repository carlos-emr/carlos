/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.decisionSupport.prevention;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;

import org.apache.commons.io.IOUtils;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.kie.api.KieBase;

/**
 * Unit tests for {@link DSPreventionDrools}.
 *
 * <p>Tests the XML-to-Drools rule base pipeline by loading a sample
 * guideline XML file containing prevention recommendations (DTaP-IPV,
 * Rotavirus, Flu, Varicella, PAP screening) and verifying the resulting
 * {@link KieBase} is correctly created.</p>
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("drools")
@DisplayName("PreventionGuideLine")
class PreventionGuideLineUnitTest {

    @Test
    @DisplayName("should create KieBase from guideline XML")
    void shouldCreateKieBase_fromGuidelineXml() throws Exception {
        byte[] ruleSet = loadGuidelineXml();

        KieBase kieBase = DSPreventionDrools.createRuleBase(ruleSet);

        assertThat(kieBase).isNotNull();
    }

    @Test
    @DisplayName("should produce rule base with non-empty packages")
    void shouldProduceRuleBase_withNonEmptyPackages() throws Exception {
        byte[] ruleSet = loadGuidelineXml();

        KieBase kieBase = DSPreventionDrools.createRuleBase(ruleSet);

        assertThat(kieBase).isNotNull();
        assertThat(kieBase.getKiePackages()).isNotEmpty();
    }

    private byte[] loadGuidelineXml() throws Exception {
        InputStream is = PreventionGuideLineUnitTest.class.getResourceAsStream(
                "/io/github/carlos_emr/carlos/decisionSupport/prevention/guidelineExample.xml");
        assertThat(is).as("guidelineExample.xml resource must be on classpath").isNotNull();
        return IOUtils.toByteArray(is);
    }
}
