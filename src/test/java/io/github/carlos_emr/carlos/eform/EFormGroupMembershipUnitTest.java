/*
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.eform;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import io.github.carlos_emr.carlos.commn.dao.EFormGroupDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
class EFormGroupMembershipUnitTest extends CarlosUnitTestBase {
    private EFormGroupDao groups;

    @BeforeEach
    void registerGroups() {
        groups = createAndRegisterMock(EFormGroupDao.class);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "not-a-form", "2147483648", "-2147483649"})
    void shouldRejectInvalidFormIdWithoutWritingGroupMarker(String formId) {
        assertThatThrownBy(() -> EFormUtil.addEFormToGroup("FAKE group", formId))
                .isInstanceOf(NumberFormatException.class);
        verifyNoInteractions(groups);
    }

    @Test
    void shouldKeepExplicitZeroMarker_whenCreatingGroup() {
        EFormUtil.addEFormToGroup("FAKE group", "0");
        verify(groups).addActiveFormIfMissing("FAKE group", 0);
    }

    @Test
    void shouldAddRequestedForm_whenFormIdIsValid() {
        EFormUtil.addEFormToGroup("FAKE group", "42");
        verify(groups).addActiveFormIfMissing("FAKE group", 42);
    }
}
