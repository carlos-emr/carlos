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
 * Migrated from legacy JUnit 4 CLSResultsComplianceTest to JUnit 5 for the CARLOS EMR project (2026).
 */
package io.github.carlos_emr.carlos.labs.alberta;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.lab.ca.all.parsers.CLSHandler;
import io.github.carlos_emr.carlos.util.ConversionUtils;

/**
 * Alberta CLS HL7 handler result element compliance tests.
 *
 * <p>Verifies correct extraction of OBR/OBX segment data including test names,
 * results, units, reference ranges, abnormal flags, result statuses, comments,
 * and date-time values from CLS HL7 messages.
 * Migrated from legacy JUnit 4 CLSResultsComplianceTest.
 *
 * @since 2014-06-20 (original)
 */
@Tag("unit")
@Tag("lab")
@DisplayName("CLS results compliance unit tests")
class CLSResultsComplianceUnitTest {

    private static final String SAMPLE_LAB_WITH_ORC_SEGMENTS =
            "MSH|^~\\&|LCS|LCA|LIS|TEST9999|199807311532||ORU^R01|3629|P|2.3\r" +
            "PID|2|2161348462|20809880170|1614614|20809880170^TESTPAT||19760924|M|||^^^^00000-0000|||||||86427531^^^03|SSN# HERE\r" +
            "ORC|NW|8642753100012^LIS|20809880170^LCS||||||19980727000000|||HAVILAND\r" +
            "OBR|1|8642753100012^LIS|20809880170^LCS|008342^UPPER RESPIRATORY CULTURE^L|||19980727175800||||||SS#634748641 CH14885 SRC:THROA SRC:PENI|19980727000000||||||20809880170||19980730041800||BN|F\r" +
            "OBX|1|ST|008342^UPPER RESPIRATORY CULTURE^L||FINALREPORT|||||N|F||| 19980729160500|BN\r" +
            "ORC|NW|8642753100012^LIS|20809880170^LCS||||||19980727000000|||HAVILAND\r" +
            "OBR|2|8642753100012^LIS|20809880170^LCS|997602^.^L|||19980727175800||||G|||19980727000000||||||20809880170||19980730041800|||F|997602|||008342\r" +
            "OBX|2|CE|997231^RESULT 1^L||M415|||||N|F|||19980729160500|BN\r" +
            "NTE|1|L|MORAXELLA (BRANHAMELLA) CATARRHALIS\r" +
            "NTE|2|L| HEAVY GROWTH\r" +
            "NTE|3|L| BETA LACTAMASE POSITIVE\r" +
            "OBX|3|CE|997232^RESULT 2^L||MR105|||||N|F|||19980729160500|BN\r" +
            "NTE|1|L|ROUTINE RESPIRATORY FLORA";

    @Test
    @DisplayName("should parse result elements correctly for LAB01")
    void shouldParseResultElements_forLab01() throws Exception {
        CLSHandler handler = new CLSHandler();
        handler.init(TestLabs.LAB01);

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBXCount(0)).isEqualTo(5);
        assertThat(handler.getOBRName(0)).isEqualTo("URINE MICROSCOPIC");

        // Test Names
        assertThat(handler.getOBXName(0, 0)).isEqualTo("WBC");
        assertThat(handler.getOBXName(0, 1)).isEqualTo("RBC");
        assertThat(handler.getOBXName(0, 2)).isEqualTo("EPITHELIAL CELLS");
        assertThat(handler.getOBXName(0, 3)).isEqualTo("URINE BACTERIA");
        assertThat(handler.getOBXName(0, 4)).isEqualTo("HYALINE CASTS");

        // Test Results
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("6-10");
        assertThat(handler.getOBXResult(0, 1)).isEqualTo("6-10");
        assertThat(handler.getOBXResult(0, 2)).isEqualTo("Few");
        assertThat(handler.getOBXResult(0, 3)).isEqualTo("Few");
        assertThat(handler.getOBXResult(0, 4)).isEqualTo("0-4");

        // Units
        assertThat(handler.getOBXUnits(0, 0)).isEqualTo("/HPF");
        assertThat(handler.getOBXUnits(0, 1)).isEqualTo("/HPF");
        assertThat(handler.getOBXUnits(0, 2)).isEqualTo("/HPF");
        assertThat(handler.getOBXUnits(0, 3)).isEqualTo("/HPF");
        assertThat(handler.getOBXUnits(0, 4)).isEqualTo("/LPF");

        // Reference Ranges
        assertThat(handler.getOBXReferenceRange(0, 0)).isEqualTo("0-5");
        assertThat(handler.getOBXReferenceRange(0, 1)).isEqualTo("0-5");
        assertThat(handler.getOBXReferenceRange(0, 2)).isEmpty();
        assertThat(handler.getOBXReferenceRange(0, 3)).isEmpty();
        assertThat(handler.getOBXReferenceRange(0, 4)).isEmpty();

        // Abnormal Flags
        assertThat(handler.getOBXAbnormalFlag(0, 0)).isEqualTo("A");
        assertThat(handler.getOBXAbnormalFlag(0, 1)).isEqualTo("A");
        assertThat(handler.getOBXAbnormalFlag(0, 2)).isEmpty();
        assertThat(handler.getOBXAbnormalFlag(0, 3)).isEmpty();
        assertThat(handler.getOBXAbnormalFlag(0, 4)).isEmpty();

        // Result Statuses
        assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo("Final");
        assertThat(handler.getOBXResultStatus(0, 1)).isEqualTo("Final");
        assertThat(handler.getOBXResultStatus(0, 2)).isEqualTo("Final");
        assertThat(handler.getOBXResultStatus(0, 3)).isEqualTo("Final");
        assertThat(handler.getOBXResultStatus(0, 4)).isEqualTo("Final");

        // Comment Counts
        assertThat(handler.getOBXCommentCount(0, 0)).isZero();
        assertThat(handler.getOBXCommentCount(0, 1)).isZero();
        assertThat(handler.getOBXCommentCount(0, 2)).isZero();
        assertThat(handler.getOBXCommentCount(0, 3)).isZero();
        assertThat(handler.getOBXCommentCount(0, 4)).isZero();

        // Order Comments
        assertThat(handler.getOBRCommentCount(0)).isZero();

        // Collection DateTime
        assertThat(handler.getOBRDateTime(0)).isEqualTo("20101203122200");
        assertThat(handler.getOBRDateTimeAsDate(0))
                .isEqualTo(ConversionUtils.fromDateString("20101203122200", "yyyyMMddHHmmss"));
    }

    @Test
    @DisplayName("should parse result elements with comments for LAB02")
    void shouldParseResultElements_forLab02() throws Exception {
        CLSHandler handler = new CLSHandler();
        handler.init(TestLabs.LAB02);

        assertThat(handler.getOBRCount()).isEqualTo(1);
        assertThat(handler.getOBXCount(0)).isEqualTo(1);
        assertThat(handler.getOBRName(0)).isEqualTo("URINALYSIS");

        assertThat(handler.getOBXName(0, 0)).isEqualTo("Urinalysis Comment");
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("See Note");
        assertThat(handler.getOBXUnits(0, 0)).isEmpty();
        assertThat(handler.getOBXReferenceRange(0, 0)).isEmpty();
        assertThat(handler.getOBXAbnormalFlag(0, 0)).isEmpty();
        assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo("Final");

        // Result Comments
        assertThat(handler.getOBXCommentCount(0, 0)).isEqualTo(2);
        assertThat(handler.getOBXComment(0, 0, 0))
                .isEqualTo("Microscopic examination not performed due to negative Blood, Nitrite,");
        assertThat(handler.getOBXComment(0, 0, 1))
                .isEqualTo("Leukocyte Esterase and Protein.");

        assertThat(handler.getOBRCommentCount(0)).isZero();

        assertThat(handler.getOBRDateTime(0)).isEqualTo("20101203122500");
        assertThat(handler.getOBRDateTimeAsDate(0))
                .isEqualTo(ConversionUtils.fromDateString("20101203122500", "yyyyMMddHHmmss"));
    }

    @Test
    @DisplayName("should parse filler order ID from ORC segments")
    void shouldParseFillerOrderId_fromOrcSegments() throws Exception {
        CLSHandler handler = new CLSHandler();
        handler.init(SAMPLE_LAB_WITH_ORC_SEGMENTS);
        assertThat(handler.getFillerOrderNumber()).isEqualTo("20809880170");
    }

    @Test
    @DisplayName("should parse accession numbers for care set compliance")
    void shouldParseAccessionNumbers_forCareSetCompliance() throws Exception {
        CLSHandler handler = new CLSHandler();

        // Occult Blood care set
        handler.init(TestLabs.LAB29);
        assertThat(handler.getAccessionNum()).isEqualTo("10-344-300006");

        handler.init(TestLabs.LAB30);
        assertThat(handler.getAccessionNum()).isEqualTo("10-344-300006");

        handler.init(TestLabs.LAB31);
        assertThat(handler.getAccessionNum()).isEqualTo("10-344-300006");

        // Glucose Tolerance care set
        handler.init(TestLabs.LAB55);
        assertThat(handler.getAccessionNum()).isEqualTo("11-012-300002");

        handler.init(TestLabs.LAB57);
        assertThat(handler.getAccessionNum()).isEqualTo("11-012-300004");
    }

    @Test
    @DisplayName("should parse headers from lab results")
    void shouldParseHeaders_fromLabResults() throws Exception {
        CLSHandler handler = new CLSHandler();
        handler.init(TestLabs.LAB01);
        assertThat(handler.getHeaders()).contains("URINE MICROSCOPIC");
    }
}
