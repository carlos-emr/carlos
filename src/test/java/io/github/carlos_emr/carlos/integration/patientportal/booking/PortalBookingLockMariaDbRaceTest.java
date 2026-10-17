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
package io.github.carlos_emr.carlos.integration.patientportal.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Two patients picking the same time at once on real MariaDB (#3850): only one is booked.
 *
 * <p>H2 cannot show this, so it runs only against a throwaway MariaDB schema named in
 * {@code CARLOS_TEST_MARIADB_URL} (with {@code CARLOS_TEST_MARIADB_USER} and
 * {@code CARLOS_TEST_MARIADB_PASSWORD}); it creates and drops its own two tables there. It follows
 * the statement order of {@link PortalBookingChoiceService#book}: the provider row lock is the first
 * statement of the transaction, then the overlap check, then the insert. Taking the lock first
 * matters on MariaDB: a consistent read taken earlier would not see the other booking.
 */
@Tag("integration")
@Tag("mariadb")
class PortalBookingLockMariaDbRaceTest {
    private static final int BOOKERS = 8;

    @Test
    void shouldBookOnlyOnce_whenPatientsPickTheSameTimeAtOnce() throws Exception {
        String url = System.getenv("CARLOS_TEST_MARIADB_URL");
        assumeTrue(url != null && url.contains("carlos_race_"), "throwaway MariaDB schema not configured");
        try (Connection setup = connect(url); Statement statement = setup.createStatement()) {
            statement.execute("DROP TABLE IF EXISTS appointment");
            statement.execute("DROP TABLE IF EXISTS provider");
            statement.execute("CREATE TABLE provider (provider_no VARCHAR(6) PRIMARY KEY) ENGINE=InnoDB");
            statement.execute("CREATE TABLE appointment (appointment_no INT AUTO_INCREMENT PRIMARY KEY, "
                    + "provider_no VARCHAR(6) NOT NULL, appointment_date DATE NOT NULL, start_time TIME NOT NULL, "
                    + "end_time TIME NOT NULL, demographic_no INT NOT NULL, status CHAR(1) NOT NULL, "
                    + "KEY (provider_no, appointment_date, start_time)) ENGINE=InnoDB");
            statement.execute("INSERT INTO provider VALUES ('101')");
        }
        CyclicBarrier together = new CyclicBarrier(BOOKERS);
        ExecutorService pool = Executors.newFixedThreadPool(BOOKERS);
        List<Future<Boolean>> results = new ArrayList<>();
        for (int patient = 1; patient <= BOOKERS; patient++) {
            int demographicNo = patient;
            results.add(pool.submit(() -> book(url, demographicNo, together)));
        }
        int booked = 0;
        for (Future<Boolean> result : results) {
            booked += result.get(60, TimeUnit.SECONDS) ? 1 : 0;
        }
        pool.shutdown();
        try (Connection check = connect(url); Statement statement = check.createStatement();
                ResultSet count = statement.executeQuery("SELECT COUNT(*) FROM appointment")) {
            count.next();
            assertThat(count.getInt(1)).isEqualTo(1);
        } finally {
            try (Connection cleanup = connect(url); Statement statement = cleanup.createStatement()) {
                statement.execute("DROP TABLE IF EXISTS appointment");
                statement.execute("DROP TABLE IF EXISTS provider");
            }
        }
        assertThat(booked).isEqualTo(1);
    }

    private static boolean book(String url, int demographicNo, CyclicBarrier together) throws Exception {
        try (Connection connection = connect(url)) {
            connection.setAutoCommit(false);
            connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            together.await(30, TimeUnit.SECONDS);
            try (PreparedStatement lock = connection.prepareStatement(
                    "SELECT provider_no FROM provider WHERE provider_no = ? FOR UPDATE")) {
                lock.setString(1, "101");
                lock.executeQuery().close();
            }
            boolean free;
            try (PreparedStatement overlap = connection.prepareStatement("SELECT COUNT(*) FROM appointment "
                    + "WHERE provider_no = ? AND appointment_date = '2026-10-20' AND status <> 'C' "
                    + "AND start_time < '09:45:00' AND end_time >= '09:30:00'")) {
                overlap.setString(1, "101");
                try (ResultSet count = overlap.executeQuery()) {
                    count.next();
                    free = count.getInt(1) == 0;
                }
            }
            if (free) {
                try (PreparedStatement insert = connection.prepareStatement("INSERT INTO appointment "
                        + "(provider_no, appointment_date, start_time, end_time, demographic_no, status) "
                        + "VALUES ('101', '2026-10-20', '09:30:00', '09:44:00', ?, 't')")) {
                    insert.setInt(1, demographicNo);
                    insert.executeUpdate();
                }
            }
            connection.commit();
            return free;
        }
    }

    private static Connection connect(String url) throws Exception {
        return DriverManager.getConnection(url, System.getenv("CARLOS_TEST_MARIADB_USER"),
                System.getenv("CARLOS_TEST_MARIADB_PASSWORD"));
    }
}
