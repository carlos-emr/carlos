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

import io.github.carlos_emr.carlos.commn.interfaces.Immunization.ImmunizationProperty;
import io.github.carlos_emr.carlos.commn.model.Prevention;
import io.github.carlos_emr.carlos.commn.model.PreventionExt;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.hibernate.Hibernate;
import org.hibernate.LazyInitializationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression coverage for the lazy {@code Prevention.preventionExts} mapping as seen by
 * consumers that receive entities <em>after</em> the DAO transaction has ended.
 *
 * <p>Runs without a test-managed transaction so that every DAO call opens and closes its own
 * transaction, exactly like the REST and FHIR immunization paths in production, where no
 * OpenEntityManagerInView filter or outer {@code @Transactional} boundary exists.</p>
 *
 * @since 2026-09-17
 */
@DisplayName("PreventionDao detached preventionExts access")
@Tag("integration")
@Tag("dao")
@Tag("prevention")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PreventionDaoDetachedExtensionsIntegrationTest extends CarlosTestBase {

    private static final int DEMOGRAPHIC = 20777;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PreventionDao preventionDao;

    @Autowired
    private PreventionExtDao preventionExtDao;

    private TransactionTemplate transactions;

    @BeforeEach
    void seedPreventionWithExtensions() {
        transactions = new TransactionTemplate(transactionManager);
        transactions.executeWithoutResult(status -> {
            Prevention prevention = new Prevention();
            prevention.setDemographicId(DEMOGRAPHIC);
            prevention.setPreventionType("Flu");
            prevention.setPreventionDate(new Date());
            prevention.setProviderNo("-1");
            prevention.setCreatorProviderNo("999998");
            prevention.setDeleted(false);
            prevention.setRefused(false);
            preventionDao.persist(prevention);

            persistExt(prevention.getId(), ImmunizationProperty.lot.name(), "LOT-001");
            persistExt(prevention.getId(), ImmunizationProperty.providerName.name(), "External Clinic");
        });
    }

    @AfterEach
    void removeSyntheticRows() {
        transactions.executeWithoutResult(status -> {
            entityManager.createQuery("delete from PreventionExt e where e.preventionId in "
                    + "(select p.id from Prevention p where p.demographicId = :demo)")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
            entityManager.createQuery("delete from Prevention p where p.demographicId = :demo")
                    .setParameter("demo", DEMOGRAPHIC).executeUpdate();
        });
    }

    @Test
    @Tag("query")
    @DisplayName("findUniqueByDemographicId returns detached entities whose extensions are usable")
    void shouldInitializePreventionExts_whenLoadedViaFindUniqueByDemographicId() {
        List<Prevention> results = preventionDao.findUniqueByDemographicId(DEMOGRAPHIC);

        assertThat(results).hasSize(1);
        Prevention detached = results.get(0);
        assertThat(Hibernate.isInitialized(detached.getPreventionExts()))
                .as("preventionExts must be initialized before the DAO transaction ends")
                .isTrue();

        // Same call sequence as PreventionManagerImpl.getImmunizationsByDemographic and the
        // FHIR Immunization mapper, now outside any transaction.
        detached.setPreventionExtendedProperties();

        assertThat(detached.getLotNo()).isEqualTo("LOT-001");
        assertThat(detached.getImmunizationProperty(ImmunizationProperty.providerName))
                .isEqualTo("External Clinic");
    }

    @Test
    @Tag("query")
    @DisplayName("plain list queries leave the extensions collection uninitialized")
    void shouldLeavePreventionExtsUninitialized_whenLoadedViaListQuery() {
        List<Prevention> results = preventionDao.findNotDeletedByDemographicId(DEMOGRAPHIC);

        assertThat(results).hasSize(1);
        Prevention detached = results.get(0);

        // Pins the performance contract of the LAZY mapping at runtime, not just the annotation:
        // bulk list queries must not load one extension collection per row.
        assertThat(Hibernate.isInitialized(detached.getPreventionExts())).isFalse();
        assertThatThrownBy(detached::setPreventionExtendedProperties)
                .isInstanceOf(LazyInitializationException.class);
    }

    @Test
    @Tag("query")
    @DisplayName("single extension lookup works for detached preventions without the collection")
    void shouldResolveSingleExtension_withoutInitializingCollection() {
        Prevention detached = preventionDao.findNotDeletedByDemographicId(DEMOGRAPHIC).get(0);

        // The PreventionData list view resolves the external provider name this way.
        List<PreventionExt> providerName = preventionExtDao.findByPreventionIdAndKey(
                detached.getId(), ImmunizationProperty.providerName.name());

        assertThat(providerName).extracting(PreventionExt::getVal).containsExactly("External Clinic");
        assertThat(Hibernate.isInitialized(detached.getPreventionExts())).isFalse();
    }

    private void persistExt(Integer preventionId, String key, String value) {
        PreventionExt ext = new PreventionExt();
        ext.setPreventionId(preventionId);
        ext.setKeyval(key);
        ext.setVal(value);
        preventionExtDao.persist(ext);
    }
}
