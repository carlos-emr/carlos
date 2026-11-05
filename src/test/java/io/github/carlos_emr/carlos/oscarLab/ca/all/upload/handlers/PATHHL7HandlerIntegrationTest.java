/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 *
 * Modifications by CARLOS Contributors, 2026.
 */
package io.github.carlos_emr.carlos.oscarLab.ca.all.upload.handlers;

import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.dao.utils.AuthUtils;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.PATHL7Handler;
import io.github.carlos_emr.carlos.lab.ca.all.upload.MessageUploader;
import io.github.carlos_emr.carlos.lab.ca.all.upload.RouteReportResults;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;

/** Exercises the real parser, JDBC lookup, JPA message storage and inbox routing for every fixture. */
@Tag("integration")
@Tag("lab")
@Tag("upload")
@Isolated("MessageUploader caches Spring beans in static fields")
class PATHHL7HandlerIntegrationTest extends CarlosTestBase {
    @PersistenceContext(unitName = "entityManagerFactory") private EntityManager em;
    @Autowired private Hl7TextMessageDao messages;
    @Autowired private Hl7TextInfoDao infos;
    @Autowired private ProviderLabRoutingDao routes;
    private final Map<Field, Object> originalBeans = new LinkedHashMap<>();

    @BeforeEach
    void bindRealUploaderDaos() throws Exception {
        // A unit test may have initialized this legacy class first with mocked Spring beans.
        for (Field field : MessageUploader.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())
                    && (field.getName().endsWith("Dao") || field.getName().endsWith("Manager"))) {
                field.setAccessible(true);
                originalBeans.put(field, field.get(null));
                field.set(null, applicationContext.getBean(field.getType()));
            }
        }
        em.createNativeQuery("CREATE TABLE IF NOT EXISTS providerLabRoutingLock (lab_no INT PRIMARY KEY)").executeUpdate();
    }

    @AfterEach
    void restoreUploaderDaos() throws Exception {
        for (var entry : originalBeans.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    static Stream<Arguments> labMessages() throws Exception {
        var fixtures = new ArrayList<Arguments>();
        InputStream resource = PATHHL7HandlerIntegrationTest.class.getResourceAsStream("/excelleris_test_lab_data.zip");
        assertThat(resource).as("required PATHL7 archive").isNotNull();
        try (ZipInputStream zip = new ZipInputStream(resource)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (!entry.isDirectory() && entry.getName().endsWith(".txt")) {
                    fixtures.add(Arguments.of(entry.getName(), new String(zip.readAllBytes(), StandardCharsets.UTF_8)));
                }
            }
        }
        assertThat(fixtures).as("all six archived reports must be exercised").hasSize(6);
        return fixtures.stream();
    }

    @ParameterizedTest(name = "should persist and route PATHL7 report {0}")
    @MethodSource("labMessages")
    void shouldPersistAndRoutePathl7LabMessage_whenArchiveContainsReport(String fixture, String body) throws Exception {
        PATHL7Handler parser = new PATHL7Handler();
        parser.init(body);
        RouteReportResults result = new RouteReportResults();
        MessageUploader.routeReport(AuthUtils.initLoginContext(), "PATHHL7HandlerTest", "PATHL7", body, 983440, result);
        em.flush();
        em.clear();

        assertThat(result.segmentId).as(fixture).isPositive();
        var stored = messages.find(result.segmentId);
        assertThat(stored).isNotNull();
        assertThat(stored.getType()).isEqualTo("PATHL7");
        assertThat(new String(Base64.getDecoder().decode(stored.getBase64EncodedeMessage()), StandardCharsets.UTF_8))
                .isEqualTo(body);
        var info = infos.findLabId(result.segmentId);
        assertThat(info).isNotNull();
        assertThat(info.getAccessionNumber()).isEqualTo(parser.getAccessionNum());
        assertThat(info.getLabel()).as("complete panel label, including the fixture exceeding 255 characters")
                .isEqualTo(parser.getLabel().trim());
        assertThat(routes.findAllLabRoutingByIdandType(result.segmentId, "HL7"))
                .as("uploaded result must reach an inbox").isNotEmpty();
    }
}
