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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.commn.dao.CdsImportPreparationDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportPreparation;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.SimpleTransactionStatus;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@Tag("unit")
class CdsNativePreparationServiceUnitTest {
    public record Source(String text, Map<String, String> values) { }

    @Test void shouldPersistAndReuseCanonicalLargeDto_whenMapOrderChangesAfterRestart() throws Exception {
        var dao = mock(CdsImportPreparationDao.class);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(call -> { CdsImportPreparation row = call.getArgument(0); rows.put(row.getId(), row); return null; }).when(dao).persist(any());
        var service = service(dao);
        Map<String, String> first = new LinkedHashMap<>(); first.put("b", "two"); first.put("a", "one");
        Map<String, String> second = new LinkedHashMap<>(); second.put("a", "one"); second.put("b", "two");
        service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), new Source("x".repeat(100000), first));
        assertThatCode(() -> service.store("run", 1, "a".repeat(64), "b".repeat(64), command(),
                new Source("x".repeat(100000), second))).doesNotThrowAnyException();
        verify(dao, times(1)).persist(any());
        assertThat(rows.values().iterator().next().getPayload()).contains("x".repeat(100000));
    }

    @Test void shouldRegenerateOnlyIdenticalUncommittedCache_whenStoredPayloadIsCorrupt() throws Exception {
        var dao = mock(CdsImportPreparationDao.class);
        var ledger = mock(CdsImportLedgerService.class);
        var unit = new io.github.carlos_emr.carlos.commn.model.CdsImportUnit(); unit.setPatientKey("patient");
        when(ledger.unit("run", "a".repeat(64))).thenReturn(unit);
        Map<String, CdsImportPreparation> rows = new HashMap<>();
        when(dao.get(anyString())).thenAnswer(call -> rows.get(call.getArgument(0)));
        doAnswer(call -> { CdsImportPreparation row = call.getArgument(0); rows.put(row.getId(), row); return null; }).when(dao).persist(any());
        var service = service(dao, ledger);
        var source = new Source("complete Unicode 😀", Map.of());
        service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
        var row = rows.values().iterator().next(); String expected = row.getPayload(); row.setPayload("corrupt");
        service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), source);
        assertThat(row.getPayload()).isEqualTo(expected);
        verify(ledger).requireExpectedCheckpointFree("run", 1, "a".repeat(64));
        verify(ledger).diagnostic(eq("run"), eq(1L), eq("patient"), eq("a".repeat(64)), eq("INFO"),
                eq("NATIVE_PREPARATION_CACHE_REGENERATED"), eq("RECOVERY"), isNull(), anyString(), isNull(), eq(false));
        row.setOptionsDigest("c".repeat(64)); row.setPayload("corrupt again");
        assertThatThrownBy(() -> service.store("run", 1, "a".repeat(64), "b".repeat(64), command(), source))
                .isInstanceOf(CdsImportException.class).hasMessageContaining("NATIVE_PREPARATION_CONFLICT");
        assertThat(row.getPayload()).isEqualTo("corrupt again");
    }

    @Test void shouldRefuseOversizedPreparation_beforeOpeningMetadataTransaction() {
        var dao = mock(CdsImportPreparationDao.class);
        var service = service(dao);
        assertThatThrownBy(() -> service.store("run", 1, "a".repeat(64), "b".repeat(64), command(),
                new Source("x".repeat(1100000), Map.of()))).isInstanceOf(Exception.class)
                .hasStackTraceContaining("NATIVE_PREPARATION_CAPACITY_EXCEEDED");
        verifyNoInteractions(dao);
    }

    private static CdsNativePreparationService service(CdsImportPreparationDao dao) {
        return service(dao, mock(CdsImportLedgerService.class));
    }
    private static CdsNativePreparationService service(CdsImportPreparationDao dao, CdsImportLedgerService ledger) {
        var manager = mock(PlatformTransactionManager.class);
        when(manager.getTransaction(any())).thenReturn(new SimpleTransactionStatus(true));
        return new CdsNativePreparationService(ledger, dao, manager,
                new CdsImportConfigurationService(new Properties()));
    }
    private static CdsImportCommand command() {
        return new CdsImportCommand(Path.of("/source/one.xml"), "request", "1", null, null, "test", "omd-5.2",
                false, false, false, 0, null, false);
    }
}
