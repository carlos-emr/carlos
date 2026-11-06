/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.form;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.commn.dao.DemographicExtDao;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Pins dispatch to the service proxy while retaining existing save/print navigation. */
@Tag("unit")
@Tag("form")
class Growth0To36FormAdapterUnitTest extends CarlosUnitTestBase {
    @BeforeEach
    void registerLegacyBaseDependencies() {
        createAndRegisterMock(DemographicManager.class);
        createAndRegisterMock(DemographicExtDao.class);
    }

    @Test
    void shouldDispatchLoadsAndSavesThroughTheService_whenUsingTheExistingFactoryName() throws Exception {
        Growth0To36FormService service = mock(Growth0To36FormService.class);
        registerMock(Growth0To36FormService.class, service);
        LoggedInInfo actor = mock(LoggedInInfo.class);
        Properties values = new Properties();
        values.setProperty("demographic_no", "123");
        values.setProperty("formId", "42");
        when(service.load(actor, 123, 42)).thenReturn(values);
        when(service.saveVersion(actor, 123, 42, values)).thenReturn(43);
        FrmRecord form = new FrmRecordFactory().factory("Growth0_36");
        assertThat(form).isInstanceOf(Growth0To36Form.class);
        assertThat(form.getFormRecord(actor, 123, 42)).isSameAs(values);
        assertThat(form.saveFormRecord(actor, values)).isEqualTo(43);
        assertThat(form.findActionValue("printall")).isEqualTo("printAll");
        assertThat(form.findActionValue(null)).isEqualTo("failure");
        assertThat(form.createActionURL("save", "save", "123", "43"))
                .isEqualTo("save?demographic_no=123&formId=43");
        assertThat(form.createActionURL("exit", "exit", "123", "43")).isEqualTo("exit");
        verify(service).load(actor, 123, 42);
        verify(service).saveVersion(actor, 123, 42, values);
    }

    @Test
    void shouldRefuseAnActorlessSave_withoutCallingTheService() {
        Growth0To36FormService service = mock(Growth0To36FormService.class);
        registerMock(Growth0To36FormService.class, service);
        FrmRecord form = new FrmRecordFactory().factory("Growth0_36");
        assertThatThrownBy(() -> form.saveFormRecord(new Properties())).isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(service);
    }

    @ParameterizedTest
    @NullAndEmptySource
    void shouldUseTheLegacyIdFallback_whenFormIdIsMissingOrEmpty(String source) throws Exception {
        Growth0To36FormService service = mock(Growth0To36FormService.class);
        registerMock(Growth0To36FormService.class, service);
        LoggedInInfo actor = mock(LoggedInInfo.class);
        Properties values = new Properties();
        values.setProperty("demographic_no", " 123 ");
        values.setProperty("ID", " 42 ");
        if (source != null) values.setProperty("formId", source);
        when(service.saveVersion(actor, 123, 42, values)).thenReturn(43);
        assertThat(new FrmRecordFactory().factory("Growth0_36").saveFormRecord(actor, values)).isEqualTo(43);
        verify(service).saveVersion(actor, 123, 42, values);
    }
}
