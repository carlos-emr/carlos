/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.CLINIC;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.FIRST;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.SECOND;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.catalogue;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import jakarta.persistence.EntityManager;
import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.TransactionSystemException;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Reuses the existing unit transaction fixture; no new lifecycle engine or actual database. */
class PortalMyCarlosDeviceApprovalTransactionsUnitTest {
    private static final int INVENTORY = PortalMyCarlosKeyInventoryService.COORDINATION_KEY;
    private static final int APPROVAL = PortalMyCarlosDeviceApprovalTransactions.COORDINATION_KEY;
    private PropertyDao properties;
    private ProviderLabRoutingDao coordination;
    private PortalMyCarlosInventoryTestSupport.Fixture fixture;
    private PortalMyCarlosKeyInventoryWorker worker;
    private PortalMyCarlosDeviceApprovalTransactions approval;
    private List<Integer> locks;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyDao.class);
        coordination = mock(ProviderLabRoutingDao.class);
        fixture = PortalMyCarlosInventoryTestSupport.fixture(properties, coordination);
        worker = new PortalMyCarlosKeyInventoryWorker(properties);
        approval = new PortalMyCarlosDeviceApprovalTransactions(fixture.transactions, worker);
        locks = new ArrayList<>();
        doAnswer(call -> {
            locks.add(call.getArgument(0));
            return null;
        }).when(coordination).lockRoutingReport(anyInt());
    }

    private <T> T owner(int variant, Supplier<T> operation) {
        return switch (variant) {
            case 0 -> fixture.transactions.execute(operation);
            case 1 -> fixture.transactions.executeSigningApproval(operation);
            case 2 -> fixture.transactions.executeApprovalOnly(operation);
            default -> throw new IllegalArgumentException("unknown test variant");
        };
    }

    private static List<Integer> expectedLocks(int variant) {
        return switch (variant) {
            case 0 -> List.of(INVENTORY);
            case 1 -> List.of(INVENTORY, APPROVAL);
            case 2 -> List.of(APPROVAL);
            default -> throw new IllegalArgumentException("unknown test variant");
        };
    }

    private static RuntimeException conflict(int code, String state) {
        return new IllegalStateException("FAKE rollback conflict", new SQLException("FAKE", state, code));
    }

    private void assertClean() {
        assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
        assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
        assertFalse(TransactionSynchronizationManager.hasResource(fixture.factory));
        assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireOwnedTransaction);
        assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireApprovalOwnedTransaction);
    }

    private static Property row(PortalMyCarlosKeyInventory catalogue) throws Exception {
        var json = new ObjectMapper();
        var root = json.createObjectNode();
        root.put("schema_version", 1);
        root.set("catalogue", json.readTree(catalogue.toStoredJson()));
        root.putNull("publication");
        Property row = new Property(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        row.setValue(root.toString());
        return row;
    }

    @Test
    void inventoryOwnerTakesOnlyPlusOneAndRefusesApprovalAuthority() {
        assertEquals("FAKE inventory", fixture.transactions.execute(() -> {
            assertEquals(List.of(INVENTORY), locks);
            PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
            assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireApprovalOwnedTransaction);
            return "FAKE inventory";
        }));
        assertEquals(1, fixture.manager.begins);
        assertEquals(1, fixture.manager.commits);
        verifyNoInteractions(properties);
        assertClean();
    }

    @Test
    void combinedBridgeLocksPlusOneThenPlusTwoBeforeCurrentReadAndCallback() throws Exception {
        var current = catalogue();
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenAnswer(call -> {
            assertEquals(List.of(INVENTORY, APPROVAL), locks);
            PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
            PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
            assertEquals(0, fixture.manager.commits);
            return List.of(row(current));
        });
        var result = approval.withSigningInventory(value -> {
            assertEquals(current.toStoredJson(), value.orElseThrow().toStoredJson());
            assertEquals(0, fixture.manager.commits);
            var holder = (EntityManagerHolder) TransactionSynchronizationManager.getResource(fixture.factory);
            assertSame(fixture.manager.entityManagers.getFirst(), holder.getEntityManager());
            return value.orElseThrow();
        });
        assertEquals(current.toStoredJson(), result.toStoredJson());
        assertEquals(1, fixture.manager.begins);
        assertEquals(1, fixture.manager.commits);
        verify(properties, never()).findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        assertClean();
    }

    @Test
    void pureApprovalBridgeTakesOnlyPlusTwoAndNeverReadsInventory() {
        assertEquals("FAKE approval", approval.withApprovalState(() -> {
            assertEquals(List.of(APPROVAL), locks);
            PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
            assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireOwnedTransaction);
            return "FAKE approval";
        }));
        verifyNoInteractions(properties);
        assertEquals(1, fixture.manager.commits);
        assertClean();
    }

    @Test
    void bothGuardsStayClosedUntilEverySelectedLockHasReturned() {
        for (int variant = 0; variant < 3; variant++) {
            setUp();
            doAnswer(call -> {
                locks.add(call.getArgument(0));
                assertTrue(TransactionSynchronizationManager.isActualTransactionActive());
                assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireOwnedTransaction);
                assertThrows(IllegalStateException.class, PortalMyCarlosInventoryTransactions::requireApprovalOwnedTransaction);
                assertThrows(IllegalStateException.class, worker::currentForUpdate);
                verifyNoInteractions(properties);
                return null;
            }).when(coordination).lockRoutingReport(anyInt());
            int selected = variant;
            owner(selected, () -> {
                assertEquals(expectedLocks(selected), locks);
                if (selected != 2) {
                    PortalMyCarlosInventoryTransactions.requireOwnedTransaction();
                }
                if (selected != 0) {
                    PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
                }
                return null;
            });
            assertClean();
        }
    }

    @Test
    void approvalOnlyCannotCurrentReadOrMutateInventoryBeforePropertyAccess() {
        AtomicInteger preparation = new AtomicInteger();
        approval.withApprovalState(() -> {
            assertThrows(IllegalStateException.class, worker::currentForUpdate);
            assertThrows(IllegalStateException.class, () -> worker.provision(CLINIC, 0, List.of(), (before, after) -> {}));
            assertThrows(IllegalStateException.class, () -> worker.provisionPrepared(CLINIC, 0, before -> {
                preparation.incrementAndGet();
                return List.of();
            }, (before, after) -> {}));
            assertThrows(IllegalStateException.class, () -> worker.claimCurrent(() -> null, "FAKE token",
                    Instant::now, Duration.ofSeconds(30)));
            assertThrows(IllegalStateException.class, () -> worker.acknowledgeCurrent(null, () -> null, null, Instant::now));
            assertThrows(IllegalStateException.class,
                    () -> worker.failedCurrent(null, () -> null, Instant::now, Duration.ofSeconds(30)));
            return null;
        });
        assertEquals(0, preparation.get());
        assertEquals(List.of(APPROVAL), locks);
        verifyNoInteractions(properties);
        assertClean();
    }

    @Test
    void approvalOnlyCannotUpgradeOrStartAnotherNestedOwner() {
        AtomicInteger effects = new AtomicInteger();
        approval.withApprovalState(() -> {
            assertThrows(IllegalStateException.class, () -> fixture.transactions.execute(effects::incrementAndGet));
            assertThrows(IllegalStateException.class, () -> fixture.transactions.executeSigningApproval(effects::incrementAndGet));
            assertThrows(IllegalStateException.class, () -> approval.withSigningInventory(value -> effects.incrementAndGet()));
            assertThrows(IllegalStateException.class, () -> approval.withApprovalState(effects::incrementAndGet));
            assertThrows(IllegalStateException.class,
                    () -> fixture.service.withCurrentInventory(value -> effects.incrementAndGet()));
            return null;
        });
        assertEquals(0, effects.get());
        assertEquals(1, fixture.manager.begins);
        assertEquals(List.of(APPROVAL), locks);
        verifyNoInteractions(properties);
        assertClean();
    }

    @Test
    void inventoryOnlyCannotAddApprovalScopeAfterStarting() {
        fixture.transactions.execute(() -> {
            assertThrows(IllegalStateException.class, () -> approval.withApprovalState(() -> "FAKE"));
            assertThrows(IllegalStateException.class, () -> approval.withSigningInventory(value -> "FAKE"));
            return null;
        });
        assertEquals(List.of(INVENTORY), locks);
        assertEquals(1, fixture.manager.begins);
        verifyNoInteractions(properties);
        assertClean();
    }

    @Test
    void currentReadRequiresOwnerEvenWhenNoCallerTransactionExists() {
        assertThrows(IllegalStateException.class, worker::currentForUpdate);
        verifyNoInteractions(properties, coordination);
        assertEquals(0, fixture.manager.begins);
        assertClean();
    }

    @Test
    void currentReadRejectsAnInheritedTransactionWithoutOurOwnerMarker() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThrows(IllegalStateException.class, worker::currentForUpdate);
            verifyNoInteractions(properties, coordination);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
        assertClean();
    }

    @Test
    void currentReadCapabilityDoesNotEscapeTheCompletedTransaction() {
        Supplier<Optional<PortalMyCarlosKeyInventory>> captured = approval.withSigningInventory(value -> worker::currentForUpdate);
        assertThrows(IllegalStateException.class, captured::get);
        verify(properties, times(1)).findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        assertClean();
    }

    @Test
    void currentReadUsesTheLockingRowAndNeverTheStaleSnapshotRow() throws Exception {
        var old = catalogue();
        var current = old.replaceComplete(1, List.of(FIRST, SECOND));
        when(properties.findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenReturn(List.of(row(old)));
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenReturn(List.of(row(current)));
        var result = approval.withSigningInventory(value -> value.orElseThrow());
        assertEquals(2, result.generation());
        assertEquals(current.contentDigest(), result.contentDigest());
        verify(properties, never()).findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        assertEquals(List.of(INVENTORY, APPROVAL), locks);
        assertClean();
    }

    @Test
    void missingCatalogueStaysExplicitWithoutCreatingOrInferringOne() {
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenReturn(List.of());
        assertEquals(Optional.empty(), approval.withSigningInventory(value -> value));
        verify(properties, times(1)).findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        assertEquals(1, fixture.manager.commits);
        assertClean();
    }

    @Test
    void inventoryOwnerMayUseCurrentReadWithoutGainingApprovalAuthority() {
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenReturn(List.of());
        assertEquals(Optional.empty(), fixture.transactions.execute(worker::currentForUpdate));
        assertEquals(List.of(INVENTORY), locks);
        assertClean();
    }

    @Test
    void everyScopeRetriesOnlyAfterRollbackWithAFreshEntityManagerAndLockSequence() {
        for (int variant = 0; variant < 3; variant++) {
            setUp();
            AtomicInteger attempts = new AtomicInteger();
            owner(variant, () -> {
                if (attempts.incrementAndGet() == 1) {
                    throw conflict(1020, "HY000");
                }
                assertEquals(1, fixture.manager.rollbacks);
                assertEquals(2, fixture.manager.entityManagers.size());
                assertNotSame(fixture.manager.entityManagers.get(0), fixture.manager.entityManagers.get(1));
                return null;
            });
            List<Integer> expected = new ArrayList<>(expectedLocks(variant));
            expected.addAll(expectedLocks(variant));
            assertEquals(expected, locks);
            assertEquals(2, attempts.get());
            assertEquals(1, fixture.manager.commits);
            for (EntityManager manager : fixture.manager.entityManagers) {
                verify(manager, times(1)).close();
            }
            assertClean();
        }
    }

    @Test
    void failedSecondLockRollsBackFirstAndReacquiresBothBeforeTheOnlyCallback() {
        AtomicInteger secondLocks = new AtomicInteger();
        AtomicInteger callbacks = new AtomicInteger();
        doAnswer(call -> {
            int key = call.getArgument(0);
            locks.add(key);
            if (key == APPROVAL && secondLocks.incrementAndGet() == 1) {
                throw conflict(1213, "40001");
            }
            return null;
        }).when(coordination).lockRoutingReport(anyInt());
        fixture.transactions.executeSigningApproval(callbacks::incrementAndGet);
        assertEquals(List.of(INVENTORY, APPROVAL, INVENTORY, APPROVAL), locks);
        assertEquals(1, callbacks.get());
        assertEquals(1, fixture.manager.rollbacks);
        assertEquals(1, fixture.manager.commits);
        assertNotSame(fixture.manager.entityManagers.get(0), fixture.manager.entityManagers.get(1));
        assertClean();
    }

    @Test
    void newScopesStopAfterThreeConfirmedRollbackConflicts() {
        for (int variant : new int[] {1, 2}) {
            setUp();
            AtomicInteger calls = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> owner(variant, () -> {
                calls.incrementAndGet();
                throw conflict(1205, "HY000");
            }));
            assertEquals(3, calls.get());
            assertEquals(3, fixture.manager.rollbacks);
            assertEquals(0, fixture.manager.commits);
            assertClean();
        }
    }

    @Test
    void newScopesNeverRetryBusinessRefusalsOrUnrecognizedVendorStates() {
        for (int variant : new int[] {1, 2}) {
            for (RuntimeException failure : new RuntimeException[] {
                    new SecurityException("FAKE denied", new SQLException("FAKE", "HY000", 1020)),
                    new IllegalArgumentException("FAKE invalid", new SQLException("FAKE", "40001", 1213)),
                    new IllegalStateException("FAKE stale binding"), conflict(1062, "23000"), conflict(1020, "40001")}) {
                setUp();
                assertSame(failure, assertThrows(RuntimeException.class, () -> owner(variant, () -> { throw failure; })));
                assertEquals(1, fixture.manager.begins);
                assertEquals(expectedLocks(variant), locks);
                assertClean();
            }
        }
    }

    @Test
    void newScopesRejectAReusedPersistenceContextBeforeTheSecondCallbackOrLock() {
        for (int variant : new int[] {1, 2}) {
            setUp();
            fixture.manager.reuseEntityManager = true;
            AtomicInteger calls = new AtomicInteger();
            assertThrows(IllegalStateException.class, () -> owner(variant, () -> {
                calls.incrementAndGet();
                throw conflict(1020, "HY000");
            }));
            assertEquals(1, calls.get());
            assertEquals(2, fixture.manager.begins);
            assertEquals(expectedLocks(variant), locks);
            assertClean();
        }
    }

    @Test
    void newScopesNeverReplayAfterCommitAuditFailuresEvenWhenTheyLookRetryable() {
        for (int variant : new int[] {1, 2}) {
            for (int code : new int[] {1020, 1213, 1205}) {
                setUp();
                AtomicInteger operations = new AtomicInteger();
                AtomicInteger audits = new AtomicInteger();
                RuntimeException failure = conflict(code, code == 1213 ? "40001" : "HY000");
                assertSame(failure, assertThrows(RuntimeException.class, () -> owner(variant, () -> {
                    operations.incrementAndGet();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            audits.incrementAndGet();
                            throw failure;
                        }
                    });
                    return null;
                })));
                assertEquals(1, operations.get());
                assertEquals(1, audits.get());
                assertEquals(1, fixture.manager.begins);
                assertEquals(1, fixture.manager.commits);
                assertEquals(0, fixture.manager.rollbacks);
                assertClean();
            }
        }
    }

    @Test
    void newScopesNeverReplayCommitPhaseErrorsWithUnknownOrMisleadingRollbackStatus() {
        for (int variant : new int[] {1, 2}) {
            for (boolean transactionFailure : new boolean[] {false, true}) {
                setUp();
                AtomicInteger operations = new AtomicInteger();
                AtomicInteger completion = new AtomicInteger(-1);
                RuntimeException failure = transactionFailure
                        ? new TransactionSystemException("FAKE ambiguous unit-manager commit", new SQLException("FAKE", "HY000", 1020))
                        : conflict(1020, "HY000");
                fixture.manager.onCommit = () -> { throw failure; };
                assertSame(failure, assertThrows(RuntimeException.class, () -> owner(variant, () -> {
                    operations.incrementAndGet();
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void afterCompletion(int status) {
                            completion.set(status);
                        }
                    });
                    return null;
                })));
                assertEquals(transactionFailure ? TransactionSynchronization.STATUS_UNKNOWN
                        : TransactionSynchronization.STATUS_ROLLED_BACK, completion.get());
                assertEquals(1, operations.get());
                assertEquals(1, fixture.manager.begins);
                assertEquals(1, fixture.manager.commits);
                assertClean();
            }
        }
    }

    @Test
    void failedPreparationDisablesReplayInBothNewScopes() {
        for (int variant : new int[] {1, 2}) {
            setUp();
            AtomicInteger preparations = new AtomicInteger();
            assertThrows(RuntimeException.class, () -> owner(variant, () -> {
                preparations.incrementAndGet();
                PortalMyCarlosInventoryTransactions.preparationFailed();
                throw conflict(1020, "HY000");
            }));
            assertEquals(1, preparations.get());
            assertEquals(1, fixture.manager.begins);
            assertClean();
        }
    }

    @Test
    void everyScopeRejectsAnOuterTransactionBeforeAnyLockOrCallback() {
        for (int variant = 0; variant < 3; variant++) {
            setUp();
            AtomicInteger effects = new AtomicInteger();
            TransactionSynchronizationManager.setActualTransactionActive(true);
            try {
                int selected = variant;
                assertThrows(IllegalStateException.class, () -> owner(selected, effects::incrementAndGet));
            } finally {
                TransactionSynchronizationManager.setActualTransactionActive(false);
            }
            assertEquals(0, effects.get());
            assertEquals(0, fixture.manager.begins);
            verifyNoInteractions(properties, coordination);
            assertClean();
        }
    }

    @Test
    void everyScopeRejectsAnInheritedSynchronizationBeforeAnyLockOrCallback() {
        for (int variant = 0; variant < 3; variant++) {
            setUp();
            TransactionSynchronizationManager.initSynchronization();
            try {
                int selected = variant;
                assertThrows(IllegalStateException.class, () -> owner(selected, () -> "FAKE"));
            } finally {
                TransactionSynchronizationManager.clearSynchronization();
            }
            assertEquals(0, fixture.manager.begins);
            verifyNoInteractions(properties, coordination);
            assertClean();
        }
    }

    @Test
    void everyScopeRejectsAnExternallyBoundEntityManagerBeforeBeginning() {
        for (int variant = 0; variant < 3; variant++) {
            setUp();
            TransactionSynchronizationManager.bindResource(fixture.factory, new EntityManagerHolder(mock(EntityManager.class)));
            try {
                int selected = variant;
                assertThrows(IllegalStateException.class, () -> owner(selected, () -> "FAKE"));
            } finally {
                TransactionSynchronizationManager.unbindResource(fixture.factory);
            }
            assertEquals(0, fixture.manager.begins);
            verifyNoInteractions(properties, coordination);
            assertClean();
        }
    }

    @Test
    void completedApprovalScopeLeavesNoMarkerBeforeASeparateCombinedOperation() {
        approval.withApprovalState(() -> "FAKE first");
        assertClean();
        approval.withSigningInventory(value -> "FAKE second");
        assertEquals(List.of(APPROVAL, INVENTORY, APPROVAL), locks);
        assertEquals(2, fixture.manager.begins);
        assertNotSame(fixture.manager.entityManagers.get(0), fixture.manager.entityManagers.get(1));
        assertClean();
    }

    @Test
    void failedCombinedScopeLeavesNoMarkerBeforeASeparateApprovalOperation() {
        assertThrows(IllegalArgumentException.class,
                () -> fixture.transactions.executeSigningApproval(() -> { throw new IllegalArgumentException("FAKE refusal"); }));
        assertClean();
        assertDoesNotThrow(() -> approval.withApprovalState(() -> "FAKE next"));
        assertEquals(List.of(INVENTORY, APPROVAL, APPROVAL), locks);
        assertEquals(2, fixture.manager.begins);
        assertEquals(1, fixture.manager.rollbacks);
        assertEquals(1, fixture.manager.commits);
        assertClean();
    }

    @Test
    void snapshotReadDoesNotPromoteApprovalOnlyToInventoryAuthority() throws Exception {
        when(properties.findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME)).thenReturn(List.of(row(catalogue())));
        approval.withApprovalState(() -> {
            assertTrue(worker.read().isPresent());
            assertThrows(IllegalStateException.class, worker::currentForUpdate);
            return null;
        });
        verify(properties, never()).findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        assertEquals(List.of(APPROVAL), locks);
        assertClean();
    }
}
