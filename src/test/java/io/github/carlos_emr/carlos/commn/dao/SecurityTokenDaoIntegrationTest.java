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

import io.github.carlos_emr.carlos.commn.dao.utils.EntityDataGenerator;
import io.github.carlos_emr.carlos.commn.model.SecurityToken;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link SecurityTokenDao} with full method coverage matching legacy tests.
 *
 * <p>Migrated from legacy {@code SecurityTokenDaoTest} (JUnit 4 / DaoTestFixtures).</p>
 *
 * @since 2026-03-07
 * @see SecurityTokenDao
 */
@DisplayName("SecurityToken Dao Integration Tests")
@Tag("integration")
@Tag("dao")
@Transactional
public class SecurityTokenDaoIntegrationTest extends CarlosTestBase {

    @Autowired
    private SecurityTokenDao dao;

    private final DateFormat dfm = new SimpleDateFormat("yyyyMMdd");

    @Test
    @Tag("create")
    @DisplayName("should persist security token with generated ID")
    void shouldPersistSecurityToken_whenValidDataProvided() throws Exception {
        SecurityToken entity = new SecurityToken();
        EntityDataGenerator.generateTestDataForModelClass(entity);
        dao.persist(entity);

        assertThat(entity.getId()).isPositive();
    }

    @Test
    @Tag("read")
    @DisplayName("should return matching token when filtered by token string and expiry after date")
    void shouldReturnMatchingToken_whenFilteredByTokenAndExpiry() throws Exception {
        String token1 = "alpha";
        String token2 = "bravo";

        Date date1 = new Date(dfm.parse("20100301").getTime());
        Date date2 = new Date(dfm.parse("20110301").getTime());
        Date date3 = new Date(dfm.parse("20130301").getTime());
        Date date4 = new Date(dfm.parse("20080301").getTime());

        Date expiry = new Date(dfm.parse("20090301").getTime());

        SecurityToken securityToken1 = new SecurityToken();
        EntityDataGenerator.generateTestDataForModelClass(securityToken1);
        securityToken1.setToken(token1);
        securityToken1.setExpiry(date1);
        dao.persist(securityToken1);

        SecurityToken securityToken2 = new SecurityToken();
        EntityDataGenerator.generateTestDataForModelClass(securityToken2);
        securityToken2.setToken(token2);
        securityToken2.setExpiry(date2);
        dao.persist(securityToken2);

        SecurityToken securityToken3 = new SecurityToken();
        EntityDataGenerator.generateTestDataForModelClass(securityToken3);
        securityToken3.setToken(token1);
        securityToken3.setExpiry(date3);
        dao.persist(securityToken3);

        SecurityToken securityToken4 = new SecurityToken();
        EntityDataGenerator.generateTestDataForModelClass(securityToken4);
        securityToken4.setToken(token1);
        securityToken4.setExpiry(date4);
        dao.persist(securityToken4);

        SecurityToken result = dao.getByTokenAndExpiry(token1, expiry);
        SecurityToken expectedResult = securityToken1;

        assertThat(result).isEqualTo(expectedResult);
    }
    @Test
    void shouldScopeSoapLookupAndRevocation_withoutChangingOtherTokenPurposes() {
        SecurityToken mine = soapRow("soap1:" + "1".repeat(64), "soap1:41:123:456:hash");
        SecurityToken otherAccount = soapRow("soap1:" + "2".repeat(64), "soap1:410:123:456:hash");
        SecurityToken otherPurpose = soapRow("FAKE-other-purpose", "soap1:41:123:456:hash");
        dao.persist(mine);
        dao.persist(otherAccount);
        dao.persist(otherPurpose);
        hibernateTemplate.flush();
        assertThat(dao.findSoapToken(mine.getToken()).getId()).isEqualTo(mine.getId());
        assertThat(dao.getByTokenAndExpiry(mine.getToken(), new Date(0))).isNull();
        assertThat(dao.getByTokenAndExpiry(mine.getToken().toUpperCase(java.util.Locale.ROOT), new Date(0))).isNull();
        assertThat(dao.revokeSoapTokens("soap1:41:")).isEqualTo(1);
        hibernateTemplate.flush();
        hibernateTemplate.clear();
        assertThat(dao.findSoapToken(mine.getToken())).isNull();
        assertThat(dao.findSoapToken(otherAccount.getToken())).isNotNull();
        assertThat(dao.getByTokenAndExpiry(otherPurpose.getToken(), new Date(0))).isNotNull();
        assertThat(dao.revokeSoapTokens("soap1:41:")).isZero();
    }

    @Test
    void shouldRejectAmbiguousSoapRows_insteadOfSelectingAnArbitraryCredential() {
        String token = "soap1:" + "3".repeat(64);
        dao.persist(soapRow(token, "soap1:41:123:456:hash"));
        dao.persist(soapRow(token, "soap1:42:123:456:hash"));
        hibernateTemplate.flush();
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> dao.findSoapToken(token))
                .isInstanceOf(IllegalStateException.class).hasMessage("Ambiguous SOAP token storage");
    }

    @Test
    void shouldRejectWildcardOrUnscopedSoapDeletion_beforeAnyWrite() {
        for (String owner : new String[]{null, "", "soap1:%", "soap1:0:", "soap1:41"}) {
            org.assertj.core.api.Assertions.assertThatThrownBy(() -> dao.revokeSoapTokens(owner))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    private SecurityToken soapRow(String hash, String data) {
        SecurityToken row = new SecurityToken();
        row.setToken(hash);
        row.setData(data);
        row.setCreated(new Date());
        row.setExpiry(new Date(System.currentTimeMillis() + 3600000));
        return row;
    }

}
