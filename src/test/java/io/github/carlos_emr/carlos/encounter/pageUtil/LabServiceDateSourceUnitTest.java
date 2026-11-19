/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.encounter.pageUtil;

import io.github.carlos_emr.carlos.lab.ca.on.LabResultData;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
class LabServiceDateSourceUnitTest extends io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"CML", "MDS", "BCP", "DOC", "HRM"})
    void shouldNeverLookUpAnotherSource_whenNumericReportIdsOverlap(String type) {
        LabResultData lab = new LabResultData(type);
        lab.labType = type;
        lab.segmentID = "42";
        try (var factory = mockStatic(Factory.class)) {
            assertThat(new EctDisplayLabAction2.ServiceDateLoader(lab).determineResultDate(null)).isNull();
            assertThat(new EctDisplayLabAction22Action.ServiceDateLoader(lab).determineResultDate(null)).isNull();
            factory.verifyNoInteractions();
        }
    }

    @Test
    void shouldStillResolveHl7ServiceDate_whenSourceIsHl7() {
        LabResultData lab = new LabResultData("HL7");
        lab.segmentID = "42";
        MessageHandler handler = mock(MessageHandler.class);
        when(handler.getServiceDate()).thenReturn("2026-09-01");
        try (var factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler("42")).thenReturn(handler);
            assertThat(new EctDisplayLabAction2.ServiceDateLoader(lab).determineResultDate(null)).isNotNull();
            assertThat(new EctDisplayLabAction22Action.ServiceDateLoader(lab).determineResultDate(null)).isNotNull();
            factory.verify(() -> Factory.getHandler("42"), times(2));
        }
    }
}
