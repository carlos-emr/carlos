/**
 * Copyright (c) 2025. Magenta Health. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for
 * Magenta Health
 * Toronto, Ontario, Canada
 */
package io.github.carlos_emr.carlos.tickler.dao;

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.model.Tickler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Date;

/**
 * Base test class for TicklerDao integration tests.
 *
 * <p>Provides shared setup, utilities, and test data creation methods for all
 * TicklerDao test classes. Extends OpenOTestBase to inherit Spring context
 * configuration and SpringUtils anti-pattern handling.</p>
 *
 * <p><b>Shared Features:</b></p>
 * <ul>
 *   <li>Autowired TicklerDao instance</li>
 *   <li>EntityManager for direct database operations</li>
 *   <li>Standard test data creation in setUp()</li>
 *   <li>Helper methods for creating test ticklers</li>
 *   <li>Transaction rollback after each test</li>
 * </ul>
 *
 * @since 2025-01-17
 * @see TicklerDao
 * @see Tickler
 */
@Tag("integration")
@Tag("database")
@Tag("slow")
@Tag("dao")
@Tag("tickler")
@Transactional
public abstract class TicklerDaoBaseIntegrationTest extends OpenOTestBase {

    @Autowired
    protected TicklerDao ticklerDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    protected EntityManager entityManager;

    /**
     * Creates standardized test data for general test scenarios.
     *
     * <p>Sets up 5 test ticklers with the following characteristics:</p>
     * <ul>
     *   <li>Ticklers 1-3: Active status (STATUS.A)</li>
     *   <li>Ticklers 4-5: Completed status (STATUS.C)</li>
     *   <li>Tickler 1: High priority</li>
     *   <li>Ticklers 2-5: Normal priority</li>
     *   <li>Even-numbered ticklers assigned to provider 999999</li>
     *   <li>Odd-numbered ticklers assigned to provider 999998</li>
     * </ul>
     */
    @BeforeEach
    protected void setUp() {
        // Create test ticklers with varying statuses and assignments
        for (int i = 1; i <= 5; i++) {
            Tickler tickler = new Tickler();
            tickler.setDemographicNo(1000 + i);
            tickler.setMessage("Test tickler " + i);
            tickler.setStatus(i <= 3 ? Tickler.STATUS.A : Tickler.STATUS.C);
            tickler.setPriority(i == 1 ? Tickler.PRIORITY.High : Tickler.PRIORITY.Normal);
            tickler.setCreator("999998");
            tickler.setTaskAssignedTo(i % 2 == 0 ? "999999" : "999998");
            tickler.setServiceDate(new Date());
            entityManager.persist(tickler);
        }
        entityManager.flush();
    }

    /**
     * Helper method to create and persist a tickler with specified attributes.
     *
     * @param demoNo Integer the demographic number for the tickler
     * @param message String the tickler message content
     * @param status Tickler.STATUS the status of the tickler (A=Active, C=Completed, etc.)
     * @return Tickler the created and persisted tickler instance
     */
    protected Tickler createTickler(Integer demoNo, String message, Tickler.STATUS status) {
        Tickler tickler = new Tickler();
        tickler.setDemographicNo(demoNo);
        tickler.setMessage(message);
        tickler.setStatus(status);
        tickler.setPriority(Tickler.PRIORITY.Normal);
        tickler.setCreator("999998");
        tickler.setTaskAssignedTo("999998");
        tickler.setServiceDate(new Date());
        entityManager.persist(tickler);
        return tickler;
    }

    /**
     * Helper method to create multiple ticklers for bulk testing scenarios.
     *
     * @param count int the number of ticklers to create
     * @param baseDemo Integer the starting demographic number
     */
    protected void createBulkTestData(int count, Integer baseDemo) {
        for (int i = 0; i < count; i++) {
            Tickler tickler = new Tickler();
            tickler.setDemographicNo(baseDemo + i);
            tickler.setMessage("Bulk test tickler " + i);
            tickler.setStatus(i % 3 == 0 ? Tickler.STATUS.C : Tickler.STATUS.A);
            tickler.setPriority(i % 5 == 0 ? Tickler.PRIORITY.High : Tickler.PRIORITY.Normal);
            tickler.setCreator("999998");
            tickler.setTaskAssignedTo(i % 2 == 0 ? "999999" : "999998");
            tickler.setServiceDate(new Date());
            entityManager.persist(tickler);
        }
        entityManager.flush();
    }
}