/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.lab.ca.on;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import java.lang.reflect.Field;
import java.sql.Timestamp;
import java.util.List;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7OrcDao;
import io.github.carlos_emr.carlos.billing.CA.BC.dao.Hl7PidDao;
import io.github.carlos_emr.carlos.commn.dao.MdsMSHDao;
import io.github.carlos_emr.carlos.commn.model.MdsMSH;
import io.github.carlos_emr.carlos.lab.ca.bc.PathNet.PathnetResultsData;
import io.github.carlos_emr.carlos.mds.data.MDSResultsData;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

@Tag("unit")
class LegacyLabVersionLookupUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldUseRequestedMdsLabAndItsDate_notUninitializedListOrCandidateDate() {
        MdsMSHDao dao = mock(MdsMSHDao.class);
        registerMock(MdsMSHDao.class, dao);
        MDSResultsData data = mock(MDSResultsData.class, CALLS_REAL_METHODS);
        doReturn("ACCESSION").when(data).findMDSAccessionNumber("42");
        MdsMSH current = lab(42, "2026-09-01 12:00:00");
        MdsMSH oldAccessionReuse = lab(9, "2020-09-01 12:00:00");
        MdsMSH older = lab(41, "2026-08-31 12:00:00");
        when(dao.findLabsByAccessionNumAndId(42, "%ACCESSION%"))
                .thenReturn(List.of(new Object[]{oldAccessionReuse, current},
                        new Object[]{older, current}, new Object[]{current, current}));
        assertThat(data.getMatchingLabs("42")).isEqualTo("41,42");
        verify(dao).findLabsByAccessionNumAndId(42, "%ACCESSION%");
    }

    @Test
    void shouldNotQueryEveryMdsAccession_whenLookupIsBlank() {
        MdsMSHDao dao = mock(MdsMSHDao.class);
        registerMock(MdsMSHDao.class, dao);
        MDSResultsData data = mock(MDSResultsData.class, CALLS_REAL_METHODS);
        doReturn("").when(data).findMDSAccessionNumber("42");
        assertThat(data.getMatchingLabs("42")).isEqualTo("42");
        verifyNoInteractions(dao);
    }

    @Test
    void shouldUseRequestedPathnetLab_notEmptyDateAsId() throws Exception {
        Hl7OrcDao orcDao = mock(Hl7OrcDao.class);
        Hl7PidDao pidDao = mock(Hl7PidDao.class);
        registerMock(Hl7PidDao.class, pidDao);
        PathnetResultsData data = pathnet(orcDao);
        when(orcDao.findFillerAndStatusChageByMessageId(42))
                .thenReturn(List.<Object[]>of(new Object[]{"ACCESSION-1-1", Timestamp.valueOf("2026-09-01 12:00:00")}));
        when(pidDao.findByFillerOrderNumber("%ACCESSION%"))
                .thenReturn(List.of(new Object[]{41, Timestamp.valueOf("2026-08-31 12:00:00")},
                        new Object[]{42, Timestamp.valueOf("2026-09-01 12:00:00")},
                        new Object[]{9, Timestamp.valueOf("2020-09-01 12:00:00")}));
        assertThat(data.getMatchingLabs("42")).isEqualTo("41,42");
        verify(orcDao).findFillerAndStatusChageByMessageId(42);
    }

    @Test
    void shouldNotQueryEveryPathnetAccession_whenLabIsMissing() throws Exception {
        Hl7OrcDao orcDao = mock(Hl7OrcDao.class);
        Hl7PidDao pidDao = mock(Hl7PidDao.class);
        registerMock(Hl7PidDao.class, pidDao);
        assertThat(pathnet(orcDao).getMatchingLabs("42")).isEqualTo("42");
        verifyNoInteractions(pidDao);
    }

    private static PathnetResultsData pathnet(Hl7OrcDao dao) throws Exception {
        PathnetResultsData data = mock(PathnetResultsData.class, CALLS_REAL_METHODS);
        Field field = PathnetResultsData.class.getDeclaredField("hl7OrcDao");
        field.setAccessible(true);
        field.set(data, dao);
        Field logger = PathnetResultsData.class.getDeclaredField("logger");
        logger.setAccessible(true);
        logger.set(data, mock(org.apache.logging.log4j.Logger.class));
        return data;
    }

    private static MdsMSH lab(int id, String date) {
        MdsMSH lab = mock(MdsMSH.class);
        when(lab.getId()).thenReturn(id);
        when(lab.getDateTime()).thenReturn(Timestamp.valueOf(date));
        return lab;
    }
}
