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
package io.github.carlos_emr.carlos.daos.security;

import io.github.carlos_emr.carlos.model.security.Secobjectname;
import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for {@link SecObjectNameDao} Hibernate migration validation.
 *
 * <p>These tests validate that the {@code saveOrUpdate} method in
 * {@code SecObjectNameDaoImpl} correctly persists and updates
 * {@link Secobjectname} entities via HibernateTemplate after Hibernate migration.
 * The entity uses an assigned ID strategy (the {@code objectname} field is the
 * primary key), mapped to the {@code secObjectName} table.</p>
 *
 * <p>The {@code secObjectName} table is part of the CARLOS EMR security model
 * and stores the names of security objects used by the role-based access control
 * system (see {@link io.github.carlos_emr.carlos.managers.SecurityInfoManager}).
 * Each row defines a named security privilege (e.g., {@code _demographic},
 * {@code _prevention}) that can be granted to roles via
 * {@code secobjprivilege} records.</p>
 *
 * <h3>Entity Mapping Details</h3>
 * <p>The {@link Secobjectname} entity is mapped via
 * {@code com/quatro/model/security/Secobjectname.hbm.xml} with the following
 * column layout:</p>
 * <ul>
 *   <li>{@code objectName} (VARCHAR 100) - assigned primary key, the security object identifier</li>
 *   <li>{@code description} (VARCHAR 60) - human-readable description of the security object</li>
 *   <li>{@code orgapplicable} (INTEGER, precision 1) - flag indicating whether the object
 *       applies at the organization level (1 = applicable, 0 = not applicable)</li>
 * </ul>
 *
 * <h3>Verification Strategy</h3>
 * <p>All assertions use native SQL queries rather than Hibernate/JPA reads to ensure
 * that the data has actually been flushed to the underlying H2 database. This avoids
 * false positives from Hibernate's first-level cache returning the same managed entity
 * instance that was just saved.</p>
 *
 * @since 2026-02-09
 * @see SecObjectNameDao
 * @see SecObjectNameDaoImpl
 * @see Secobjectname
 */
@DisplayName("SecObjectNameDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
public class SecObjectNameDaoIntegrationTest extends OpenOTestBase {

    /**
     * The DAO under test, providing {@code saveOrUpdate} operations for
     * {@link Secobjectname} entities.
     *
     * <p>Injected by Spring from the test application context. The implementation
     * ({@link SecObjectNameDaoImpl}) extends {@code HibernateDaoSupport} and
     * delegates persistence to {@code HibernateTemplate.saveOrUpdate()}, which
     * performs an INSERT for new entities or an UPDATE for detached/managed
     * entities based on the assigned identifier.</p>
     *
     * @see SecObjectNameDaoImpl
     */
    @Autowired
    private SecObjectNameDao secObjectNameDao;

    /**
     * JPA {@link EntityManager} used exclusively for test verification queries.
     *
     * <p>Injected with the {@code entityManagerFactory} persistence unit, which
     * shares the same H2 in-memory database and transaction context as the
     * Hibernate {@code SessionFactory} used by the DAO. This allows native SQL
     * verification queries to read data written by the DAO within the same
     * transaction, ensuring test isolation via {@code @Transactional} rollback.</p>
     *
     * <p>The EntityManager is used here instead of the DAO for reads because
     * verification through the same DAO would not confirm that data actually
     * reached the database -- it could be served from Hibernate's first-level
     * cache.</p>
     *
     * @see javax.persistence.PersistenceContext
     */
    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    /**
     * Native SQL query used to verify persisted {@link Secobjectname} data directly
     * in the {@code secObjectName} table.
     *
     * <p>This query selects all three columns ({@code objectName}, {@code description},
     * {@code orgapplicable}) from the {@code secObjectName} table filtered by the
     * primary key. Using native SQL (rather than HQL or a DAO read method) ensures
     * that assertions validate actual database state, bypassing Hibernate's first-level
     * cache entirely.</p>
     *
     * <p>The positional parameter {@code ?1} corresponds to the {@code objectName}
     * primary key value being verified. Results are returned as {@code Object[]} arrays
     * with columns in the order: [0] objectName, [1] description, [2] orgapplicable.</p>
     */
    private static final String SELECT_SQL = "SELECT objectName, description, orgapplicable FROM secObjectName WHERE objectName = ?1";

    /**
     * Verifies that {@link SecObjectNameDao#saveOrUpdate(Secobjectname)} correctly
     * persists a new {@link Secobjectname} entity with all fields populated.
     *
     * <p>This test exercises the INSERT path of {@code HibernateTemplate.saveOrUpdate()}.
     * Because the entity uses an assigned ID strategy (the {@code objectname} field is
     * set by the caller, not auto-generated), Hibernate determines whether to INSERT
     * or UPDATE by checking if an entity with the given identifier already exists in the
     * session or database. For a brand-new identifier, this results in an INSERT.</p>
     *
     * <h4>Verification</h4>
     * <p>After flushing the persistence context, a native SQL query confirms that all
     * three columns ({@code objectName}, {@code description}, {@code orgapplicable})
     * were written to the database with the expected values.</p>
     *
     * @see SecObjectNameDao#saveOrUpdate(Secobjectname)
     */
    @Test
    @Tag("create")
    @DisplayName("should persist Secobjectname when new instance provided")
    void shouldPersistSecobjectname_whenNewInstanceProvided() {
        // Given - create a fully populated entity using the three-arg constructor
        Secobjectname secObj = new Secobjectname("_testObject1", "Test security object", 1);

        // When - persist via the DAO under test
        secObjectNameDao.saveOrUpdate(secObj);

        // Flush the Hibernate session to force the SQL INSERT to execute immediately,
        // ensuring that the subsequent native SQL verification query can see the data
        hibernateTemplate.flush();

        // Then - verify via native SQL to ensure data reached the database
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject1")
            .getResultList();

        // Exactly one row should exist for this primary key
        assertThat(results).hasSize(1);

        // Extract the row as an Object array (columns ordered per SELECT_SQL)
        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject1");
        assertThat(row[1]).isEqualTo("Test security object");
        // Cast to Number first because H2 may return different integer subtypes
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }

    /**
     * Verifies that {@link SecObjectNameDao#saveOrUpdate(Secobjectname)} correctly
     * updates an already-persisted {@link Secobjectname} entity when its mutable
     * fields are modified.
     *
     * <p>This test exercises the UPDATE path of {@code HibernateTemplate.saveOrUpdate()}.
     * The entity is first persisted with initial values, then its {@code description}
     * and {@code orgapplicable} fields are modified, and {@code saveOrUpdate} is called
     * a second time. Because the entity is still managed within the same Hibernate
     * session (thanks to {@code @Transactional}), this results in an UPDATE rather
     * than a duplicate INSERT.</p>
     *
     * <h4>Verification</h4>
     * <p>After the second flush, a native SQL query confirms that only one row exists
     * for the primary key, and that it contains the updated (not original) field values.
     * This confirms that {@code saveOrUpdate} correctly merged the changes rather than
     * creating a duplicate record.</p>
     *
     * @see SecObjectNameDao#saveOrUpdate(Secobjectname)
     */
    @Test
    @Tag("update")
    @DisplayName("should update Secobjectname when existing instance modified")
    void shouldUpdateSecobjectname_whenExistingInstanceModified() {
        // Given - save an initial entity with original values
        Secobjectname secObj = new Secobjectname("_testObject2", "Original description", 0);
        secObjectNameDao.saveOrUpdate(secObj);

        // Flush to ensure the initial INSERT is executed before the update
        hibernateTemplate.flush();

        // When - modify mutable fields and call saveOrUpdate again
        secObj.setDescription("Updated description");
        secObj.setOrgapplicable(1);
        secObjectNameDao.saveOrUpdate(secObj);

        // Flush again to push the UPDATE SQL to the database
        hibernateTemplate.flush();

        // Then - verify the updated values via native SQL
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject2")
            .getResultList();

        // Still exactly one row -- no duplicate was created
        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject2");
        // Description should reflect the updated value, not "Original description"
        assertThat(row[1]).isEqualTo("Updated description");
        // orgapplicable should be updated from 0 to 1
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }

    /**
     * Verifies that {@link SecObjectNameDao#saveOrUpdate(Secobjectname)} correctly
     * handles a minimally constructed entity where only the primary key
     * ({@code objectname}) is set, and all optional fields are {@code null}.
     *
     * <p>This test uses the single-argument constructor
     * {@link Secobjectname#Secobjectname(String)}, which sets only the
     * {@code objectname} field and leaves {@code description} and
     * {@code orgapplicable} as {@code null}. This validates that:</p>
     * <ul>
     *   <li>The HBM mapping does not impose NOT NULL constraints on optional columns</li>
     *   <li>The DAO correctly persists entities with nullable fields</li>
     *   <li>Hibernate does not throw exceptions for null property values</li>
     * </ul>
     *
     * <h4>Verification</h4>
     * <p>After flushing, a native SQL query confirms that the row was created with
     * {@code null} values for both {@code description} and {@code orgapplicable}.</p>
     *
     * @see SecObjectNameDao#saveOrUpdate(Secobjectname)
     * @see Secobjectname#Secobjectname(String)
     */
    @Test
    @Tag("create")
    @DisplayName("should persist with minimal fields when only objectname provided")
    void shouldPersistWithMinimalFields_whenOnlyObjectnameProvided() {
        // Given - use the minimal constructor (objectname only, all other fields null)
        Secobjectname secObj = new Secobjectname("_testObject3");

        // When
        secObjectNameDao.saveOrUpdate(secObj);
        hibernateTemplate.flush();

        // Then - verify persisted with null optional fields
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject3")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject3");
        // description should be null since only the minimal constructor was used
        assertThat(row[1]).isNull();
        // orgapplicable should also be null (not defaulted to 0)
        assertThat(row[2]).isNull();
    }

    /**
     * Verifies that {@link SecObjectNameDao#saveOrUpdate(Secobjectname)} correctly
     * persists all fields when the full three-argument constructor is used.
     *
     * <p>This test is complementary to
     * {@link #shouldPersistWithMinimalFields_whenOnlyObjectnameProvided()} and confirms
     * that the full constructor {@link Secobjectname#Secobjectname(String, String, Integer)}
     * populates every column in the {@code secObjectName} table. Together, these two
     * tests ensure that both constructor paths produce valid, persistable entities.</p>
     *
     * <h4>Verification</h4>
     * <p>After flushing, a native SQL query confirms that all three columns contain the
     * expected non-null values passed to the full constructor.</p>
     *
     * @see SecObjectNameDao#saveOrUpdate(Secobjectname)
     * @see Secobjectname#Secobjectname(String, String, Integer)
     */
    @Test
    @Tag("create")
    @DisplayName("should persist with all fields when full constructor used")
    void shouldPersistWithAllFields_whenFullConstructorUsed() {
        // Given - use the full constructor with all three fields populated
        Secobjectname secObj = new Secobjectname("_testObject4", "Full constructor test", 1);

        // When
        secObjectNameDao.saveOrUpdate(secObj);
        hibernateTemplate.flush();

        // Then - verify all fields persisted correctly
        List<?> results = entityManager.createNativeQuery(SELECT_SQL)
            .setParameter(1, "_testObject4")
            .getResultList();

        assertThat(results).hasSize(1);

        Object[] row = (Object[]) results.get(0);
        assertThat(row[0]).isEqualTo("_testObject4");
        assertThat(row[1]).isEqualTo("Full constructor test");
        assertThat(((Number) row[2]).intValue()).isEqualTo(1);
    }
}
