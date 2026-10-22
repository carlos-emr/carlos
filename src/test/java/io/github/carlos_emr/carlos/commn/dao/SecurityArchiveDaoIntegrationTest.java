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

import java.util.Comparator;
import java.util.Date;
import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.commn.model.SecurityArchive;
import io.github.carlos_emr.carlos.managers.SecurityManager;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips {@link SecurityArchive} rows through H2 (issue #5120): an archived security record
 * keeps its MFA on/off setting, a row written before the column existed reads back as "not
 * recorded", and {@link SecurityManager#updateSecurityRecord} archives the setting the record
 * had BEFORE each change, so the archive shows when MFA was turned on and off.
 *
 * @since 2026-10-22
 * @see SecurityArchiveDao
 */
@DisplayName("SecurityArchiveDao Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("security")
@Transactional
class SecurityArchiveDaoIntegrationTest extends CarlosTestBase {

    private static final String PROVIDER_NO = "999990";
    private static final String ADMIN_PROVIDER_NO = "999998";

    @Autowired
    private SecurityDao securityDao;

    @Autowired
    private SecurityArchiveDao securityArchiveDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Security saveSecurity(String userName, boolean usingMfa) {
        Security security = new Security();
        security.setUserName(userName);
        security.setPassword("not-a-real-hash");
        security.setProviderNo(PROVIDER_NO);
        security.setLastUpdateDate(new Date());
        security.setUsingMfa(usingMfa);
        security.setMfaSecret(usingMfa ? "not-a-real-mfa-secret" : null);
        securityDao.persist(security);
        entityManager.flush();
        return security;
    }

    private List<SecurityArchive> archivedOldestFirst(Integer securityNo) {
        entityManager.flush();
        entityManager.clear();
        List<SecurityArchive> rows = securityArchiveDao.findBySecurityNo(securityNo);
        rows.sort(Comparator.comparing(SecurityArchive::getId));
        return rows;
    }

    @Nested
    @DisplayName("archiveRecord")
    class ArchiveRecord {

        @Test
        void shouldStoreMfaOn_whenTheArchivedRecordUsesMfa() {
            Security security = saveSecurity("fakemfaon", true);

            securityArchiveDao.archiveRecord(security);

            assertThat(archivedOldestFirst(security.getId()))
                    .singleElement()
                    .extracting(SecurityArchive::getUsingMfa)
                    .isEqualTo(Boolean.TRUE);
        }

        @Test
        void shouldStoreMfaOff_whenTheArchivedRecordDoesNotUseMfa() {
            Security security = saveSecurity("fakemfaoff", false);

            securityArchiveDao.archiveRecord(security);

            assertThat(archivedOldestFirst(security.getId()))
                    .singleElement()
                    .extracting(SecurityArchive::getUsingMfa)
                    .isEqualTo(Boolean.FALSE);
        }

        @Test
        void shouldReadMfaAsNotRecorded_forARowArchivedBeforeTheColumnExisted() {
            Security security = saveSecurity("fakelegacy", true);
            // A pre-V1.0.59 row: written without the column, so it holds NULL there.
            entityManager.createNativeQuery("INSERT INTO SecurityArchive "
                            + "(security_no, user_name, password, provider_no, lastUpdateDate) "
                            + "VALUES (?1, ?2, ?3, ?4, ?5)")
                    .setParameter(1, security.getId())
                    .setParameter(2, "fakelegacy")
                    .setParameter(3, "not-a-real-hash")
                    .setParameter(4, PROVIDER_NO)
                    .setParameter(5, new Date())
                    .executeUpdate();

            assertThat(archivedOldestFirst(security.getId()))
                    .singleElement()
                    .extracting(SecurityArchive::getUsingMfa)
                    .isNull();
        }
    }

    @Nested
    @DisplayName("SecurityManager.updateSecurityRecord")
    class UpdateSecurityRecord {

        private SecurityManager securityManager() {
            SecurityManager manager = new SecurityManager();
            ReflectionTestUtils.setField(manager, "securityDao", securityDao);
            ReflectionTestUtils.setField(manager, "securityArchiveDao", securityArchiveDao);
            return manager;
        }

        private LoggedInInfo admin() {
            Provider provider = new Provider();
            provider.setProviderNo(ADMIN_PROVIDER_NO);
            LoggedInInfo admin = new LoggedInInfo();
            admin.setLoggedInProvider(provider);
            return admin;
        }

        /** Loads the record as a page does: a copy of its own, edited and handed to the manager. */
        private Security editableCopy(Integer securityNo) {
            entityManager.clear();
            Security copy = securityDao.find(securityNo);
            entityManager.detach(copy);
            return copy;
        }

        @Test
        void shouldArchiveTheSettingBeforeEachChange_whenMfaIsTurnedOnAndThenOff() {
            SecurityManager manager = securityManager();
            Security security = saveSecurity("fakemfatoggle", false);
            Integer securityNo = security.getId();

            Security turnOn = editableCopy(securityNo);
            turnOn.setUsingMfa(true);
            manager.updateSecurityRecord(admin(), turnOn);
            entityManager.flush();

            Security turnOff = editableCopy(securityNo);
            turnOff.setUsingMfa(false);
            manager.updateSecurityRecord(admin(), turnOff);

            List<SecurityArchive> history = archivedOldestFirst(securityNo);
            assertThat(history).extracting(SecurityArchive::getUsingMfa)
                    .containsExactly(Boolean.FALSE, Boolean.TRUE);
            // The second archived row is the state the admin's first change left behind.
            assertThat(history.get(1).getLastUpdateUser()).isEqualTo(ADMIN_PROVIDER_NO);
            assertThat(securityDao.find(securityNo).isUsingMfa()).isFalse();
        }
    }
}
