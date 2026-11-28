/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

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

    private static final Path MESSAGE_CONFIG =
            Path.of("src/main/resources/io/github/carlos_emr/carlos/lab/ca/all/parsers/message_config.xml");

    private static List<Element> configuredHandlers() throws Exception {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        Document document;
        try (InputStream in = Files.newInputStream(MESSAGE_CONFIG)) {
            document = factory.newDocumentBuilder().parse(in);
        }
        NodeList nodes = document.getElementsByTagName("handler");
        List<Element> handlers = new ArrayList<>();
        for (int i = 0; i < nodes.getLength(); i++) {
            handlers.add((Element) nodes.item(i));
        }
        return handlers;
    }

    /**
     * #5479 (#6336 gap test): the PDFDOC and FHIR_COMMUNICATION_REQUEST entries named parser classes that no
     * longer exist. Every remaining display handler must resolve to a class on the classpath, and those that
     * serve the display factory must implement this package's {@link MessageHandler}.
     */
    @Test
    void shouldResolveEveryConfiguredHandler_toAClassOnTheClasspath() throws Exception {
        // PHS names the upload-side PhsStarHandler, which implements upload.handlers.MessageHandler instead;
        // Factory catches the resulting ClassCastException and falls back to DefaultGenericHandler.
        Set<String> uploadInterfaceTypes = Set.of("PHS");
        List<Element> handlers = configuredHandlers();
        assertThat(handlers).isNotEmpty();
        Set<String> names = new HashSet<>();
        for (Element handler : handlers) {
            String name = handler.getAttribute("name");
            String className = handler.getAttribute("className");
            if (className.indexOf('.') == -1) {
                className = "io.github.carlos_emr.carlos.lab.ca.all.parsers." + className;
            }
            names.add(name);
            Class<?> type = Class.forName(className, false, Factory.class.getClassLoader());
            if (!uploadInterfaceTypes.contains(name)) {
                assertThat(MessageHandler.class).as(name).isAssignableFrom(type);
                assertThat(type.getConstructor()).as(name).isNotNull();
            }
            assertThat(className).as(name).doesNotStartWith("io.github.carlos_emr.carlos.lab.ca.all.upload.");
        }
        assertThat(names).doesNotContain("PDFDOC", "FHIR_COMMUNICATION_REQUEST").contains("PATHL7");
    }

    @ParameterizedTest
    @ValueSource(strings = {"PDFDOC", "FHIR_COMMUNICATION_REQUEST"})
    void shouldUseDefaultGenericHandler_forTypesWithoutDisplayParser(String type) {
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            MessageHandler handler = Factory.getHandler(type, PathL7EmbeddedDocumentMessage.message());
            assertThat(handler).isInstanceOf(DefaultGenericHandler.class);
            assertThat(log.messages()).anyMatch(message -> message.contains("No message handler specified for type: " + type));
            assertThat(log.messages()).noneMatch(message -> message.contains("Could not find message handler"));
        }
    }

    @Test
    void shouldKeepPathl7Resolving_toItsDisplayHandler() {
        assertThat(Factory.getHandler("PATHL7", PathL7EmbeddedDocumentMessage.message()))
                .isInstanceOf(PATHL7Handler.class);
    }
}
