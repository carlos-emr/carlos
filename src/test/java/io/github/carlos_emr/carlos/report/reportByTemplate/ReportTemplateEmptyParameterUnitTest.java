/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.report.reportByTemplate;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jdom2.JDOMException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/** Native upload/save/load path with an in-memory DAO; the XML and report objects are real. */
@Tag("unit")
@Tag("report")
class ReportTemplateEmptyParameterUnitTest extends CarlosUnitTestBase {
    private final AtomicReference<ReportTemplates> stored = new AtomicReference<>();
    private final LoggedInInfo info = new LoggedInInfo();
    private final Map<Integer, ReportTemplates> rows = new HashMap<>();
    private final AtomicInteger nextId = new AtomicInteger(17);
    private ReportTemplatesDao dao;
    @TempDir Path directory;

    @BeforeEach
    void templates() {
        rows.clear();
        nextId.set(17);
        stored.set(null);
        dao = mock(ReportTemplatesDao.class);
        var security = mock(SecurityInfoManager.class);
        registerMock(ReportTemplatesDao.class, dao);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), any(), any(), any())).thenReturn(true);
        when(dao.find(anyInt())).thenAnswer(call -> rows.get((Integer) call.getArgument(0)));
        when(dao.find(any(Object.class))).thenAnswer(call -> rows.get((Integer) call.getArgument(0)));
        doAnswer(call -> {
            ReportTemplates row = call.getArgument(0);
            row.setId(nextId.getAndIncrement());
            stored.set(row);
            rows.put(row.getId(), row);
            return null;
        }).when(dao).persist(any(ReportTemplates.class));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRetainALoneEmptyTextParameter_afterNativeSaveAndReopen(boolean selfClosing) {
        String parameter = selfClosing ? "<param id='pick' type='text' description='Choice'/>"
                : "<param id='pick' type='text' description='Choice'></param>";
        String xml = "<report title='FAKE empty parameter' description='Synthetic only' active='1'>"
                + "<query>SELECT '{pick}' AS chosen</query>" + parameter + "</report>";
        var manager = new ReportManager();
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(stored.get()).isNotNull();
        var report = manager.getReportTemplate("17");
        assertThat(report.getTitle()).isEqualTo("FAKE empty parameter");
        assertThat(report.getParameters()).hasSize(1);
        Parameter actual = (Parameter) report.getParameters().getFirst();
        assertThat(actual.getParamId()).isEqualTo("pick");
        assertThat(actual.getParamType()).isEqualTo("text");
        assertThat(actual.getParamDescription()).isEqualTo("Choice");
        assertThat(actual.getParamChoices()).isEmpty();
        assertThat(report.getPreparedSQL(Map.of("pick", new String[] {"FAKE chosen"})))
                .isEqualTo("SELECT 'FAKE chosen' AS chosen");
    }

    @ParameterizedTest
    @ValueSource(strings = {"<param/>", "Literal <param note='placeholder'> text", "Literal <param id='documentation'/> text", "Literal <param id='documentation' type='text'/> text", "Literal <param id='x' type='text' description='X' missing:hint='literal'/> text"})
    void shouldRetainLiteralReportText_withoutInventingParameterMetadata(String text) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE literal text' description='Synthetic only'><query>SELECT 1</query>" + text + "</report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).isEmpty();
    }

    @Test
    void shouldRetainBareLiteralTags_whenNoLaterSpaceExists() {
        assertThat(io.github.carlos_emr.carlos.util.UtilXML.escapeXML("<report><param/></report>"))
                .isEqualTo("<report>&lt;param/></report>");
        assertThat(io.github.carlos_emr.carlos.util.UtilXML.escapeXML("<report><param/> text</report>"))
                .isEqualTo("<report>&lt;param/> text</report>");
    }

    @Test
    void shouldRestoreRealMetadata_besideLiteralParameterText() {
        var manager = new ReportManager();
        String xml = "<report title='FAKE mixed text' description='Synthetic only'><query>SELECT '{pick}'</query>"
                + "Literal <param note='placeholder'> and <param/> text "
                + "<param id='pick' type='text' description='Choice'/></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        var parameters = manager.getReportTemplate("17").getParameters();
        assertThat(parameters).hasSize(1);
        assertThat(((Parameter) parameters.getFirst()).getParamId()).isEqualTo("pick");
    }

    @ParameterizedTest
    @ValueSource(strings = {"' OR 1=1 --", "x'; DELETE FROM FAKE_table; --"})
    void shouldBindSubmittedSqlSyntax_afterReopeningAnEmptyTextParameter(String submitted) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE bound text' description='Synthetic only'><query>SELECT '{pick}' AS chosen</query>"
                + "<param id='pick' type='text' description='Choice'/></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        var report = (ReportObjectGeneric) manager.getReportTemplate("17");
        var query = report.buildParameterizedSql(Map.of("pick", new String[] {submitted}));
        assertThat(query.getSql()).isEqualTo("SELECT ? AS chosen");
        assertThat(query.getParams()).containsExactly(submitted);
    }

    @ParameterizedTest
    @ValueSource(strings = {"\n", "\t", "\r", "\r\n"})
    void shouldRestoreEmptyMetadata_whenTheTagNameEndsWithXmlWhitespace(String whitespace) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE whitespace' description='Synthetic only'><query>SELECT '{pick}'</query>"
                + "<param" + whitespace + "id='pick' type='text' description='Choice'/></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).singleElement()
                .satisfies(parameter -> assertThat(((Parameter) parameter).getParamId()).isEqualTo("pick"));
        assertThat(manager.updateTemplate(null, "17", rows.get(17).getTemplateXml(), info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "<!-- <param id='fake' type='text' description='Fake'/> --><param id='real' type='text' description='Real'/>",
            "<param id='real' type='text' description='Real'/><!-- <param id='fake' type='text' description='Fake'/> -->",
            "<!-- <param id='fake' type='text' description='Fake'/> --><!-- <param id='fake2' type='text' description='Fake'/> --><param id='real' type='text' description='Real'/>"})
    void shouldRestoreOnlyActiveMetadata_whenCommentsSurroundRealParameters(String metadata) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE comments' description='Synthetic only'><query>SELECT '{real}'</query>" + metadata + "</report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).singleElement()
                .satisfies(parameter -> assertThat(((Parameter) parameter).getParamId()).isEqualTo("real"));
        assertThat(rows.get(17).getTemplateXml()).contains("fake");
        assertThat(manager.updateTemplate(null, "17", rows.get(17).getTemplateXml(), info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
    }

    @Test
    void shouldRetainUnclosedCommentText_withoutActivatingItsParameterLiteral() {
        var manager = new ReportManager();
        String xml = "<report title='FAKE literal comment' description='Synthetic only'><query>SELECT 1</query>"
                + "<!-- <param id='fake' type='text' description='Fake'/></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).isEmpty();
        assertThat(rows.get(17).getTemplateXml()).contains("fake");
    }

    @Test
    void shouldRestoreEmptyMetadata_withoutRecursiveMatchingOfLongWhitespace() {
        var manager = new ReportManager();
        String xml = "<report title='FAKE long parameter' description='Synthetic only'><query>SELECT '{pick}'</query>"
                + "<param" + " ".repeat(50_000) + "id='pick' type='text' description='Choice'/></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        var parameters = manager.getReportTemplate("17").getParameters();
        assertThat(parameters).hasSize(1);
        assertThat(((Parameter) parameters.getFirst()).getParamId()).isEqualTo("pick");
    }

    @Test
    void shouldKeepSiblingChoicesAndAnotherTemplate_whenEditingAnEmptyParameter() {
        var manager = new ReportManager();
        String mixed = "<report title='FAKE mixed' description='Synthetic only'><query>SELECT '{pick}' AS chosen</query>"
                + "<param id='pick' type='text' description='Choice'/><param id='list' type='list' description='List'>"
                + "<choice id='a'>Alpha</choice></param></report>";
        String other = "<report title='FAKE other' description='Other'><query>SELECT 2</query>"
                + "<param id='other' type='list' description='Other list'><choice id='b'>Beta</choice></param></report>";
        assertThat(manager.addTemplate(null, mixed, info)).isEqualTo("Saved Successfully");
        assertThat(manager.addTemplate(null, other, info)).isEqualTo("Saved Successfully");
        String otherBefore = rows.get(18).getTemplateXml();
        assertThat(manager.updateTemplate(null, "17", mixed.replace("description='Choice'", "description='Changed'"), info))
                .isEqualTo("Saved Successfully");
        var params = manager.getReportTemplate("17").getParameters();
        assertThat(params).hasSize(2);
        assertThat(((Parameter) params.get(0)).getParamDescription()).isEqualTo("Changed");
        assertThat(((Parameter) params.get(1)).getParamChoices()).hasSize(1);
        assertThat(rows.get(18).getTemplateXml()).isEqualTo(otherBefore);
        assertThat(manager.getReportTemplate("18").getTitle()).isEqualTo("FAKE other");
        assertThat(manager.getReportTemplate("18").getParameters()).hasSize(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT 1 < 2 AS chosen", "SELECT 'A&B' AS chosen", "SELECT '<param/>' AS chosen"})
    void shouldKeepLegacyUnescapedSql_whenLoadingAndSaving(String sql) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE raw query' description='Synthetic only'><type>SQL</type><query>" + sql + "</query></report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getSQL("17")).isEqualTo(sql);
        assertThat(manager.getReportTemplate("17").getTitle()).isEqualTo("FAKE raw query");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldRejectDoctypesAndEntityExpansion_beforePersistence(boolean external) throws Exception {
        var secret = Files.writeString(directory.resolve("sentinel.txt"), "FAKE-5813-SECRET");
        String declaration = external ? "<!DOCTYPE report [<!ENTITY secret SYSTEM '" + secret.toUri() + "'>]>"
                : "<!DOCTYPE report [<!ENTITY secret 'FAKE-5813-SECRET'>]>";
        String xml = declaration + "<report title='FAKE entity' description='Synthetic only'><type>SQL</type>"
                + "<query>SELECT '&secret;' AS chosen</query></report>";
        var manager = new ReportManager();
        assertThatThrownBy(() -> manager.readXml(xml)).isInstanceOf(JDOMException.class);
        assertThat(manager.addTemplate(null, xml, info)).startsWith("Error").doesNotContain("FAKE-5813-SECRET");
        verify(dao, never()).persist(any(ReportTemplates.class));
    }

    @Test
    void shouldRetainTheSelectOnlyPolicy_whenParsingAnOrdinaryXmlTemplate() {
        var manager = new ReportManager();
        String xml = "<report title='FAKE refused' description='Synthetic only'><type>SQL</type>"
                + "<query>DELETE FROM demographic</query></report>";
        assertThat(manager.addTemplate(null, xml, info)).startsWith("Error: The <query> was refused");
        verify(dao, never()).persist(any(ReportTemplates.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"SELECT 'A&B', '{pick}' AS chosen", "SELECT 1 < 2, '{pick}' AS chosen"})
    void shouldRetainTheEmptyParameter_whenLegacySqlNeedsEscaping(String sql) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE combined' description='Synthetic only'>\n<type>SQL</type>\n<query>" + sql
                + "</query>\n<param id='pick' type='text' description='Choice'/>\n</report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getSQL("17")).isEqualTo(sql);
        var report = manager.getReportTemplate("17");
        assertThat(report.getParameters()).hasSize(1);
        assertThat(((Parameter) report.getParameters().getFirst()).getParamId()).isEqualTo("pick");
    }

    @Test
    void shouldRestoreEmptyParameters_inEachReportOfAReportList() {
        var manager = new ReportManager();
        String xml = "<report-list><report title='FAKE first' description='One'><query>SELECT 'A&B'</query>"
                + "<param id='one' type='text' description='One'/><param id='two' type='text' description='Two'/></report>"
                + "<report title='FAKE second' description='Two'><query>SELECT 2</query>"
                + "<param id='three' type='text' description='Three'/></report></report-list>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        var first = manager.getReportTemplate("17").getParameters();
        assertThat(first).hasSize(2);
        assertThat(((Parameter) first.get(0)).getParamId()).isEqualTo("one");
        assertThat(((Parameter) first.get(1)).getParamId()).isEqualTo("two");
        var second = manager.getReportTemplate("18").getParameters();
        assertThat(second).hasSize(1);
        assertThat(((Parameter) second.getFirst()).getParamId()).isEqualTo("three");
    }

    @Test
    void shouldRetainLegacyRefusal_forRootCdataContainingParameterText() {
        var manager = new ReportManager();
        String xml = "<report title='FAKE cdata' description='Synthetic only'><query>SELECT 1</query>"
                + "<![CDATA[<param id='fake'/>]]></report>";
        assertThat(manager.addTemplate(null, xml, info)).startsWith("Error");
        verify(dao, never()).persist(any(ReportTemplates.class));
    }

    @ParameterizedTest
    @ValueSource(strings = {"<parameter-placeholder ignored='yes'/> ", "<!-- <param id='fake'/> -->", "<!-- <param id='fake' type='text' description='Fake'/> -->"})
    void shouldNotInventParameters_fromCommentsOrLookalikeMetadata(String metadata) {
        var manager = new ReportManager();
        String xml = "<report title='FAKE metadata' description='Synthetic only'><query>SELECT 1</query>" + metadata + "</report>";
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).isEmpty();
    }
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveInheritedMetadataNamespaces_afterSaveAndReopen(boolean onList) {
        String namespace = " xmlns:m='urn:meta' xmlns:extra='urn:extra?a=1&amp;b=2'";
        String xml = "<report title='FAKE namespace' description='Synthetic only'" + (onList ? "" : namespace)
                + "><query>SELECT 'A&amp;B', '{pick}' AS chosen</query>"
                + "<param id='pick' type='text' description='Choice' m:hint='optional'/></report>";
        if (onList) xml = "<report-list" + namespace + ">" + xml + "</report-list>";
        var manager = new ReportManager();
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
        assertThat(manager.updateTemplate(null, "17", rows.get(17).getTemplateXml(), info))
                .isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
        assertThat(rows.get(17).getTemplateXml()).contains("m:hint=\"optional\"");
    }

    @ParameterizedTest
    @ValueSource(strings = {"root", "list", "prefixed-list"})
    void shouldRetainTheEmptyParameter_withPrefixedReportRoot(String arrangement) {
        String xml = "<r:report xmlns:r='urn:report' title='FAKE prefixed' description='Synthetic only'>"
                + "<query>SELECT 'A&B', '{pick}' AS chosen</query>"
                + "<param id='pick' type='text' description='Choice'/></r:report>";
        if ("list".equals(arrangement)) xml = "<report-list>" + xml + "</report-list>";
        if ("prefixed-list".equals(arrangement)) {
            xml = "<r:report-list xmlns:r='urn:report'>" + xml + "</r:report-list>";
        }
        var manager = new ReportManager();
        assertThat(manager.addTemplate(null, xml, info)).isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
        assertThat(manager.updateTemplate(null, "17", rows.get(17).getTemplateXml(), info))
                .isEqualTo("Saved Successfully");
        assertThat(manager.getReportTemplate("17").getParameters()).hasSize(1);
        assertThat(manager.getSQL("17")).isEqualTo("SELECT 'A&B', '{pick}' AS chosen");
    }

}
