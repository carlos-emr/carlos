/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.decisionSupport.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import io.github.carlos_emr.carlos.commn.dao.PartialDateDao;
import io.github.carlos_emr.carlos.decisionSupport.model.DSDemographicAccess.Module;
import io.github.carlos_emr.carlos.dxresearch.bean.dxResearchBean;
import io.github.carlos_emr.carlos.test.logging.LogCapture;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * Guideline detail "actual values" for every decision-support module (#6328).
 *
 * <p>The readers are replaced with fixed values, so no database is touched; the tests pin which
 * module reads which source, that the modules without a displayable value read nothing and log
 * nothing at WARN or above, and that a module cannot be added without a display decision.</p>
 *
 * @since 2026-11-26
 */
@Tag("unit")
class DSDemographicDisplayValuesUnitTest extends CarlosUnitTestBase {

    /** Independently specified: the four modules whose patient values the detail view lists. */
    private static final Set<Module> DISPLAYED = EnumSet.of(Module.dxcodes, Module.drugs, Module.age, Module.sex);

    private static final Map<Module, String> READER_VALUES = Map.of(
            Module.dxcodes, "icd9:250,icd9:401",
            Module.drugs, "atc:N05BA01",
            Module.age, "30 y",
            Module.sex, "F");

    @BeforeEach
    void registerDiagnosisBeanDependency() {
        // dxResearchBean resolves this DAO in its static initializer.
        registerMock(PartialDateDao.class, mock(PartialDateDao.class));
    }

    /** Readers that return fixed values and record which one was asked. */
    private static final class RecordingAccess extends DSDemographicAccess {
        private final List<Module> reads = new ArrayList<>();
        private final boolean fail;

        RecordingAccess(boolean fail) {
            super(null, "0");
            this.fail = fail;
        }

        private String read(Module module) {
            reads.add(module);
            if (fail) {
                throw new IllegalStateException("synthetic reader failure");
            }
            return READER_VALUES.get(module);
        }

        @Override public String getDxCodesStr() { return read(Module.dxcodes); }
        @Override public String getRxCodesStr() { return read(Module.drugs); }
        @Override public String getAge() { return read(Module.age); }
        @Override public String getSex() { return read(Module.sex); }
    }

    private static List<LogEvent> warningsOrWorse(LogCapture capture) {
        return capture.events().stream().filter(event -> event.getLevel().isMoreSpecificThan(Level.WARN)).toList();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(Module.class)
    @DisplayName("should answer every module without an error log")
    void shouldAnswerEveryModule_withoutWarningOrError(Module module) {
        RecordingAccess access = new RecordingAccess(false);
        String values;
        try (LogCapture capture = LogCapture.forLogger(DSDemographicAccess.class)) {
            values = access.getDemogrpahicValues(module);
            assertThat(warningsOrWorse(capture)).as("log events for " + module).isEmpty();
        }

        assertThat(module.displaysPatientValues()).isEqualTo(DISPLAYED.contains(module));
        if (DISPLAYED.contains(module)) {
            assertThat(values).isEqualTo(READER_VALUES.get(module));
            assertThat(access.reads).containsExactly(module);
        } else {
            assertThat(values).isNull();
            assertThat(access.reads).as("patient data read for " + module).isEmpty();
        }
    }

    @Test
    @DisplayName("should keep the issue's five modules and notes out of the displayed set")
    void shouldMarkConditionArgumentModules_asNotDisplayed() {
        assertThat(EnumSet.complementOf(EnumSet.copyOf(DISPLAYED)))
                .containsExactlyInAnyOrder(Module.notes, Module.billedFor, Module.paid, Module.flowsheet,
                        Module.hasATCcode, Module.hasRxClass);
        for (Module module : Module.values()) {
            assertThat(module.displaysPatientValues()).as(module.name()).isEqualTo(DISPLAYED.contains(module));
        }
    }

    @Test
    @DisplayName("should return null without logging when the condition has no module")
    void shouldReturnNull_whenModuleIsNull() {
        RecordingAccess access = new RecordingAccess(false);
        try (LogCapture capture = LogCapture.forLogger(DSDemographicAccess.class)) {
            assertThat(access.getDemogrpahicValues(null)).isNull();
            assertThat(warningsOrWorse(capture)).isEmpty();
        }
        assertThat(access.reads).isEmpty();
    }

    @ParameterizedTest(name = "{0}")
    @EnumSource(value = Module.class, names = {"dxcodes", "drugs", "age", "sex"})
    @DisplayName("should still report a failed reader as an error")
    void shouldLogErrorAndReturnNull_whenReaderFails(Module module) {
        RecordingAccess access = new RecordingAccess(true);
        try (LogCapture capture = LogCapture.forLogger(DSDemographicAccess.class)) {
            assertThat(access.getDemogrpahicValues(module)).isNull();
            assertThat(capture.events()).anySatisfy(event -> {
                assertThat(event.getLevel()).isEqualTo(Level.ERROR);
                assertThat(event.getMessage().getFormattedMessage()).contains("module: '" + module + "'");
            });
        }
        assertThat(access.reads).containsExactly(module);
    }

    @Test
    @DisplayName("should keep an empty reader answer distinct from a missing one")
    void shouldReturnEmptyString_whenPatientHasNoValues() {
        DSDemographicAccess access = new DSDemographicAccess(null, "0") {
            @Override public String getDxCodesStr() { return ""; }
        };
        assertThat(access.getDemogrpahicValues(Module.dxcodes)).isEmpty();
    }

    @Test
    @DisplayName("should list only active diagnoses, the ones the condition evaluates")
    void shouldListOnlyActiveDiagnoses_forDxcodesValues() {
        List<dxResearchBean> registry = List.of(
                new dxResearchBean("FAKE active", "1", "2701", "2026-11-01", "2026-11-01", "A", "icd9", "999998"),
                new dxResearchBean("FAKE resolved", "2", "2700", "2026-11-01", "2026-11-01", "C", "icd9", "999998"),
                new dxResearchBean("FAKE active", "3", "E11", "2026-11-01", "2026-11-01", "A", "icd10", "999998"));
        DSDemographicAccess access = new DSDemographicAccess(null, "0") {
            @Override public List<dxResearchBean> getDxCodes() { return registry; }
        };

        assertThat(access.getDemogrpahicValues(Module.dxcodes)).isEqualTo("icd9:2701,icd10:E11");
    }

    @Test
    @DisplayName("should report no active diagnoses as an empty value")
    void shouldReturnEmptyDiagnoses_whenOnlyResolvedEntriesExist() {
        DSDemographicAccess access = new DSDemographicAccess(null, "0") {
            @Override public List<dxResearchBean> getDxCodes() {
                return List.of(new dxResearchBean("FAKE resolved", "2", "2700", "2026-11-01", "2026-11-01", "C", "icd9", "999998"));
            }
        };

        assertThat(access.getDemogrpahicValues(Module.dxcodes)).isEmpty();
    }

    @Test
    @DisplayName("should report a missing sex as an empty value, not a failed read")
    void shouldReturnEmptyString_whenSexIsMissing() {
        DSDemographicAccess access = new DSDemographicAccess(null, "0") {
            @Override public String getSex() { return null; }
        };

        assertThat(access.getDemogrpahicValues(Module.sex)).isEmpty();
    }
}
