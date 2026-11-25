/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.webserv.rest.to;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobResponse.ScheduleActivation;
import jakarta.xml.bind.JAXBContext;
import java.io.StringWriter;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * Pins the wire shape of {@link OscarJobResponse} added with the optional
 * {@code scheduleActivation} property in #6239 (#6336): the JSON field is written only when a
 * schedule outcome was set, and the XML form of the class (XmlAccessType.NONE) is marshalled
 * through the real JAXB runtime.
 */
@Tag("unit")
@Tag("rest")
class OscarJobResponseUnitTest {
    private final ObjectMapper json = new ObjectMapper();

    @ParameterizedTest
    @EnumSource(ScheduleActivation.class)
    void shouldWriteScheduleActivationToJson_whenItIsSet(ScheduleActivation outcome) {
        OscarJobResponse response = new OscarJobResponse();
        response.setScheduleActivation(outcome);

        JsonNode tree = json.valueToTree(response);

        assertThat(tree.get("scheduleActivation").asText()).isEqualTo(outcome.name());
        assertThat(tree.get("jobs").isArray()).isTrue();
    }

    @Test
    void shouldOmitScheduleActivationFromJson_whenNoScheduleWasAttempted() {
        JsonNode tree = json.valueToTree(new OscarJobResponse());

        assertThat(tree.has("scheduleActivation")).isFalse();
        assertThat(tree.get("jobs").isArray()).isTrue();
    }

    @Test
    void shouldMarshalXmlWithoutScheduleActivation_whenItIsNull() throws Exception {
        assertThat(marshal(new OscarJobResponse())).contains("oscarJobResponse")
                .doesNotContain("scheduleActivation");
    }

    /**
     * Characterises today's XML output (#6336 finding): the property has no {@code @XmlElement}
     * under {@code XmlAccessType.NONE}, so JAXB never emits it even when set, unlike JSON.
     * If the class is annotated so XML carries the outcome, update this expectation.
     */
    @Test
    void shouldNotYetMarshalScheduleActivationToXml_whenItIsSet() throws Exception {
        OscarJobResponse response = new OscarJobResponse();
        response.setScheduleActivation(ScheduleActivation.ACTIVE);

        assertThat(marshal(response)).contains("oscarJobResponse").doesNotContain("scheduleActivation");
    }

    private static String marshal(OscarJobResponse response) throws Exception {
        StringWriter out = new StringWriter();
        JAXBContext.newInstance(OscarJobResponse.class).createMarshaller().marshal(response, out);
        return out.toString();
    }
}
