/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * Originally written for the Department of Family Medicine, McMaster University.
 * Now maintained by the CARLOS EMR Project.
 *
 * <p>
 * Migrated from legacy JUnit 4 PreventionGuideLineTest to JUnit 5 for the CARLOS EMR project (2026).
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
 * Unit tests for {@link DSPreventionDrools} guideline XML loading and compilation.
 *
 * <p>Verifies that XML-based prevention guideline definitions can be successfully
 * parsed and compiled into a Drools KieBase.
 * Migrated from legacy JUnit 4 PreventionGuideLineTest.
 *
 * @since 2026-03-07
 */
@Tag("unit")
@Tag("drools")
@DisplayName("PreventionGuideLine unit tests")
class PreventionGuideLineUnitTest {

    @Test
    @DisplayName("should compile guideline XML into KieBase")
    void shouldCompileGuidelineXml_intoKieBase() throws Exception {
        try (InputStream is = getClass().getResourceAsStream(
                "/io/github/carlos_emr/carlos/decisionSupport/prevention/guidelineExample.xml")) {
            assertThat(is).as("guidelineExample.xml should be on classpath").isNotNull();
            byte[] ruleSet = IOUtils.toByteArray(is);
            KieBase kieBase = DSPreventionDrools.createRuleBase(ruleSet);
            assertThat(kieBase).isNotNull();
            assertThat(kieBase.getKiePackages()).isNotEmpty();
            int totalRules = kieBase.getKiePackages().stream()
                    .mapToInt(p -> p.getRules().size())
                    .sum();
            assertThat(totalRules).isGreaterThan(0);
        }
    }
}
