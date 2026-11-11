/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.test.logging.LogCapture;

@Tag("integration")
@Tag("lab")
class FactoryIntegrationTest extends CarlosTestBase {
    @Autowired
    private Hl7TextMessageDao dao;

    @Test
    void shouldHandleAbsentRowWithoutParsing_whenDatabaseLookupReturnsNull() {
        assertThat(dao.find(Integer.MAX_VALUE)).isNull();
        try (LogCapture log = LogCapture.forLogger(Factory.class)) {
            assertThat(Factory.getHandler(String.valueOf(Integer.MAX_VALUE))).isNull();
            assertThat(log.events()).allMatch(event -> event.getThrown() == null);
            assertThat(log.messages()).noneMatch(message -> message.contains("Could not create message handler"));
        }
    }

    @Test
    void shouldLoadSyntheticResultsThroughDaoAndParser_whenMessageIsStored() {
        Hl7TextMessage message = new Hl7TextMessage();
        message.setType("PATHL7");
        message.setBase64EncodedeMessage(Base64.getEncoder().encodeToString(
                PathL7EmbeddedDocumentMessage.message().getBytes(StandardCharsets.UTF_8)));
        dao.persist(message);
        hibernateTemplate.flush();

        MessageHandler handler = Factory.getHandler(message.getId().toString());
        assertThat(handler).isInstanceOf(PATHL7Handler.class);
        assertThat(handler.getOBRCount()).isEqualTo(2);
        assertThat(handler.getOBXResult(0, 0)).isEqualTo("5.2");
    }
}
