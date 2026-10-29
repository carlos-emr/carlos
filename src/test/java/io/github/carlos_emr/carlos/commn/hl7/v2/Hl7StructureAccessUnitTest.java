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
package io.github.carlos_emr.carlos.commn.hl7.v2;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v23.message.ORU_R01;

/**
 * Tests {@link Hl7StructureAccess}, which lets lab handlers treat HAPI 2.x's unchecked
 * structure-access failure as the {@link HL7Exception} HAPI 1.0.1 threw, and nothing else
 * (issue #5414).
 */
@Tag("unit")
@Tag("fast")
@Tag("hl7")
@DisplayName("Hl7StructureAccess")
class Hl7StructureAccessUnitTest {

    private static final String ONE_ORDER = "MSH|^~\\&|LAB|FAC|||20261029120000||ORU^R01|C1|P|2.3\r"
            + "PID|1||9999999901||FAKE^PATIENT\r"
            + "OBR|1||ACC1|TEST^Test\r"
            + "OBX|1|NM|CODE^Name||5|u|0-1|N|||F\r";

    @Test
    @DisplayName("should accept the failure when HAPI cannot reach a requested repetition")
    void shouldAcceptFailure_whenHapiCannotReachRequestedRepetition() throws Exception {
        ORU_R01 message = (ORU_R01) Hl7Parsers.newPipeParser().parse(ONE_ORDER);

        RuntimeException failure = null;
        try {
            message.getRESPONSE().getORDER_OBSERVATION(5);
        } catch (RuntimeException e) {
            failure = e;
        }

        assertThat(failure).as("HAPI 2.x refuses the missing repetition").isNotNull();
        RuntimeException hapiFailure = failure;
        assertThatCode(() -> Hl7StructureAccess.rethrowUnlessMissingStructure(hapiFailure)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("should rethrow the failure when it is not HAPI's structure access")
    void shouldRethrowFailure_whenItIsNotHapisStructureAccess() {
        NullPointerException bug = new NullPointerException("a programming error");
        RuntimeException unrelated = new IllegalStateException("unexpected state", new IllegalArgumentException());
        RuntimeException noCause = new RuntimeException("no cause");

        assertThatThrownBy(() -> Hl7StructureAccess.rethrowUnlessMissingStructure(bug)).isSameAs(bug);
        assertThatThrownBy(() -> Hl7StructureAccess.rethrowUnlessMissingStructure(unrelated)).isSameAs(unrelated);
        assertThatThrownBy(() -> Hl7StructureAccess.rethrowUnlessMissingStructure(noCause)).isSameAs(noCause);
    }
}
