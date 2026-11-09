/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.lab.ca.all.upload.handlers;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.MessageHandler;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class HL7UploadOwnershipUnitTest extends CarlosUnitTestBase {
    private Hl7TextInfoDao info;
    private Hl7TextMessageDao messages;

    @BeforeEach
    void setup() {
        info = mock(Hl7TextInfoDao.class);
        messages = mock(Hl7TextMessageDao.class);
        registerMock(Hl7TextInfoDao.class, info);
        registerMock(Hl7TextMessageDao.class, messages);
    }

    @Test
    void shouldRecheckOnlyOwnedIds_andPreserveAbnormalDetection() throws Exception {
        var lab = new Hl7TextInfo();
        lab.setResultStatus("N");
        var message = new Hl7TextMessage();
        var parser = mock(MessageHandler.class);
        when(info.findByLabId(17)).thenReturn(java.util.List.of(lab));
        when(messages.find(17)).thenReturn(message);
        when(parser.getOBRCount()).thenReturn(1);
        when(parser.getOBXCount(0)).thenReturn(1);
        when(parser.isOBXAbnormal(0, 0)).thenReturn(true);
        try (var factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler(message)).thenReturn(parser);
            HL7Handler.updateLabStatus(Set.of(17));
        }
        verify(info).findByLabId(17);
        verify(info).updateResultStatusLowPriority(17, "A");
        verifyNoMoreInteractions(info);
        verify(messages).find(17);
        verifyNoMoreInteractions(messages);
    }

    @Test
    void shouldPropagateRetrievalFailure_insteadOfSkippingIt() {
        var lab = new Hl7TextInfo();
        lab.setResultStatus("N");
        when(info.findByLabId(17)).thenReturn(java.util.List.of(lab));
        when(messages.find(17)).thenThrow(new jakarta.persistence.PersistenceException("synthetic read failure"));
        assertThatThrownBy(() -> HL7Handler.updateLabStatus(Set.of(17)))
                .isInstanceOf(jakarta.persistence.PersistenceException.class);
        verify(info, never()).updateResultStatusLowPriority(anyInt(), anyString());
    }

    @Test
    void shouldAvoidAllReads_forEmptyOwnedSet() throws Exception {
        HL7Handler.updateLabStatus(Set.of());
        verifyNoInteractions(info, messages);
    }

    @Test
    void shouldAvoidReparse_whenStatusAlreadyAbnormal() throws Exception {
        var lab = new Hl7TextInfo();
        lab.setResultStatus(" A ");
        when(info.findByLabId(17)).thenReturn(java.util.List.of(lab));
        HL7Handler.updateLabStatus(Set.of(17));
        verifyNoInteractions(messages);
        verify(info).findByLabId(17);
        verifyNoMoreInteractions(info);
    }

    @Test
    void shouldRejectMissingContent_withoutReportingASuccessfulRecheck() {
        var lab = new Hl7TextInfo();
        when(info.findByLabId(17)).thenReturn(java.util.List.of(lab));
        assertThatThrownBy(() -> HL7Handler.updateLabStatus(Set.of(17)))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("content is missing");
        verify(info, never()).updateResultStatusLowPriority(anyInt(), anyString());
    }

    @Test
    void shouldRejectUnreadableContent_withoutReportingASuccessfulRecheck() {
        var lab = new Hl7TextInfo();
        var message = new Hl7TextMessage();
        when(info.findByLabId(17)).thenReturn(java.util.List.of(lab));
        when(messages.find(17)).thenReturn(message);
        try (var factory = mockStatic(Factory.class)) {
            factory.when(() -> Factory.getHandler(message)).thenReturn(null);
            assertThatThrownBy(() -> HL7Handler.updateLabStatus(Set.of(17)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("cannot be parsed");
        }
        verify(info, never()).updateResultStatusLowPriority(anyInt(), anyString());
    }

    @Test
    void shouldRejectMissingOrAmbiguousMetadata_beforeReadingContent() {
        when(info.findByLabId(17)).thenReturn(java.util.List.of(),
                java.util.List.of(new Hl7TextInfo(), new Hl7TextInfo()));
        for (int attempt = 0; attempt < 2; attempt++) {
            assertThatThrownBy(() -> HL7Handler.updateLabStatus(Set.of(17)))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("missing or ambiguous");
        }
        verifyNoInteractions(messages);
    }
}
