/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

import io.github.carlos_emr.carlos.commn.dao.EncounterFormDao;
import io.github.carlos_emr.carlos.commn.model.EncounterForm;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.XmlUtils;

@Tag("unit")
@DisplayName("JDBC XML utilities")
class JDBCUtilUnitTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should leave result set closing to the caller")
    void shouldLeaveResultSetClosing_toCaller() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        ResultSetMetaData metadata = mock(ResultSetMetaData.class);

        when(rs.getMetaData()).thenReturn(metadata);
        when(metadata.getColumnCount()).thenReturn(1);
        when(metadata.getColumnName(1)).thenReturn("name");
        when(rs.next()).thenReturn(true, false);
        when(rs.getString(1)).thenReturn("Alice");

        Document document = JDBCUtil.toDocument(rs);

        assertThat(document.getElementsByTagName("name").item(0).getTextContent()).isEqualTo("Alice");
        verify(rs, never()).close();
    }

    @Test
    @DisplayName("should parse valid XML import file names")
    void shouldParseValidXmlImportFileNames() throws Exception {
        JDBCUtil.FormImportTarget target = JDBCUtil.parseImportFileName("formFoo_123_20260520145500.xml");

        assertThat(target.formName()).isEqualTo("formFoo");
        assertThat(target.demographicNo()).isEqualTo("123");
        assertThat(target.timeStamp()).isEqualTo("20260520145500");
    }

    @Test
    @DisplayName("should reject XML import entries with path components")
    void shouldRejectXmlImportEntries_withPathComponents() {
        assertThatThrownBy(() -> JDBCUtil.parseImportFileName("../formFoo_123_20260520145500.xml"))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
        assertThatThrownBy(() -> JDBCUtil.parseImportFileName("nested/formFoo_123_20260520145500.xml"))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should reject XML import entries that do not match archived form naming")
    void shouldRejectXmlImportEntries_withUnexpectedShape() {
        assertThatThrownBy(() -> JDBCUtil.parseImportFileName("formFoo_abc_20260520145500.xml"))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
        assertThatThrownBy(() -> JDBCUtil.parseImportFileName("formFoo_123_20260520145500.txt"))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should allow registered encounter form tables")
    void shouldAllowRegisteredEncounterFormTables() throws Exception {
        EncounterFormDao encounterFormDao = mock(EncounterFormDao.class);
        EncounterForm encounterForm = new EncounterForm();
        encounterForm.setFormTable("formFoo");
        registerMock(EncounterFormDao.class, encounterFormDao);
        when(encounterFormDao.findByFormTable("formFoo")).thenReturn(List.of(encounterForm));

        assertThat(JDBCUtil.validateImportFormTable("formFoo")).isEqualTo("formFoo");
    }

    @Test
    @DisplayName("should reject unregistered encounter form tables")
    void shouldRejectUnregisteredEncounterFormTables() {
        EncounterFormDao encounterFormDao = mock(EncounterFormDao.class);
        registerMock(EncounterFormDao.class, encounterFormDao);
        when(encounterFormDao.findByFormTable("provider")).thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> JDBCUtil.validateImportFormTable("provider"))
                .isInstanceOf(JDBCUtil.XmlImportException.class);
    }

    @Test
    @DisplayName("should take XML import patient identity from archive entry name")
    void shouldTakeXmlImportPatientIdentity_fromArchiveEntryName() throws Exception {
        ResultSet rs = mock(ResultSet.class);
        Document document = XmlUtils.createSecureDocumentBuilderFactory()
                .newDocumentBuilder()
                .parse(new ByteArrayInputStream("""
                        <Results>
                          <Row>
                            <ID>99</ID>
                            <demographic_no>999</demographic_no>
                            <formEdited>attacker-controlled</formEdited>
                            <notes>trusted body field</notes>
                          </Row>
                        </Results>
                        """.getBytes(StandardCharsets.UTF_8)));
        JDBCUtil.FormImportTarget target =
                new JDBCUtil.FormImportTarget("formFoo", "123", "20260520145500");

        JDBCUtil.toResultSet(document, rs);
        JDBCUtil.applyTrustedImportTarget(target, rs);

        verify(rs, never()).updateString("ID", "99");
        verify(rs, never()).updateString("demographic_no", "999");
        verify(rs, never()).updateString("formEdited", "attacker-controlled");
        verify(rs).updateString("notes", "trusted body field");
        verify(rs).updateString("demographic_no", "123");
        verify(rs).updateString("formEdited", "20260520145500");
    }
}
