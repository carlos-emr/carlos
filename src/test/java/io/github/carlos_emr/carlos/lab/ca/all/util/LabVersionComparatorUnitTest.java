/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.lab.ca.all.util;

import io.github.carlos_emr.carlos.commn.dao.Hl7TextMessageDao;
import io.github.carlos_emr.carlos.commn.model.Hl7TextMessage;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.ExcellerisOntarioHandler;
import io.github.carlos_emr.carlos.lab.ca.all.parsers.Factory;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("lab")
@Isolated
class LabVersionComparatorUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldCompareBodyDigestsAndKeepFirstVersion_whenOnlyHeadersDiffer() {
        var dao = createAndRegisterMock(Hl7TextMessageDao.class);
        Object previous = ReflectionTestUtils.getField(LabVersionComparator.class, "hl7TextMessageDao");
        ReflectionTestUtils.setField(LabVersionComparator.class, "hl7TextMessageDao", dao);
        try (var factory = mockStatic(Factory.class)) {
            var first = mock(Hl7TextMessage.class); when(first.getId()).thenReturn(10);
            var second = mock(Hl7TextMessage.class); when(second.getId()).thenReturn(20);
            var third = mock(Hl7TextMessage.class); when(third.getId()).thenReturn(30);
            var a = mock(ExcellerisOntarioHandler.class); when(a.getHl7Body()).thenReturn("FAKE-header-A\nOBX|café|1.2");
            var b = mock(ExcellerisOntarioHandler.class); when(b.getHl7Body()).thenReturn("FAKE-header-B\nOBX|café|1.2");
            var c = mock(ExcellerisOntarioHandler.class); when(c.getHl7Body()).thenReturn("FAKE-header-C\nOBX|café|9.9");
            when(dao.findByIds(List.of(10, 20, 30))).thenReturn(List.of(first, second, third));
            factory.when(() -> Factory.getHandler(first)).thenReturn(a);
            factory.when(() -> Factory.getHandler(second)).thenReturn(b);
            factory.when(() -> Factory.getHandler(third)).thenReturn(c);
            var comparator = new LabVersionComparator(List.of("10", "20", "30"));
            assertThat(comparator.isLabDuplicate("10")).isNull();
            assertThat(comparator.isLabDuplicate("20")).isEqualTo("1");
            assertThat(comparator.isLabDuplicate("30")).isNull();
        } finally {
            ReflectionTestUtils.setField(LabVersionComparator.class, "hl7TextMessageDao", previous);
        }
    }
}
