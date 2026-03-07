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
 *
 * <p>
 * Migrated from legacy JUnit 4 CLSComplianceTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.labs.alberta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler;

/**
 * Alberta CLS HL7 handler compliance tests.
 *
 * <p>Verifies compliance with Alberta healthcare requirements for patient
 * demographic parsing and physician information extraction from CLS HL7 messages.
 * Migrated from legacy JUnit 4 CLSComplianceTest.
 *
 * @since 2014-06-20 (original)
 */
@Tag("unit")
@Tag("lab")
@DisplayName("CLS compliance unit tests")
class CLSComplianceUnitTest {

    private static final String LAB02 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||MillMCK CB FSI||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    private static final String LAB03 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||MillMCK CB FSI, Karla Bruni||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    private static final String LAB04 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||MillMCK CB FSI, Karla Marla Darla||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    private static final String LAB05 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||, Karla||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    private static final String LAB06 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||,||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    private static final String LAB07 = "MSH|^~\\&|OPEN ENGINE|CLS|Egate|POSP|20101203122425||ORU^R01|Q199816389T198313506|P|2.3\r" +
            "PID|1|798274114^^^AB|2250008675^^^88000||||19701027|F||||83||\r" +
            "PV1|1|E|05031^^^88000|||||||||||||||E\r" +
            "OBR|1||0176439413^101LA|4356952^URINALYSIS^L01N|||20101203122200|||||||20101203122200|^^|1001745^Test, Physician - p-Test Physician||||10-337-300046||20101203122421||LA|F||1^^^20101203122100^^RT~^^^^^RT|\r" +
            "OBX|1|ST|4669021^COLOR^L01N||Yellow||||||F|||20101203122419";

    @Test
    @DisplayName("should parse patient name correctly for various HL7 name formats")
    void shouldParsePatientName_forVariousHL7Formats() throws Exception {
        CLSHandler handler = new CLSHandler();

        handler.init(LAB02);
        assertThat(handler.getFirstName()).isEmpty();
        assertThat(handler.getLastName()).isEqualTo("MillMCK CB FSI");
        assertThat(handler.getMiddleName()).isEmpty();

        handler.init(LAB03);
        assertThat(handler.getFirstName()).isEqualTo("Karla");
        assertThat(handler.getLastName()).isEqualTo("MillMCK CB FSI");
        assertThat(handler.getMiddleName()).isEqualTo("Bruni");

        handler.init(LAB04);
        assertThat(handler.getFirstName()).isEqualTo("Karla Marla");
        assertThat(handler.getLastName()).isEqualTo("MillMCK CB FSI");
        assertThat(handler.getMiddleName()).isEqualTo("Darla");

        handler.init(LAB05);
        assertThat(handler.getFirstName()).isEqualTo("Karla");
        assertThat(handler.getLastName()).isEmpty();
        assertThat(handler.getMiddleName()).isEmpty();

        handler.init(LAB06);
        assertThat(handler.getFirstName()).isEmpty();
        assertThat(handler.getLastName()).isEmpty();
        assertThat(handler.getMiddleName()).isEmpty();

        handler.init(LAB07);
        assertThat(handler.getFirstName()).isEmpty();
        assertThat(handler.getLastName()).isEmpty();
        assertThat(handler.getMiddleName()).isEmpty();
    }

    /**
     * REQ-ALBT-01 - Patient Demographic Compliance.
     * Verifies demographic parsing across all 57 test lab messages.
     */
    @Test
    @DisplayName("should parse patient demographics correctly for all test labs (REQ-ALBT-01)")
    void shouldParsePatientDemographics_forAllTestLabs() throws Exception {
        CLSHandler handler = new CLSHandler();

        for (int i = 0; i < TestLabs.ALL_LABS.length; i++) {
            handler.init(TestLabs.ALL_LABS[i]);
            assertThat(handler.getFirstName())
                    .as("First name for lab %d", i + 1)
                    .isEqualTo("Karla");
            assertThat(handler.getLastName())
                    .as("Last name for lab %d", i + 1)
                    .isEqualTo("MillMCK CB FSI");
            assertThat(handler.getMiddleName())
                    .as("Middle name for lab %d", i + 1)
                    .isEmpty();
            assertThat(handler.getHealthNum())
                    .as("Health number for lab %d", i + 1)
                    .isEqualTo("798274114");
            assertThat(handler.getAssigningAuthority())
                    .as("Assigning authority for lab %d", i + 1)
                    .isEqualTo("AB");
            assertThat(handler.getSex())
                    .as("Sex for lab %d", i + 1)
                    .isEqualTo("F");
            assertThat(handler.getDOB())
                    .as("DOB for lab %d", i + 1)
                    .isEqualTo("1970-10-27");
            assertThat(handler.getAge())
                    .as("Age for lab %d", i + 1)
                    .isNotNull();
        }
    }

    /**
     * REQ-ALBT-02 - Physician Information Compliance.
     * Verifies physician info parsing across all 57 test lab messages.
     */
    @Test
    @DisplayName("should parse physician information correctly for all test labs (REQ-ALBT-02)")
    void shouldParsePhysicianInfo_forAllTestLabs() throws Exception {
        CLSHandler handler = new CLSHandler();

        for (int i = 0; i < TestLabs.ALL_LABS.length; i++) {
            handler.init(TestLabs.ALL_LABS[i]);

            String orderingProvider = handler.getOrderingProvider();
            assertThat(orderingProvider)
                    .as("Ordering provider for lab %d", i + 1)
                    .satisfiesAnyOf(
                            p -> assertThat(p).isEqualTo("Test, Physician - p-Test Physician"),
                            p -> assertThat(p).isEqualTo("Unknown1, Physician, MD")
                    );

            String orderingProviderId = handler.getOrderingProviderId();
            assertThat(orderingProviderId)
                    .as("Ordering provider ID for lab %d", i + 1)
                    .satisfiesAnyOf(
                            id -> assertThat(id).isEqualTo("1001745"),
                            id -> assertThat(id).isEqualTo("1000000")
                    );
        }
    }
}
