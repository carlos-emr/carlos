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
import static org.mockito.Mockito.verifyNoInteractions;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.util.Terser;
import io.github.carlos_emr.carlos.PMmodule.dao.ProgramDao;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.AdmissionDao;
import io.github.carlos_emr.carlos.commn.dao.AppointmentArchiveDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.test.hl7.HostileHl7Xml;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * {@link PhsStarHandler#init(String)} is the only production code that hands text to HAPI's XML
 * parser, through {@code GenericParser}, and it is reached by a PHS/STAR lab upload (issue #5414).
 * A message that declares a document type must be refused before it is parsed and before the
 * handler touches any record; ordinary pipe and XML messages must still be read.
 */
@Tag("unit")
@Tag("security")
@Tag("hl7")
@DisplayName("PhsStarHandler XML boundary")
class PhsStarHandlerXmlBoundaryUnitTest extends CarlosUnitTestBase {

    private HostileHl7Xml hostile;
    private DemographicDao demographicDao;
    private OscarAppointmentDao appointmentDao;
    private AppointmentArchiveDao appointmentArchiveDao;
    private ProviderDao providerDao;
    private ProgramDao programDao;
    private AdmissionDao admissionDao;
    private PhsStarHandler handler;

    @BeforeEach
    void setUp() throws Exception {
        hostile = HostileHl7Xml.start();
        demographicDao = createAndRegisterMock(DemographicDao.class);
        appointmentDao = createAndRegisterMock(OscarAppointmentDao.class);
        appointmentArchiveDao = createAndRegisterMock(AppointmentArchiveDao.class);
        providerDao = createAndRegisterMock(ProviderDao.class);
        programDao = createAndRegisterMock(ProgramDao.class);
        admissionDao = createAndRegisterMock(AdmissionDao.class);
        handler = new PhsStarHandler();
    }

    @AfterEach
    void tearDown() throws Exception {
        hostile.close();
    }

    private void assertNoRecordTouched() {
        verifyNoInteractions(demographicDao, appointmentDao, appointmentArchiveDao, providerDao, programDao,
                admissionDao);
    }

    @Test
    @DisplayName("should refuse a message with an external file entity before parsing it")
    void shouldRefuseMessage_withExternalFileEntity() throws Exception {
        assertThatThrownBy(() -> handler.init(hostile.externalFileEntity()))
                .isInstanceOf(HL7Exception.class).hasMessageContaining("document type");
        assertThat(handler.msg).isNull();
        assertNoRecordTouched();
    }

    @Test
    @DisplayName("should refuse a message that points at an external DTD without calling out")
    void shouldRefuseMessage_withExternalDtd() throws Exception {
        assertThatThrownBy(() -> handler.init(hostile.externalDtd()))
                .isInstanceOf(HL7Exception.class).hasMessageContaining("document type");
        assertThat(hostile.requestCount()).isZero();
        assertNoRecordTouched();
    }

    @Test
    @DisplayName("should refuse a message that expands entities exponentially")
    void shouldRefuseMessage_withEntityExpansion() throws Exception {
        assertThatThrownBy(() -> handler.init(hostile.internalEntityExpansion(9)))
                .isInstanceOf(HL7Exception.class).hasMessageContaining("document type");
        assertNoRecordTouched();
    }

    @Test
    @DisplayName("should still read a well-formed HL7 v2 XML message")
    void shouldReadMessage_whenXmlIsWellFormed() throws Exception {
        handler.init(hostile.validXml());
        assertThat(handler.msg).isNotNull();
        assertThat(new Terser(handler.msg).get("/MSH-9-1")).isEqualTo("ADT");
        assertThat(new Terser(handler.msg).get("/PID-5-1")).isEqualTo(HostileHl7Xml.FAMILY_NAME);
    }

    @Test
    @DisplayName("should still read a pipe-delimited message as the uploader supplies it, one segment per line")
    void shouldReadMessage_whenPipeDelimited() throws Exception {
        handler.init(hostile.validPipe().replace('\r', '\n'));
        assertThat(handler.msg).isNotNull();
        assertThat(new Terser(handler.msg).get("/PID-5-1")).isEqualTo(HostileHl7Xml.FAMILY_NAME);
    }
}
