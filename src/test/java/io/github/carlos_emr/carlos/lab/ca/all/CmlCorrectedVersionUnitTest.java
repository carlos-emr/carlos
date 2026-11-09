/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextInfoDao;
import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextInfo;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** CML completeness and clinical dates, including metadata written before the C-status fix.
 * @since 2026-11-09
 */
@Tag("unit")
@Isolated("Restores the cached HL7 DAOs after each test")
class CmlCorrectedVersionUnitTest extends CarlosUnitTestBase {
    private final Map<Field, Object> originalDaos = new LinkedHashMap<>();

    @BeforeEach
    void prepareDaos() throws Exception {
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                createAndRegisterMock(field.getType());
            }
        }
        for (Field field : Hl7textResultsData.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && field.getName().endsWith("Dao")) {
                field.setAccessible(true);
                originalDaos.put(field, field.get(null));
                field.set(null, mockedBeans.get(field.getType()));
            }
        }
    }

    @AfterEach
    void restoreDaos() throws Exception {
        for (var entry : originalDaos.entrySet()) entry.getKey().set(null, entry.getValue());
    }

    @ParameterizedTest
    @CsvSource({"F,1", "C,1", "c,1", "I,0", "P,0"})
    void shouldCountCorrectedObservationsAsComplete_withoutChangingTheirStatus(String status, int count) throws Exception {
        CMLHandler handler = source(status, "20260310120000");
        assertThat(handler.getOBXFinalResultCount()).isEqualTo(count);
        if ("C".equalsIgnoreCase(status)) assertThat(handler.getOBXResultStatus(0, 0)).isEqualTo(status);
    }

    @ParameterizedTest
    @CsvSource({"11,false,C", "12,false,C", "13,false,C", "12,true,C", "12,true,c"})
    void shouldOrderTheCorrectionAfterOlderFinals_whenItsStoredCountPredatesTheFix(
            int selected, boolean sameTimeFinal, String correctionStatus) throws Exception {
        Hl7TextInfo original = info(11, "2026-03-10 08:00:00", 1);
        Hl7TextInfo correction = info(12, sameTimeFinal ? "2026-03-10 08:00:00" : "2026-03-10 12:00:00", 0);
        Hl7TextInfo older = info(13, sameTimeFinal ? "2026-03-10 08:00:00" : "2026-03-10 07:00:00", 1);
        Hl7TextInfo otherVendor = info(99, "2026-03-10 13:00:00", 9);
        var selectedInfo = Map.of(11, original, 12, correction, 13, older).get(selected);
        Hl7TextInfoDao infos = (Hl7TextInfoDao) mockedBeans.get(Hl7TextInfoDao.class);
        when(infos.findByLabIdViaMagic(selected)).thenReturn(List.of(new Object[]{correction, selectedInfo},
                new Object[]{older, selectedInfo}, new Object[]{original, selectedInfo},
                new Object[]{correction, selectedInfo},
                new Object[]{otherVendor, selectedInfo}));
        Hl7TextMessageDao messages = (Hl7TextMessageDao) mockedBeans.get(Hl7TextMessageDao.class);
        try (var parsers = mockStatic(Factory.class)) {
            Hl7TextMessage foreign = new Hl7TextMessage();
            org.springframework.test.util.ReflectionTestUtils.setField(foreign, "id", 99);
            foreign.setType("PATHL7");
            when(messages.find(99)).thenReturn(foreign);
            for (int id : List.of(11, 12, 13)) {
                Hl7TextMessage message = new Hl7TextMessage();
                org.springframework.test.util.ReflectionTestUtils.setField(message, "id", id);
                message.setType("CML");
                when(messages.find(id)).thenReturn(message);
                String observed = Map.of(11, original, 12, correction, 13, older).get(id)
                        .getObrDate().replaceAll("[- :]", "");
                CMLHandler handler = source(id == 12 ? correctionStatus : "F", observed);
                parsers.when(() -> Factory.getHandler(message)).thenReturn(handler);
            }
            assertThat(Hl7textResultsData.getMatchingLabs(Integer.toString(selected)))
                    .isEqualTo(sameTimeFinal ? "11,13,12" : "13,11,12");
            parsers.verify(() -> Factory.getHandler(foreign), never());
        }
        assertThat(correction.getFinalResultCount()).isZero(); // A read does not rewrite historical metadata.
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void shouldPreserveLegacyVersionOrder_forNonCmlSources(boolean reversed) {
        var selected = new Hl7TextMessage();
        selected.setType("GDML");
        var messages = (Hl7TextMessageDao) mockedBeans.get(Hl7TextMessageDao.class);
        when(messages.find(12)).thenReturn(selected);
        Hl7TextInfo older = info(11, "2026-03-10 08:00:00", 1);
        Hl7TextInfo current = info(12, "2026-03-10 12:00:00", 1);
        var infos = (Hl7TextInfoDao) mockedBeans.get(Hl7TextInfoDao.class);
        when(infos.findByLabIdViaMagic(12)).thenReturn(reversed
                ? List.of(new Object[]{current, current}, new Object[]{older, current})
                : List.of(new Object[]{older, current}, new Object[]{current, current}));
        try (var parsers = mockStatic(Factory.class)) {
            assertThat(Hl7textResultsData.getMatchingLabs("12")).isEqualTo(reversed ? "12,11" : "11,12");
            parsers.verifyNoInteractions();
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldRefuseUnresolvableCmlVersions_withoutUsingStaleCompleteness(boolean missing) {
        Hl7TextMessage selected = new Hl7TextMessage();
        org.springframework.test.util.ReflectionTestUtils.setField(selected, "id", 12);
        selected.setType("CML");
        Hl7TextMessage previous = new Hl7TextMessage();
        org.springframework.test.util.ReflectionTestUtils.setField(previous, "id", 11);
        previous.setType("CML");
        Hl7TextInfo selectedInfo = info(12, "2026-03-10 12:00:00", 0);
        Hl7TextInfo oldInfo = info(11, "2026-03-10 08:00:00", 1);
        var messages = (Hl7TextMessageDao) mockedBeans.get(Hl7TextMessageDao.class);
        when(messages.find(12)).thenReturn(selected);
        when(messages.find(11)).thenReturn(missing ? null : previous);
        var infos = (Hl7TextInfoDao) mockedBeans.get(Hl7TextInfoDao.class);
        when(infos.findByLabIdViaMagic(12)).thenReturn(List.of(new Object[]{oldInfo, selectedInfo},
                new Object[]{selectedInfo, selectedInfo}));
        try (var parsers = mockStatic(Factory.class)) {
            assertThatThrownBy(() -> Hl7textResultsData.getMatchingLabs("12"))
                    .isInstanceOf(Hl7textResultsData.IncompleteCmlVersionChainException.class)
                    .hasMessageContaining(missing ? "no stored source" : "cannot be parsed");
            assertThatThrownBy(() -> Hl7textResultsData.populateMeasurementsTable("12", "101"))
                    .isInstanceOf(Hl7textResultsData.IncompleteCmlVersionChainException.class);
            verifyNoInteractions(mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementDao.class),
                    mockedBeans.get(io.github.carlos_emr.carlos.commn.dao.MeasurementsDeletedDao.class));
            parsers.verify(() -> Factory.getHandler(previous), times(missing ? 0 : 2));
        }
    }

    private static Hl7TextInfo info(int id, String date, int count) {
        Hl7TextInfo info = new Hl7TextInfo();
        info.setLabNumber(id);
        info.setObrDate(date);
        info.setFinalResultCount(count);
        return info;
    }

    private static CMLHandler source(String status, String time) throws Exception {
        String[] obr = new String[26], obx = new String[15];
        Arrays.fill(obr, ""); Arrays.fill(obx, "");
        obr[0] = "OBR"; obr[1] = "1"; obr[2] = "SYNTHETIC-CML"; obr[4] = "ML70^SYNTHETIC PANEL";
        obr[7] = time; obr[22] = time; obr[25] = status;
        obx[0] = "OBX"; obx[1] = "1"; obx[2] = "NM"; obx[3] = "7010^SYNTHETIC RESULT";
        obx[5] = "12.7"; obx[6] = "mmol/L"; obx[8] = "H"; obx[11] = status; obx[14] = time;
        CMLHandler handler = new CMLHandler();
        handler.init("MSH|^~\\&|Reports|CML|||" + time + "||ORU^R01|SYNTHETIC|P|2.3\r"
                + "PID|1||||Synthetic^Workflow||19800102|F\rORC|RE|SYNTHETIC-CML\r"
                + String.join("|", obr) + "\r" + String.join("|", obx) + "\r");
        return handler;
    }
}
