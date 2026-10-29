/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.integration.hl7.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import ca.uhn.hl7v2.HL7Exception;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.hl7.v2.Hl7XmlEntityAttackFixture;
import io.github.carlos_emr.carlos.commn.hl7.v2.Hl7XmlEntityAttackFixture.Attack;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * XML external entity regression tests for the PHS/STAR HL7 entry point (issue #5414).
 *
 * <p>{@link PhsStarHandler#init(String)} is the one production path where an uploaded HL7 body
 * reaches HAPI's encoding-sniffing parser: a lab upload typed {@code PHS} (web upload or REST, both
 * gated on {@code _lab} write) whose body starts with {@code <} is parsed as HL7 XML. These tests
 * drive that path with synthetic payloads and assert that no external file or network resource is
 * read, that the message is refused rather than half-parsed, and that legitimate pipe and XML
 * messages still parse.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("security")
@Tag("hl7")
@DisplayName("PhsStarHandler XML entity hardening")
class PhsStarHandlerXmlEntityUnitTest extends CarlosUnitTestBase {

    @TempDir
    Path tempDir;

    private Hl7XmlEntityAttackFixture fixture;

    @BeforeEach
    void setUpDaosAndObserver() throws Exception {
        // PhsStarHandler resolves these DAOs in field initialisers; the ADT^A01 payloads never
        // reach them because the handler ignores that trigger after parsing.
        registerMock(DemographicDao.class, mock(DemographicDao.class));
        registerMock(OscarAppointmentDao.class, mock(OscarAppointmentDao.class));
        registerMock(AppointmentArchiveDao.class, mock(AppointmentArchiveDao.class));
        registerMock(ProviderDao.class, mock(ProviderDao.class));
        registerMock(ProgramDao.class, mock(ProgramDao.class));
        registerMock(AdmissionDao.class, mock(AdmissionDao.class));
        fixture = new Hl7XmlEntityAttackFixture(tempDir);
    }

    @AfterEach
    void stopObserver() {
        fixture.close();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Attack.class)
    @DisplayName("should refuse the message without external access for each XML entity attack")
    void shouldRefuseMessageWithoutExternalAccess_forEachXmlEntityAttack(Attack attack) {
        PhsStarHandler handler = new PhsStarHandler();

        assertThatThrownBy(() -> handler.init(fixture.payload(attack)))
                .isInstanceOf(HL7Exception.class);

        assertThat(fixture.requests())
                .as("the parser must not contact any network resource named by the payload")
                .isEmpty();
        assertThat(handler.getLastName())
                .as("no entity text may surface in a parsed field")
                .doesNotContain(fixture.fileMarker())
                .doesNotContain(fixture.httpMarker())
                .doesNotContain(Hl7XmlEntityAttackFixture.INTERNAL_ENTITY_TEXT);
    }

    @Test
    @DisplayName("should parse patient name when a benign XML message has no DOCTYPE")
    void shouldParsePatientName_whenBenignXmlMessageHasNoDoctype() throws Exception {
        PhsStarHandler handler = new PhsStarHandler();

        handler.init(Hl7XmlEntityAttackFixture.benignXml());

        assertThat(handler.getLastName()).isEqualTo("SYNTHETIC");
        assertThat(handler.getFirstName()).isEqualTo("PATIENT");
        assertThat(fixture.requests()).isEmpty();
    }

    @Test
    @DisplayName("should parse patient name when the message is pipe encoded")
    void shouldParsePatientName_whenMessageIsPipeEncoded() throws Exception {
        PhsStarHandler handler = new PhsStarHandler();

        handler.init(Hl7XmlEntityAttackFixture.benignPipe());

        assertThat(handler.getLastName()).isEqualTo("SYNTHETIC");
        assertThat(handler.getFirstName()).isEqualTo("PATIENT");
    }

    @Test
    @DisplayName("should reject the body when it is neither pipe nor XML encoded")
    void shouldRejectBody_whenNeitherPipeNorXmlEncoded() {
        PhsStarHandler handler = new PhsStarHandler();

        assertThatThrownBy(() -> handler.init("not an HL7 message"))
                .isInstanceOf(HL7Exception.class);
    }
}
