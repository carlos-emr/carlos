/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements;

import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScopedFlowSheetCustomizationUnitTest extends CarlosUnitTestBase {
    private MeasurementTemplateFlowSheetConfig config;
    private MeasurementFlowSheet builtIn;
    private MeasurementFlowSheet scoped;

    @BeforeEach
    void prepare() throws Exception {
        config = mock(MeasurementTemplateFlowSheetConfig.class, CALLS_REAL_METHODS);
        builtIn = sheet("BP");
        scoped = sheet("A1C", "HT");
        doReturn(builtIn).when(config).getFlowSheet("tracker");
        doReturn(scoped).when(config).getFlowSheet("tracker", "42", 101);
        doReturn(builtIn).when(config).getFlowSheet("tracker", "42", 202);
        // Isolate the existing XML-copy machinery; exercise real customization operations
        // and assert that the selected source definition remains unchanged.
        doAnswer(call -> {
            MeasurementFlowSheet source = call.getArgument(0);
            MeasurementFlowSheet copy = sheet();
            for (String type : source.getMeasurementList()) {
                copy.addFlowSheetItem(copy.getMeasurementList().size(),
                        new FlowSheetItem(new HashMap<>(source.getMeasurementFlowSheetInfo(type))));
            }
            return copy;
        }).when(config).makeNewFlowsheet(any(MeasurementFlowSheet.class));
    }

    @Test
    void shouldRetainScopedDefinition_whenAddingPatientItem() {
        MeasurementFlowSheet result = config.getFlowSheet("tracker", "42", 101,
                List.of(change(FlowSheetCustomization.ADD, null,
                        "<item measurement_type=\"WT\" display_name=\"Patient weight\"/>")));

        assertEquals(List.of("A1C", "HT", "WT"), result.getVisibleMeasurementList());
        assertEquals("Patient weight", result.getMeasurementFlowSheetInfo("WT").get("display_name"));
        assertEquals(List.of("A1C", "HT"), scoped.getVisibleMeasurementList());
        assertEquals(List.of("BP"), builtIn.getVisibleMeasurementList());
    }

    @Test
    void shouldApplyUpdatesAndHideRemovedItems_withoutChangingSharedDefinition() {
        MeasurementFlowSheet result = config.getFlowSheet("tracker", "42", 101, List.of(
                change(FlowSheetCustomization.UPDATE, "A1C",
                        "<item measurement_type=\"A1C\" display_name=\"Patient A1C\"/>"),
                change(FlowSheetCustomization.DELETE, "HT", null)));

        assertEquals(List.of("A1C"), result.getVisibleMeasurementList());
        assertEquals("Patient A1C", result.getMeasurementFlowSheetInfo("A1C").get("display_name"));
        assertEquals(List.of("A1C", "HT"), scoped.getVisibleMeasurementList());
        assertEquals("A1C", scoped.getMeasurementFlowSheetInfo("A1C").get("display_name"));
    }

    @Test
    void shouldRetainScopedDefinition_whenNoItemCustomizationsExist() {
        assertSame(scoped, config.getFlowSheet("tracker", "42", 101, List.of()));
    }

    @Test
    void shouldRetainScopedDefinition_whenCopyingFails() throws Exception {
        doThrow(new IllegalStateException("copy unavailable")).when(config).makeNewFlowsheet(scoped);

        MeasurementFlowSheet result = config.getFlowSheet("tracker", "42", 101,
                List.of(change(FlowSheetCustomization.DELETE, "HT", null)));

        assertSame(scoped, result);
        assertEquals(List.of("A1C", "HT"), result.getVisibleMeasurementList());
    }

    @Test
    void shouldKeepOtherPatientDefinitionFreeOfFirstPatientItems() {
        config.getFlowSheet("tracker", "42", 101, List.of(change(FlowSheetCustomization.ADD, null,
                "<item measurement_type=\"WT\" display_name=\"Only patient 101\"/>")));

        MeasurementFlowSheet otherPatient = config.getFlowSheet("tracker", "42", 202, List.of());

        assertSame(builtIn, otherPatient);
        assertEquals(List.of("BP"), otherPatient.getVisibleMeasurementList());
        assertFalse(otherPatient.getMeasurementList().contains("WT"));
    }

    @Test
    void shouldPreserveBuiltInCustomizationOverload() {
        MeasurementFlowSheet result = config.getFlowSheet("tracker",
                List.of(change(FlowSheetCustomization.ADD, null,
                        "<item measurement_type=\"WT\" display_name=\"Weight\"/>")));

        assertEquals(List.of("BP", "WT"), result.getVisibleMeasurementList());
        assertEquals(List.of("BP"), builtIn.getVisibleMeasurementList());
    }

    private static MeasurementFlowSheet sheet(String... types) {
        MeasurementFlowSheet result = new MeasurementFlowSheet();
        result.setName("tracker");
        for (String type : types) {
            result.addFlowSheetItem(result.getMeasurementList().size(),
                    new FlowSheetItem(Map.of("measurement_type", type, "display_name", type)));
        }
        return result;
    }

    private static FlowSheetCustomization change(String action, String measurement, String payload) {
        FlowSheetCustomization result = new FlowSheetCustomization();
        result.setAction(action);
        result.setMeasurement(measurement);
        result.setPayload(payload);
        return result;
    }
}
