/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 */
package io.github.carlos_emr.carlos.decisionSupport.prevention;

import java.io.InputStream;

import io.github.carlos_emr.carlos.decisionSupport.prevention.DSPreventionDrools;
import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.Logger;
import org.kie.api.KieBase;

import org.junit.Test;
import io.github.carlos_emr.carlos.utility.MiscUtils;

import static org.junit.Assert.assertNotNull;

/**
 * JUnit 4 test verifying that XML-based prevention guideline definitions can be successfully
 * parsed and compiled into a Drools {@link KieBase} by
 * {@link DSPreventionDrools#createRuleBase(byte[])}.
 *
 * <p>This test validates the XML-to-DRL pipeline used when prevention rules are stored
 * in the {@link io.github.carlos_emr.carlos.commn.model.ResourceStorage} database table.
 * The pipeline works as follows:</p>
 * <ol>
 *   <li>An XML file ({@code guidelineExample.xml}) containing {@code <preventions>}
 *       with {@code <recommendations>} and {@code <condition>} elements is loaded</li>
 *   <li>{@link DSPreventionDrools#createRuleBase(byte[])} parses the XML using JDOM2,
 *       converts the conditions into DRL rule strings via
 *       {@link io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator},
 *       and compiles them into a {@link KieBase} via
 *       {@link io.github.carlos_emr.carlos.drools.DroolsHelper}</li>
 *   <li>The test asserts that the resulting {@link KieBase} is non-null, confirming
 *       successful parsing and compilation</li>
 * </ol>
 *
 * <h3>Test Resource</h3>
 * <p>Uses {@code guidelineExample.xml} located at
 * {@code src/test/resources/io/github/carlos_emr/carlos/decisionSupport/prevention/guidelineExample.xml},
 * which contains sample immunization recommendations for DTaP-IPV, Rot (Rotavirus),
 * Flu, VZ (Varicella), and PAP screening with various condition types including age ranges,
 * prevention counts, date ranges, and boolean eligibility checks.</p>
 *
 * <h3>Migration Context</h3>
 * <p>This test was migrated from the Drools 2.0 API to the KIE API (Drools 7.74.1).
 * The underlying {@code DSPreventionDrools.createRuleBase()} method now produces a
 * {@link KieBase} instead of the legacy {@code RuleBase}.</p>
 *
 * @since 2026-01-06
 * @see DSPreventionDrools#createRuleBase(byte[])
 * @see io.github.carlos_emr.carlos.drools.DroolsHelper
 * @see io.github.carlos_emr.carlos.prevention.Prevention
 * @see org.kie.api.KieBase
 */
public class PreventionGuideLineTest {
    private static final Logger logger = MiscUtils.getLogger();

    /**
     * Loads the sample prevention guideline XML from the test classpath and returns
     * it as a byte array.
     *
     * <p>The XML file ({@code guidelineExample.xml}) contains a {@code <preventions>}
     * root element with multiple {@code <recommendations>} blocks, each defining
     * condition-based immunization rules for a specific prevention type (e.g., DTaP-IPV,
     * Rot, Flu, VZ, PAP). This format mirrors the XML content stored in the
     * {@link io.github.carlos_emr.carlos.commn.model.ResourceStorage} database table
     * for production prevention rules.</p>
     *
     * @return byte[] the raw XML content of guidelineExample.xml
     * @throws Exception if the resource cannot be found on the classpath or cannot be
     *                   read into a byte array
     */
    byte[] getRuleSet() throws Exception {
        InputStream is = this.getClass().getResourceAsStream("/io/github/carlos_emr/carlos/decisionSupport/prevention/guidelineExample.xml");
        byte[] bytes = IOUtils.toByteArray(is);
        return bytes;
    }

    /**
     * Verifies that {@link DSPreventionDrools#createRuleBase(byte[])} can successfully
     * parse the sample guideline XML and compile the resulting DRL rules into a
     * non-null {@link KieBase}.
     *
     * <p>This test exercises the full XML-to-KieBase pipeline:</p>
     * <ol>
     *   <li>Loads {@code guidelineExample.xml} via {@link #getRuleSet()}</li>
     *   <li>Passes the XML bytes to {@link DSPreventionDrools#createRuleBase(byte[])},
     *       which parses the XML, generates DRL rule strings for each recommendation,
     *       and compiles them via the KIE API</li>
     *   <li>Asserts the resulting {@link KieBase} is not null, confirming that the
     *       XML was valid and all generated DRL rules compiled without errors</li>
     * </ol>
     *
     * @throws Exception if XML parsing or DRL compilation fails
     */
    @Test
    public void testLoading() throws Exception {
        KieBase kieBase = DSPreventionDrools.createRuleBase(getRuleSet());
        assertNotNull(kieBase);
    }

}
