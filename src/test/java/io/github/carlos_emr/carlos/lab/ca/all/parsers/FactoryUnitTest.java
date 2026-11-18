/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
@Tag("lab")
class FactoryUnitTest extends CarlosUnitTestBase {
    private Hl7TextMessageDao dao;

    @BeforeEach
    void setUpDao() {
        dao = createAndRegisterMock(Hl7TextMessageDao.class);
    }

    @Test
    void shouldReturnNoHandlerWithoutParserErrors_whenMessageIsMissing() {
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler("42")).isNull();
            assertThat(log.messages()).anyMatch(message -> message.contains("HL7 message was not found"));
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
            assertThat(log.messages()).noneMatch(message -> message.contains("Could not create message handler"));
        }
    }

    @Test
    void shouldReturnNoHandlerWithoutThrowing_whenMessageObjectIsNull() {
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler((Hl7TextMessage) null)).isNull();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldReturnNoHandlerWithoutParserErrors_whenStoredBodyIsMissing(String body) {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(body);
        when(dao.find(42)).thenReturn(message);
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler("42")).isNull();
            assertThat(Factory.getHandler(message)).isNull();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
            assertThat(log.messages()).noneMatch(text -> text.contains("Could not create message handler"));
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " \r\n\t")
    void shouldReturnNoHandlerWithoutParserErrors_whenRawBodyIsMissing(String body) {
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler("PATHL7", body)).isNull();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @Test
    void shouldNotParseAnEmptyFallback_whenLookupFails() {
        when(dao.find(42)).thenThrow(new IllegalStateException("Synthetic database failure"));
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler("42")).isNull();
            assertThat(log.events()).hasSize(1);
            assertThat(log.events().getFirst().getThrown()).isInstanceOf(IllegalStateException.class);
        }
    }

    @Test
    void shouldReturnNoBodyWithoutNullPointer_whenMessageIsMissing() {
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHL7Body("42")).isNull();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
        }
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = "UNREGISTERED-SYNTHETIC-TYPE")
    void shouldKeepGenericFallback_whenMessageHasNoRegisteredType(String type) {
        MessageHandler handler = Factory.getHandler(type, PathL7EmbeddedDocumentMessage.message());
        assertThat(handler).isInstanceOf(DefaultGenericHandler.class);
        assertThat(handler.getOBRCount()).isPositive();
    }

    @Test
    void shouldDecodeStoredSyntheticMessage_whenRecordExists() {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(
                PathL7EmbeddedDocumentMessage.message().getBytes(StandardCharsets.UTF_8)));
        when(dao.find(42)).thenReturn(message);
        assertThat(Factory.getHandler("42")).isInstanceOf(PATHL7Handler.class);
        assertThat(Factory.getHandler(message).getOBRCount()).isEqualTo(2);
    }
}
