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
package io.github.carlos_emr.carlos.lab;

import io.github.carlos_emr.carlos.commn.dao.FileUploadCheckDao;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.commons.codec.digest.DigestUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Verifies committed database state using real Spring JDBC transactions and independent connections.
 * @since 2026-09-25
 */
@Tag("unit")
@Tag("lab")
class FileUploadCheckTransactionUnitTest extends CarlosUnitTestBase {
    private static final byte[] CONTENT = "synthetic transaction lab".getBytes(StandardCharsets.UTF_8);
    private FileUploadCheckDao dao;
    private DataSourceTransactionManager transactions;
    private JdbcTemplate jdbc;

    @BeforeEach
    void setUpDatabase() {
        var source = new DriverManagerDataSource("jdbc:h2:mem:upload_" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1", "sa", "");
        transactions = new DataSourceTransactionManager(source);
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table checksum (id int primary key, md5 varchar(32))");
        jdbc.execute("create table lab (id int primary key)");
        dao = mock(FileUploadCheckDao.class);
        when(dao.findByMd5Sum(anyString())).thenAnswer(invocation ->
                jdbc.query("select id from checksum where md5=?", (rs, index) -> {
                    var row = new io.github.carlos_emr.carlos.commn.model.FileUploadCheck();
                    row.setId(rs.getInt(1));
                    return row;
                }, invocation.<String>getArgument(0)));
        doAnswer(invocation -> {
            var row = invocation.<io.github.carlos_emr.carlos.commn.model.FileUploadCheck>getArgument(0);
            jdbc.update("insert into checksum values (1, ?)", row.getMd5sum());
            row.setId(1);
            return null;
        }).when(dao).persist(any());
        registerMock(FileUploadCheckDao.class, dao);
        registerMock(PlatformTransactionManager.class, transactions);
    }

    @AfterEach
    void closeDatabase() {
        jdbc.execute("shutdown");
    }

    @Test
    void shouldCommitLabAndChecksumIndependently_whenCallerRollsBack() {
        new TransactionTemplate(transactions).executeWithoutResult(outer -> {
            // Establish an outer snapshot before the independent upload transaction starts.
            assertThat(rows("checksum")).isZero();
            try {
                assertThat(store(id -> {
                    new TransactionTemplate(transactions).executeWithoutResult(joined -> {
                        assertThat(joined.isNewTransaction()).isFalse();
                        jdbc.update("insert into lab values (?)", id);
                    });
                    return true;
                })).isEqualTo(FileUploadCheck.StoreOutcome.STORED);
                assertThat(transactions.getDataSource()).isNotNull();
                // This second upload must see the committed row, even inside the outer transaction.
                assertThat(store(id -> { throw new AssertionError("duplicate parsed"); }))
                        .isEqualTo(FileUploadCheck.StoreOutcome.ALREADY_RECORDED);
            } catch (Exception failure) {
                throw new AssertionError(failure);
            }
            outer.setRollbackOnly();
        });
        assertThat(rows("checksum")).isEqualTo(1);
        assertThat(rows("lab")).isEqualTo(1);
    }

    @Test
    void shouldRemoveAllRowsAndPermitRetry_whenParserRejectsAfterWriting() throws Exception {
        assertThat(store(id -> {
            jdbc.update("insert into lab values (?)", id);
            return false;
        })).isEqualTo(FileUploadCheck.StoreOutcome.REJECTED);
        assertThat(rows("checksum")).isZero();
        assertThat(rows("lab")).isZero();
        assertThat(store(id -> { jdbc.update("insert into lab values (?)", id); return true; }))
                .isEqualTo(FileUploadCheck.StoreOutcome.STORED);
        assertThat(rows("checksum")).isEqualTo(1);
        assertThat(rows("lab")).isEqualTo(1);
    }

    @Test
    void shouldRemoveAllRows_whenJoinedRoutingTransactionFails() {
        assertThatThrownBy(() -> store(id -> {
            jdbc.update("insert into lab values (?)", id);
            new TransactionTemplate(transactions).executeWithoutResult(joined -> joined.setRollbackOnly());
            return true;
        })).isInstanceOf(org.springframework.transaction.UnexpectedRollbackException.class);
        assertThat(rows("checksum")).isZero();
        assertThat(rows("lab")).isZero();
    }

    @Test
    void shouldStoreWaitingUpload_whenFirstUploadRollsBack() throws Exception {
        checkConcurrentUpload(false, FileUploadCheck.StoreOutcome.STORED);
    }

    @Test
    void shouldRejectWaitingDuplicate_whenFirstUploadCommits() throws Exception {
        checkConcurrentUpload(true, FileUploadCheck.StoreOutcome.ALREADY_RECORDED);
    }

    private void checkConcurrentUpload(boolean firstCommits, FileUploadCheck.StoreOutcome expected) throws Exception {
        var worker = new AtomicReference<Thread>();
        var future = new CompletableFuture<FileUploadCheck.StoreOutcome>();
        var lock = FileUploadCheck.contentLock(DigestUtils.md5Hex(CONTENT));
        try {
            assertThat(store(id -> {
                jdbc.update("insert into lab values (?)", id);
                Thread thread = new Thread(() -> {
                    try (MockedStatic<SpringUtils> spring = mockStatic(SpringUtils.class)) {
                        spring.when(() -> SpringUtils.getBean(FileUploadCheckDao.class)).thenReturn(dao);
                        spring.when(() -> SpringUtils.getBean(PlatformTransactionManager.class)).thenReturn(transactions);
                        future.complete(store(secondId -> { jdbc.update("insert into lab values (?)", secondId); return true; }));
                    } catch (Throwable failure) {
                        future.completeExceptionally(failure);
                    }
                });
                thread.setDaemon(true);
                worker.set(thread);
                thread.start();
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (!lock.hasQueuedThread(thread) && System.nanoTime() < deadline) Thread.sleep(10);
                assertThat(lock.hasQueuedThread(thread)).isTrue();
                assertThat(future).isNotDone();
                return firstCommits;
            })).isEqualTo(firstCommits ? FileUploadCheck.StoreOutcome.STORED : FileUploadCheck.StoreOutcome.REJECTED);
        } finally {
            if (worker.get() != null) worker.get().join(10000);
        }
        assertThat(future.get(1, TimeUnit.SECONDS)).isEqualTo(expected);
        assertThat(rows("checksum")).isEqualTo(1);
        assertThat(rows("lab")).isEqualTo(1);
    }

    private FileUploadCheck.StoreOutcome store(FileUploadCheck.ContentStore callback) throws Exception {
        return FileUploadCheck.storeIfNew("synthetic.hl7", () -> new ByteArrayInputStream(CONTENT), "999998", callback);
    }

    private int rows(String table) {
        return jdbc.queryForObject(switch (table) {
            case "checksum" -> "select count(*) from checksum";
            case "lab" -> "select count(*) from lab";
            default -> throw new IllegalArgumentException("Unknown test table");
        }, Integer.class);
    }
}
