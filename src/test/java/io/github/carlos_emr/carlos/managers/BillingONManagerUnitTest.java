/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.managers;

import java.util.Locale;

import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("BillingONManager")
@Tag("unit")
@Tag("billing")
class BillingONManagerUnitTest extends CarlosUnitTestBase {

    private BillingONCHeader1Dao billingONCHeader1Dao;
    private BillingONManager manager;

    @BeforeEach
    void setUpBillingManager() {
        billingONCHeader1Dao = mock(BillingONCHeader1Dao.class);
        manager = new BillingONManager();
        injectDependency(manager, "billingONCHeader1Dao", billingONCHeader1Dao);
    }

    @Test
    void shouldRecordPrintedComment_whenExistingCommentIsNull() {
        BillingONCHeader1 header = new BillingONCHeader1();
        header.setComment(null);
        when(billingONCHeader1Dao.find(any())).thenReturn(header);

        manager.addPrintedBillingComment(42, Locale.CANADA);

        assertThat(header.getComment()).isNotBlank();
        assertThat(header.getComment()).doesNotStartWith("\n");
        verify(billingONCHeader1Dao).merge(header);
    }
}
