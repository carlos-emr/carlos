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
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults;
import io.github.carlos_emr.carlos.lab.ca.all.util.Utilities;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Verifies upload-owned abnormal rechecks and HL7Handler.parse orchestration and cleanup.
 * @since 2026-11-09
 */
@Tag("unit")
@Isolated("Initializes legacy upload dependency caches")
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

    private void registerUploaderDependencies() {
        for (var field : MessageUploader.class.getDeclaredFields()) {
            if (java.lang.reflect.Modifier.isStatic(field.getModifiers())
                    && !java.lang.reflect.Modifier.isFinal(field.getModifiers())
                    && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager"))) {
                createAndRegisterMock(field.getType());
            }
        }
    }

    @Test
    void shouldPassOnlyReturnedReportIds_toRecheckAcrossMultipleMessages() throws Exception {
        registerUploaderDependencies();
        try (var utilities = mockStatic(Utilities.class); var uploader = mockStatic(MessageUploader.class);
             var recheck = mockStatic(HL7Handler.class, CALLS_REAL_METHODS)) {
            utilities.when(() -> Utilities.separateMessages("synthetic.hl7"))
                    .thenReturn(new java.util.ArrayList<>(java.util.List.of("first", "second", "repeated-id")));
            uploader.when(() -> MessageUploader.routeReport(isNull(), eq(""), eq("HL7"), anyString(), eq(51), any()))
                    .thenAnswer(call -> {
                        RouteReportResults result = call.getArgument(5);
                        result.segmentId = "second".equals(call.getArgument(3)) ? 23 : 17;
                        return "";
                    });
            recheck.when(() -> HL7Handler.updateLabStatus(anySet())).thenAnswer(call -> null);
            assertThat(new HL7Handler().parse(null, "HL7", "synthetic.hl7", 51, "127.0.0.1"))
                    .isEqualTo("success");
            recheck.verify(() -> HL7Handler.updateLabStatus(Set.of(17, 23)));
            uploader.verify(() -> MessageUploader.clean(anyInt()), never());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void shouldFailAndCleanUpload_whenRoutingReturnsNoStoredIdentity(int returnedId) throws Exception {
        registerUploaderDependencies();
        try (var utilities = mockStatic(Utilities.class); var uploader = mockStatic(MessageUploader.class);
             var recheck = mockStatic(HL7Handler.class, CALLS_REAL_METHODS)) {
            utilities.when(() -> Utilities.separateMessages("synthetic.hl7"))
                    .thenReturn(new java.util.ArrayList<>(java.util.List.of("first")));
            uploader.when(() -> MessageUploader.routeReport(isNull(), eq(""), eq("HL7"), anyString(), eq(51), any()))
                    .thenAnswer(call -> {
                        ((RouteReportResults) call.getArgument(5)).segmentId = returnedId;
                        return "";
                    });
            assertThat(new HL7Handler().parse(null, "HL7", "synthetic.hl7", 51, "127.0.0.1")).isNull();
            uploader.verify(() -> MessageUploader.clean(51));
            recheck.verify(() -> HL7Handler.updateLabStatus(anySet()), never());
        }
    }

    @Test
    void shouldCleanEarlierRoutedMessages_whenALaterMessageFails() throws Exception {
        registerUploaderDependencies();
        try (var utilities = mockStatic(Utilities.class); var uploader = mockStatic(MessageUploader.class);
             var recheck = mockStatic(HL7Handler.class, CALLS_REAL_METHODS)) {
            utilities.when(() -> Utilities.separateMessages("synthetic.hl7"))
                    .thenReturn(new java.util.ArrayList<>(java.util.List.of("first", "second")));
            uploader.when(() -> MessageUploader.routeReport(isNull(), eq(""), eq("HL7"), anyString(), eq(51), any()))
                    .thenAnswer(call -> {
                        if ("second".equals(call.getArgument(3))) throw new java.sql.SQLException("synthetic routing failure");
                        ((RouteReportResults) call.getArgument(5)).segmentId = 17;
                        return "";
                    });
            assertThat(new HL7Handler().parse(null, "HL7", "synthetic.hl7", 51, "127.0.0.1")).isNull();
            uploader.verify(() -> MessageUploader.clean(51));
            recheck.verify(() -> HL7Handler.updateLabStatus(anySet()), never());
        }
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
