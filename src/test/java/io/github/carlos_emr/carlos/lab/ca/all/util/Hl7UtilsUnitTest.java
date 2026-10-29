/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.v25.message.ORU_R01;
import ca.uhn.hl7v2.parser.PipeParser;

/**
 * HAPI 2.x reports a repetition or field that is not in a message as a {@link RuntimeException}
 * caused by an {@link HL7Exception}; 1.x threw the {@link HL7Exception} itself. These helpers let
 * the lab handlers keep answering such a lookup with an empty value without hiding other
 * failures (issue #5414).
 */
@Tag("unit")
@Tag("lab")
@Tag("hl7")
@DisplayName("Hl7Utils HAPI access-failure helpers")
class Hl7UtilsUnitTest {

    private static final String ORU = "MSH|^~\\&|A|B|C|D|20260315||ORU^R01|X|P|2.5\r"
            + "PID|1||1^^^X||DOE^JOHN\rOBR|1|P|F|CBC^Cbc\rOBX|1|ST|A^a||v||||||F\r";

    @Test
    @DisplayName("should recognise what HAPI itself throws for a repetition the message does not have")
    void shouldRecogniseHapiFailure_forRepetitionOutOfRange() throws Exception {
        ORU_R01 message = (ORU_R01) new PipeParser().parse(ORU);

        Throwable thrown = org.assertj.core.api.Assertions.catchThrowable(
                () -> message.getPATIENT_RESULT().getORDER_OBSERVATION(7));

        assertThat(thrown).isNotNull();
        assertThat(Hl7Utils.isHl7AccessFailure(thrown)).isTrue();
    }

    @Test
    @DisplayName("should treat a checked HL7Exception and one wrapped by HAPI 2.x alike")
    void shouldAcceptBothShapes() {
        HL7Exception checked = new HL7Exception("missing");
        assertThat(Hl7Utils.isHl7AccessFailure(checked)).isTrue();
        assertThat(Hl7Utils.isHl7AccessFailure(new RuntimeException(checked))).isTrue();
    }

    @Test
    @DisplayName("should not treat programming errors as HAPI access failures")
    void shouldRejectOtherFailures() {
        assertThat(Hl7Utils.isHl7AccessFailure(new NullPointerException())).isFalse();
        assertThat(Hl7Utils.isHl7AccessFailure(new IndexOutOfBoundsException("-1"))).isFalse();
        assertThat(Hl7Utils.isHl7AccessFailure(new RuntimeException(new IllegalStateException()))).isFalse();
        assertThat(Hl7Utils.isHl7AccessFailure(new Exception(new HL7Exception("x")))).isFalse();
    }

    @Test
    @DisplayName("should swallow an HAPI access failure and rethrow anything else unchanged")
    void shouldRethrowUnlessHl7AccessFailure() {
        assertThatCode(() -> Hl7Utils.rethrowUnlessHl7AccessFailure(new RuntimeException(new HL7Exception("x"))))
                .doesNotThrowAnyException();

        NullPointerException bug = new NullPointerException("bug");
        assertThatThrownBy(() -> Hl7Utils.rethrowUnlessHl7AccessFailure(bug)).isSameAs(bug);
    }

    @Test
    @DisplayName("should give back the wrapped HL7Exception, or rethrow when there is none")
    void shouldUnwrapHl7AccessFailure() {
        HL7Exception cause = new HL7Exception("missing");
        assertThat(Hl7Utils.unwrapHl7AccessFailure(new RuntimeException(cause))).isSameAs(cause);

        IllegalStateException bug = new IllegalStateException("bug");
        assertThatThrownBy(() -> Hl7Utils.unwrapHl7AccessFailure(bug)).isSameAs(bug);
    }
}
