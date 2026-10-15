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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.Date;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Reproduces the Administration ▸ Security Records ▸ Delete Record path from issue #4129 with no
 * ambient test transaction: {@code SecurityDelete2Action} calls {@code securityDao.find()} and then
 * {@code securityDao.remove(entity)}, each in its own DAO transaction, so {@code remove()} receives a
 * detached instance. Hibernate 7 rejected that with {@code DetachedObjectException} and the row
 * survived.
 *
 * @since 2026-10-07
 * @see AbstractDaoImpl#remove(io.github.carlos_emr.carlos.commn.model.AbstractModel)
 */
@DisplayName("SecurityDao remove() across transactions")
@Tag("integration")
@Tag("dao")
@Tag("delete")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SecurityDaoDetachedRemoveIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecurityDao securityDao;

    @Autowired
    private PlatformTransactionManager transactions;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private final String userName = "detached-" + UUID.randomUUID().toString().substring(0, 8);

    @AfterEach
    void removeOwnedRows() {
        new TransactionTemplate(transactions).executeWithoutResult(tx -> entityManager
                .createQuery("delete from Security s where s.userName = :userName")
                .setParameter("userName", userName)
                .executeUpdate());
    }

    @Test
    @DisplayName("should delete security record when entity was found in an earlier transaction")
    void shouldDeleteSecurityRecord_whenFoundInEarlierTransaction() {
        // Given: a committed login
        Security created = new Security();
        created.setProviderNo("999451");
        created.setUserName(userName);
        created.setPassword("not-a-real-hash");
        created.setLastUpdateDate(new Date());
        securityDao.persist(created);
        Integer securityNo = created.getSecurityNo();

        // When: the action's two separate DAO calls
        Security found = securityDao.find(securityNo);
        assertThat(found).isNotNull();
        securityDao.remove(found);

        // Then: the row is gone when read in a fresh transaction
        assertThat(securityDao.find(securityNo)).isNull();
        assertThat(rowsForUserName()).isZero();
    }

    private long rowsForUserName() {
        return new TransactionTemplate(transactions).execute(tx -> entityManager
                .createQuery("select count(s) from Security s where s.userName = :userName", Long.class)
                .setParameter("userName", userName)
                .getSingleResult());
    }
}
