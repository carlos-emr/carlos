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
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.drools.DroolsHelper;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementTypeBeanHandler;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.ImportMeasurementTypes;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.util.RuleBaseCreator;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import org.jdom2.Element;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.kie.api.KieBase;
import org.mockito.MockedConstruction;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

/**
 * A customized flowsheet is an XML copy of its base definition (issue #4433).
 *
 * <p>{@code MeasurementTemplateFlowSheetConfig.makeNewFlowsheet} serializes the base flowsheet
 * with {@code getExportFlowsheet} and re-parses it. Every root attribute the export leaves out is
 * silently missing from the copy, and the flowsheet-level {@code ds_rules} file is the one that
 * matters clinically: without it the copy has no decision support at all, so a provider or patient
 * customization removes the flowsheet's warnings and recommendations without telling anyone.</p>
 *
 * <p>The measurement-type import and lookup that parsing and export perform against the database
 * are replaced with construction mocks; the rules themselves are the real shipped DRL files.</p>
 */
@Tag("unit")
@Tag("drools")
@Tag("measurement")
class FlowsheetCopyRuleRetentionUnitTest extends CarlosUnitTestBase {

    private MeasurementTemplateFlowSheetConfig configuration;
    private MockedConstruction<ImportMeasurementTypes> importMeasurementTypes;
    private MockedConstruction<EctMeasurementTypeBeanHandler> measurementTypeLookups;

    @BeforeEach
    void createUnregisteredConfiguration() throws Exception {
        var constructor = MeasurementTemplateFlowSheetConfig.class.getDeclaredConstructor();
        constructor.setAccessible(true);
        configuration = constructor.newInstance();
        // MeasurementInfo looks the manager up when it is constructed; the rules under test only
        // ask it about recorded measurements, never about the patient.
        registerMock(DemographicManager.class, mock(DemographicManager.class));
        importMeasurementTypes = mockConstruction(ImportMeasurementTypes.class);
        measurementTypeLookups = mockConstruction(EctMeasurementTypeBeanHandler.class);
    }

    @AfterEach
    void closeConstructionMocks() {
        measurementTypeLookups.close();
        importMeasurementTypes.close();
    }

    @Test
    @DisplayName("should declare the flowsheet-level rules file when exporting a flowsheet")
    void shouldWriteRulesFileName_whenExportingFlowsheet() {
        MeasurementFlowSheet base = parse(diabetesDefinition());

        Element exported = configuration.getExportFlowsheet(base);

        assertThat(exported.getAttributeValue("ds_rules")).isEqualTo("diab.drl");
    }

    @Test
    @DisplayName("should keep the flowsheet-level rules file on the copy made for a customization")
    void shouldKeepFlowsheetRulesFile_whenCopyingForCustomization() throws Exception {
        MeasurementFlowSheet base = parse(diabetesDefinition());

        MeasurementFlowSheet copy = configuration.makeNewFlowsheet(base);

        assertThat(copy.getDsRulesFileName()).isEqualTo("diab.drl");
        assertThat(copy.ruleBase)
                .as("a copy is re-parsed on every request, so it must reuse the compiled rules, not recompile them")
                .isSameAs(base.ruleBase);
        MeasurementInfo copyMessages = copy.getMessages(new MeasurementInfo("1"));
        MeasurementInfo baseMessages = base.getMessages(new MeasurementInfo("1"));
        assertThat(copyMessages.getWarnings())
                .as("the copy must fire the same diab.drl rules as its base")
                .isNotEmpty()
                .containsExactlyInAnyOrderElementsOf(baseMessages.getWarnings());
    }

    @Test
    @DisplayName("should still show the flowsheet rules after a customization hides one of its items")
    void shouldFireFlowsheetRules_whenCustomizedFlowsheetIsEvaluated() throws Exception {
        MeasurementFlowSheet base = parse(diabetesDefinition());
        MeasurementTemplateFlowSheetConfig spied = spy(configuration);
        doReturn(base).when(spied).getFlowSheet("issue4433");

        MeasurementFlowSheet customized = spied.getFlowSheet("issue4433",
                List.of(change(FlowSheetCustomization.DELETE, "BP")));

        assertThat(customized).isNotSameAs(base);
        assertThat(customized.getVisibleMeasurementList()).containsExactly("A1C", "WAIS");
        MeasurementInfo messages = customized.getMessages(new MeasurementInfo("1"));
        assertThat(messages.hasWarning("A1C"))
                .as("diab.drl warns about an A1C that was never recorded")
                .isTrue();
    }

    @Test
    @DisplayName("should run the flowsheet rules alongside a recommendation a customization adds")
    void shouldRunFlowsheetRulesAlongsideCustomization_whenCustomizationAddsRecommendation() throws Exception {
        MeasurementFlowSheet base = parse(diabetesDefinition());
        MeasurementTemplateFlowSheetConfig spied = spy(configuration);
        doReturn(base).when(spied).getFlowSheet("issue4433");

        MeasurementFlowSheet customized = spied.getFlowSheet("issue4433", List.of(
                change(FlowSheetCustomization.UPDATE, "WAIS", customWarning("WAIS", "issue4433 custom waist warning"))));

        MeasurementInfo messages = customized.getMessages(new MeasurementInfo("1"));
        assertThat(messages.getWarnings())
                .as("the customization's own rule fires")
                .contains("issue4433 custom waist warning");
        assertThat(messages.hasWarning("A1C"))
                .as("diab.drl still fires: adding one rule must not silently remove the flowsheet's rules")
                .isTrue();
        assertThat(base.getMessages(new MeasurementInfo("1")).getWarnings())
                .as("the shared base definition is not changed by the customization")
                .doesNotContain("issue4433 custom waist warning");
    }

    @Test
    @DisplayName("should keep a flowsheet's own item recommendations as its rules, as the base definition does")
    void shouldKeepItemRecommendationsOnly_whenBaseFlowsheetCarriesThem() throws Exception {
        // Parity with createflowsheet: item recommendations replace the ds_rules file, so a
        // customized copy of such a flowsheet must not start firing diab.drl either.
        MeasurementFlowSheet base = parse("<flowsheet name='issue4433' ds_rules='diab.drl'>"
                + "<item measurement_type='A1C' display_name='A1C'><rules>"
                + "<recommendation strength='warning' message='issue4433 item A1C rule'>"
                + "<condition type='monthrange' param='' value='-1'/></recommendation></rules></item>"
                + "<item measurement_type='BP' display_name='BP'/><item measurement_type='WAIS' display_name='Waist'/>"
                + "</flowsheet>");
        MeasurementTemplateFlowSheetConfig spied = spy(configuration);
        doReturn(base).when(spied).getFlowSheet("issue4433");

        MeasurementFlowSheet customized = spied.getFlowSheet("issue4433", List.of(
                change(FlowSheetCustomization.UPDATE, "WAIS", customWarning("WAIS", "issue4433 custom waist warning"))));

        @SuppressWarnings("unchecked")
        List<String> warnings = customized.getMessages(new MeasurementInfo("1")).getWarnings();
        assertThat(warnings).contains("issue4433 item A1C rule", "issue4433 custom waist warning");
        assertThat(warnings).as("diab.drl is not this flowsheet's rule set").doesNotContain("no BP has been recorded");
        assertThat(base.getMessages(new MeasurementInfo("1")).getWarnings())
                .containsExactly("issue4433 item A1C rule");
    }

    @Test
    @DisplayName("should reuse the base flowsheet's compiled rules instead of reloading the rules file")
    void shouldNotReloadRulesFile_whenCopyingForCustomization() throws Exception {
        MeasurementFlowSheet base = parse(diabetesDefinition());

        try (MockedStatic<DroolsHelper> drools = mockStatic(DroolsHelper.class, Mockito.CALLS_REAL_METHODS)) {
            MeasurementFlowSheet copy = configuration.makeNewFlowsheet(base);

            assertThat(copy.ruleBase).isSameAs(base.ruleBase);
            drools.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should keep the missing rules file on a copy of a flowsheet whose file did not load")
    void shouldKeepMissingRulesFile_whenCopyingFlowsheetWhoseFileDidNotLoad() throws Exception {
        MeasurementFlowSheet base = parse("<flowsheet name='issue4433' ds_rules='issue4433-absent.drl'>"
                + "<item measurement_type='WT' display_name='Weight'/></flowsheet>");

        MeasurementFlowSheet copy = configuration.makeNewFlowsheet(base);

        assertThat(copy.getDsRulesFileName()).isEqualTo("issue4433-absent.drl");
        assertThatThrownBy(() -> copy.getMessages(new MeasurementInfo("1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issue4433-absent.drl");
    }

    @Test
    @DisplayName("should not invent a rules file for a flowsheet that names none")
    void shouldOmitRulesFile_whenFlowsheetDeclaresNone() throws Exception {
        MeasurementFlowSheet base = parse("<flowsheet name='issue4433' display_name='No rules'>"
                + "<item measurement_type='WT' display_name='Weight'/></flowsheet>");

        Element exported = configuration.getExportFlowsheet(base);

        assertThat(exported.getAttribute("ds_rules")).isNull();
        assertThat(configuration.makeNewFlowsheet(base).getDsRulesFileName()).isNull();
    }

    @Test
    @DisplayName("should treat an empty ds_rules attribute as no rules file")
    void shouldIgnoreBlankRulesFile_whenFlowsheetDeclaresEmptyDsRules() throws Exception {
        try (MockedStatic<DroolsHelper> drools = mockStatic(DroolsHelper.class, Mockito.CALLS_REAL_METHODS)) {
            MeasurementFlowSheet base = configuration.validateFlowsheet(
                    "<flowsheet name='issue4433' ds_rules=''><item measurement_type='WT' display_name='Weight'/></flowsheet>");

            assertThat(base.getDsRulesFileName()).isNull();
            assertThat(configuration.getExportFlowsheet(base).getAttribute("ds_rules")).isNull();
            drools.verifyNoInteractions();
        }
    }

    @Test
    @DisplayName("should copy the header file name, not its rendered HTML, and the root flags")
    void shouldRoundTripHeaderFileAndFlags_whenCopyingFlowsheet() throws Exception {
        MeasurementFlowSheet base = parse("<flowsheet name='issue4433' display_name='INR'"
                + " top_HTML='inr.html' is_universal='true' is_medical='false'>"
                + "<item measurement_type='INR' display_name='INR'/></flowsheet>");
        assertThat(base.getTopHTMLStream()).as("fixture: inr.html ships on the classpath").isNotEmpty();

        Element exported = configuration.getExportFlowsheet(base);
        MeasurementFlowSheet copy = configuration.makeNewFlowsheet(base);

        assertThat(exported.getAttributeValue("top_HTML")).isEqualTo("inr.html");
        assertThat(copy.getTopHTMLFileName()).isEqualTo("inr.html");
        assertThat(copy.getTopHTMLStream()).isEqualTo(base.getTopHTMLStream());
        assertThat(copy.isUniversal()).isTrue();
        assertThat(copy.isMedical()).isFalse();
    }

    @Test
    @DisplayName("should keep the parser defaults for a flowsheet that sets no root flags")
    void shouldKeepDefaultFlags_whenFlowsheetSetsNone() throws Exception {
        MeasurementFlowSheet base = parse(diabetesDefinition());

        Element exported = configuration.getExportFlowsheet(base);
        MeasurementFlowSheet copy = configuration.makeNewFlowsheet(base);

        assertThat(exported.getAttribute("is_universal")).isNull();
        assertThat(exported.getAttribute("is_medical")).isNull();
        assertThat(exported.getAttribute("top_HTML")).isNull();
        assertThat(copy.isUniversal()).isFalse();
        assertThat(copy.isMedical()).isTrue();
    }

    @Test
    @DisplayName("should name the rules file that did not load instead of blaming compilation")
    void shouldNameMissingRulesFile_whenDeclaredRulesFailToLoad() {
        MeasurementFlowSheet flowsheet = parse("<flowsheet name='issue4433' ds_rules='issue4433-absent.drl'>"
                + "<item measurement_type='WT' display_name='Weight'/></flowsheet>");

        assertThat(flowsheet.getDsRulesFileName()).isEqualTo("issue4433-absent.drl");
        assertThatThrownBy(() -> flowsheet.getMessages(new MeasurementInfo("1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issue4433")
                .hasMessageContaining("issue4433-absent.drl")
                .hasMessageContaining("was not loaded")
                .hasMessageNotContaining("compilation");
    }

    @Test
    @DisplayName("should name both missing sources when the rules file and the item recommendations fail")
    void shouldNameBothCauses_whenRulesFileAndItemRecommendationsFailToLoad() {
        try (MockedConstruction<RuleBaseCreator> compiler = mockConstruction(RuleBaseCreator.class,
                (creator, context) -> when(creator.getRuleBase(anyString(), anyList()))
                        .thenThrow(new IllegalStateException("simulated item rule compilation failure")))) {
            MeasurementFlowSheet flowsheet = parse("<flowsheet name='issue4433' ds_rules='issue4433-absent.drl'>"
                    + "<item measurement_type='A1C' display_name='A1C'><rules>"
                    + "<recommendation strength='warning' message='issue4433 item A1C rule'>"
                    + "<condition type='monthrange' param='' value='-1'/></recommendation></rules></item></flowsheet>");

            assertThatThrownBy(() -> flowsheet.getMessages(new MeasurementInfo("1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("issue4433-absent.drl")
                    .hasMessageContaining("its item recommendations did not compile");
        }
    }

    @Test
    @DisplayName("should say that item recommendations did not compile when they are the only rules")
    void shouldNameItemRecommendations_whenTheyFailToCompile() {
        try (MockedConstruction<RuleBaseCreator> compiler = mockConstruction(RuleBaseCreator.class,
                (creator, context) -> when(creator.getRuleBase(anyString(), anyList()))
                        .thenThrow(new IllegalStateException("simulated item rule compilation failure")))) {
            MeasurementFlowSheet flowsheet = parse("<flowsheet name='issue4433'>"
                    + "<item measurement_type='A1C' display_name='A1C'><rules>"
                    + "<recommendation strength='warning' message='issue4433 item A1C rule'>"
                    + "<condition type='monthrange' param='' value='-1'/></recommendation></rules></item></flowsheet>");

            assertThatThrownBy(() -> flowsheet.getMessages(new MeasurementInfo("1")))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("its item recommendations did not compile")
                    .hasMessageNotContaining("rules file");
        }
    }

    @Test
    @DisplayName("should drop the previous rules when a later rules file does not load")
    void shouldDropPreviousRules_whenReloadedRulesFileDoesNotLoad() {
        MeasurementFlowSheet flowsheet = parse(diabetesDefinition());
        assertThat(flowsheet.ruleBase).as("fixture: diab.drl loaded").isNotNull();

        flowsheet.loadRuleBase("issue4433-absent.drl");

        assertThat(flowsheet.ruleBase).isNull();
        assertThatThrownBy(() -> flowsheet.getMessages(new MeasurementInfo("1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issue4433-absent.drl");
    }

    @Test
    @DisplayName("should say that a flowsheet declares no rules when it has none")
    void shouldSayNoRulesDeclared_whenFlowsheetHasNone() {
        MeasurementFlowSheet flowsheet = parse("<flowsheet name='issue4433'>"
                + "<item measurement_type='WT' display_name='Weight'/></flowsheet>");

        assertThatThrownBy(() -> flowsheet.getMessages(new MeasurementInfo("1")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("issue4433")
                .hasMessageContaining("declares no decision support rules")
                .hasMessageNotContaining("compilation");
    }

    @Test
    @DisplayName("should compile a flowsheet rules file once and reuse it for every copy")
    void shouldReuseCompiledRules_whenSameRulesFileIsLoadedAgain(@TempDir Path rulesDirectory) throws Exception {
        writeRules(rulesDirectory, "issue4433.drl", uniqueRules("A1C"));

        try (MockedStatic<CarlosProperties> properties = measurementRulesDirectory(rulesDirectory);
                MockedStatic<DroolsHelper> drools = mockStatic(DroolsHelper.class, Mockito.CALLS_REAL_METHODS)) {
            MeasurementFlowSheet first = new MeasurementFlowSheet();
            first.loadRuleBase("issue4433.drl");
            MeasurementFlowSheet second = new MeasurementFlowSheet();
            second.loadRuleBase("issue4433.drl");

            assertThat(first.ruleBase).isNotNull();
            assertThat(second.ruleBase).isSameAs(first.ruleBase);
            drools.verify(() -> DroolsHelper.createKieBaseFromDrl(anyString()), times(1));
        }
    }

    @Test
    @DisplayName("should recompile a flowsheet rules file whose content changed on disk")
    void shouldRecompileRules_whenRulesFileContentChanges(@TempDir Path rulesDirectory) throws Exception {
        writeRules(rulesDirectory, "issue4433.drl", uniqueRules("A1C"));

        try (MockedStatic<CarlosProperties> properties = measurementRulesDirectory(rulesDirectory)) {
            MeasurementFlowSheet before = new MeasurementFlowSheet();
            before.loadRuleBase("issue4433.drl");
            writeRules(rulesDirectory, "issue4433.drl", uniqueRules("LDL"));
            MeasurementFlowSheet after = new MeasurementFlowSheet();
            after.loadRuleBase("issue4433.drl");

            KieBase edited = after.ruleBase;
            assertThat(edited).isNotNull().isNotSameAs(before.ruleBase);
            MeasurementInfo messages = after.getMessages(new MeasurementInfo("1"));
            assertThat(messages.hasWarning("LDL")).isTrue();
            assertThat(messages.hasWarning("A1C")).isFalse();
        }
    }

    private MeasurementFlowSheet parse(String xml) {
        MeasurementFlowSheet flowsheet = configuration.validateFlowsheet(xml);
        assertThat(flowsheet).as("fixture flowsheet must parse").isNotNull();
        return flowsheet;
    }

    /** A DRL-backed flowsheet: three items, no item recommendations, rules from diab.drl (which ignores WAIS). */
    private static String diabetesDefinition() {
        return "<flowsheet name='issue4433' display_name='Diabetes' ds_rules='diab.drl'"
                + " warning_colour='#E00000' recommendation_colour='yellow'>"
                + "<item measurement_type='A1C' display_name='A1C'/>"
                + "<item measurement_type='BP' display_name='BP'/>"
                + "<item measurement_type='WAIS' display_name='Waist'/>"
                + "</flowsheet>";
    }

    private static FlowSheetCustomization change(String action, String measurement) {
        return change(action, measurement, null);
    }

    private static FlowSheetCustomization change(String action, String measurement, String payload) {
        FlowSheetCustomization customization = new FlowSheetCustomization();
        customization.setAction(action);
        customization.setMeasurement(measurement);
        customization.setPayload(payload);
        return customization;
    }

    /** An Update Flowsheet payload: the item with one "never recorded" warning rule, as the editor saves it. */
    private static String customWarning(String measurement, String message) {
        return "<item measurement_type=\"" + measurement + "\" display_name=\"" + measurement + "\"><rules>"
                + "<recommendation strength=\"warning\" message=\"" + message + "\">"
                + "<condition type=\"monthrange\" param=\"\" value=\"-1\" /></recommendation></rules></item>";
    }

    /** DRL whose package name is unique, so no earlier test can have cached its compiled form. */
    private static String uniqueRules(String measurement) {
        String packageName = "issue4433_" + UUID.randomUUID().toString().replace('-', '_');
        return "package " + packageName + ";\n"
                + "import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementInfo;\n"
                + "rule \"" + measurement + " never recorded\"\n"
                + "    when\n"
                + "        m : MeasurementInfo()\n"
                + "        eval( m.getLastDateRecordedInMonths(\"" + measurement + "\") == -1 )\n"
                + "    then\n"
                + "        m.addWarning(\"" + measurement + "\", \"" + measurement + " has never been recorded\");\n"
                + "end\n";
    }

    private static void writeRules(Path directory, String fileName, String drl) throws Exception {
        Files.writeString(directory.resolve(fileName), drl, StandardCharsets.UTF_8);
    }

    /** Points MEASUREMENT_DS_DIRECTORY at the test's directory for this thread only. */
    private static MockedStatic<CarlosProperties> measurementRulesDirectory(Path directory) {
        CarlosProperties carlosProperties = mock(CarlosProperties.class);
        when(carlosProperties.getProperty("MEASUREMENT_DS_DIRECTORY")).thenReturn(directory.toString());
        MockedStatic<CarlosProperties> properties = mockStatic(CarlosProperties.class);
        properties.when(CarlosProperties::getInstance).thenReturn(carlosProperties);
        return properties;
    }
}
