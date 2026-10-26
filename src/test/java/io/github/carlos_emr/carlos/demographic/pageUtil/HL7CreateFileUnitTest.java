/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.demographic.pageUtil;

import cds.LaboratoryResultsDocument;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@DisplayName("HL7 message creation timestamp")
@Tag("unit")
@Tag("fast")
class HL7CreateFileUnitTest {

    @Test
    @DisplayName("should put message creation time, not specimen time, in MSH-7")
    void shouldUseCurrentMessageCreationTime_forMsh7() throws Exception {
        Clock clock = Clock.fixed(Instant.parse("2026-07-28T14:15:16Z"), ZoneOffset.UTC);
        HL7CreateFile generator = new HL7CreateFile(new Demographic(), clock);
        LaboratoryResultsDocument.LaboratoryResults lab =
                mock(LaboratoryResultsDocument.LaboratoryResults.class);
        when(lab.getLaboratoryName()).thenReturn("CML");
        when(lab.getAccessionNumber()).thenReturn("ACCESSION");

        Method generateMsh = HL7CreateFile.class.getDeclaredMethod(
                "generateMSH", LaboratoryResultsDocument.LaboratoryResults.class);
        generateMsh.setAccessible(true);
        String msh = (String) generateMsh.invoke(generator, lab);

        assertThat(msh.split("\\|", -1)[6]).isEqualTo("20260728141516");
    }
}
