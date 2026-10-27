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
package io.github.carlos_emr.carlos.rx;

import io.github.carlos_emr.carlos.commn.dao.DrugDao;
import io.github.carlos_emr.carlos.commn.model.Drug;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class StaticScriptBeanClinicalSafetyUnitTest {
    @Test
    void nullDatesSortDeterministicallyWithoutFabricatingTiming() {
        var unknown = new StaticScriptBean.DrugDisplayData();
        unknown.localDrugId = 7;
        var exact = new StaticScriptBean.DrugDisplayData();
        exact.localDrugId = 6;
        exact.dateStartDate = new Date(0);
        var anotherUnknown = new StaticScriptBean.DrugDisplayData();
        anotherUnknown.localDrugId = 8;
        var rows = new ArrayList<>(List.of(unknown, anotherUnknown, exact));
        rows.sort(StaticScriptBean.DrugDisplayData.DATE_COMPARATOR);
        assertEquals(List.of(exact, unknown, anotherUnknown), rows);
        assertEquals(0, StaticScriptBean.DrugDisplayData.DATE_COMPARATOR.compare(exact, exact));
        assertNull(unknown.dateStartDate);
    }

    @Test
    void importedViewKeepsSourcePrescriberUnknownDatesAndUnknownAuthorityDistinct() {
        try (var spring = mockStatic(SpringUtils.class)) {
            spring.when(() -> SpringUtils.getBean(DrugDao.class)).thenReturn(mock(DrugDao.class));
            Drug source = new Drug();
            source.setId(7);
            source.setCdsCurrentness("SUSPENDED");
            source.setImportedPrescriberNo(null);
            source.setOutsideProviderName("External source clinician");
            source.setRxDate(null);
            source.setWrittenDate(null);
            source.setEndDate(null);
            source.setSpecial("1/2 tablet orally");
            source.setNonAuthoritative(null);
            source.setNoSubs((Boolean) null);
            StaticScriptBean.DrugDisplayData view = ReflectionTestUtils.invokeMethod(
                    StaticScriptBean.class, "getDrugDisplayData", source);
            assertNotNull(view);
            assertEquals("External source clinician", view.providerName);
            assertEquals("Unknown", view.startDate);
            assertEquals("Unknown", view.endDate);
            assertEquals("Unknown", view.writtenDate);
            assertEquals("SUSPENDED", view.cdsCurrentness);
            assertNull(view.cdsNonAuthoritative);
            assertNull(view.cdsSubstitutionNotAllowed);
            assertTrue(view.prescriptionDetails.contains("1/2"));
        }
    }
}
