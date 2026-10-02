/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.commn.service;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import java.util.Date;
import java.util.List;
import java.util.TreeSet;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class FieldNoteManagerUnitTest extends CarlosUnitTestBase {
    private PropertyDao properties;
    private EFormDataDao data;
    private EFormValueDao values;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyDao.class);
        registerMock(PropertyDao.class, properties);
        data = mock(EFormDataDao.class);
        registerMock(EFormDataDao.class, data);
        values = mock(EFormValueDao.class);
        registerMock(EFormValueDao.class, values);
        registerMock(EFormDao.class, mock(EFormDao.class));
        registerMock(ProviderDataDao.class, mock(ProviderDataDao.class));
    }

    @Test
    void shouldReloadSelections_withoutRetainingRemovedIds() {
        Property property = new Property();
        property.setValue("1,2");
        when(properties.checkByName("fieldNoteEform")).thenReturn(property);
        var manager = new FieldNoteManager();
        assertThat(manager.getFieldNoteEforms()).containsExactly(1, 2);
        property.setValue("2");
        assertThat(manager.getFieldNoteEforms()).containsExactly(2);
        property.setValue("");
        assertThat(manager.getFieldNoteEforms()).isEmpty();
    }

    @Test
    void shouldKeepReportContents_separateBetweenRequests() {
        EFormData form = new EFormData();
        form.setId(42);
        form.setProviderNo("100");
        EFormValue resident = new EFormValue();
        resident.setFormDataId(42);
        resident.setVarName("residentId");
        resident.setVarValue("200");
        when(data.findByFidsAndDates(any(), any(), any())).thenReturn(List.of(form), List.of());
        when(values.findByFormDataIdList(List.of(42))).thenReturn(List.of(resident));
        var first = new FieldNoteManager();
        var second = new FieldNoteManager();
        first.getResidentNameList(new TreeSet<>(List.of(1)), new Date(0), new Date(1000));
        second.getResidentNameList(new TreeSet<>(List.of(2)), new Date(1000), new Date(2000));
        assertThat(first.getTotalNumberOfFieldNotes("200")).isEqualTo(1);
        assertThat(second.getTotalNumberOfFieldNotes("200")).isZero();
    }
}
