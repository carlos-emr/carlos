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

import java.util.List;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import org.hibernate.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;

import io.github.carlos_emr.carlos.PMmodule.web.formbean.ClientSearchFormBean;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.test.base.CarlosTestBase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * Reading a patient never writes pref_name (issue #4835). {@link Demographic#getPrefName()}
 * answers the alias when one is set; while that getter was the column mapping, every loaded
 * patient whose alias differed from pref_name looked changed, so the next flush wrote the alias
 * into pref_name, and an alias longer than the 30-character column failed the page.
 *
 * <p>The stored values are set with native SQL, so the test does not depend on how the entity
 * writes them.</p>
 *
 * @since 2026-10-21
 */
@DisplayName("Demographic pref_name is not rewritten on read")
@Tag("integration")
@Tag("dao")
@Tag("demographic")
@Transactional
class DemographicPrefNameFlushIntegrationTest extends CarlosTestBase {

    private static final String STORED_PREF_NAME = "FAKE-Pref";
    private static final String LONG_ALIAS = "FAKE-Alias-" + "x".repeat(29);

    @Autowired
    private DemographicDao demographicDao;

    @PersistenceContext(unitName = "entityManagerFactory")
    private EntityManager entityManager;

    private Integer createPatient(String prefName, String alias) {
        Demographic demographic = new Demographic();
        demographic.setFirstName("FAKE-Pat");
        demographic.setLastName("FAKE-PrefName");
        demographic.setHcType("ON");
        demographic.setPatientStatus("AC");
        demographic.setProviderNo("999998");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("15");
        demographic.setSex("F");
        demographicDao.save(demographic);
        entityManager.flush();
        Integer id = demographic.getDemographicNo();
        entityManager.createNativeQuery("UPDATE demographic SET pref_name = :pref, alias = :alias WHERE demographic_no = :id")
                .setParameter("pref", prefName)
                .setParameter("alias", alias)
                .setParameter("id", id)
                .executeUpdate();
        entityManager.clear();
        return id;
    }

    private String storedPrefName(Integer id) {
        entityManager.flush();
        entityManager.clear();
        return (String) entityManager.createNativeQuery("SELECT pref_name FROM demographic WHERE demographic_no = :id")
                .setParameter("id", id)
                .getSingleResult();
    }

    private boolean sessionIsDirty() {
        return entityManager.unwrap(Session.class).isDirty();
    }

    @Test
    void shouldLeavePrefNameUnchanged_whenAPatientWithADifferentAliasIsLoaded() {
        Integer id = createPatient(STORED_PREF_NAME, "FAKE-Alias");

        Demographic loaded = demographicDao.getDemographicById(id);

        assertThat(loaded.getPrefName()).as("the display value is still the alias").isEqualTo("FAKE-Alias");
        assertThat(sessionIsDirty()).as("loading the patient changes nothing").isFalse();
        assertThat(storedPrefName(id)).isEqualTo(STORED_PREF_NAME);
    }

    @Test
    void shouldLoadAndFlushWithoutError_whenTheAliasIsLongerThanThePrefNameColumn() {
        Integer id = createPatient(STORED_PREF_NAME, LONG_ALIAS);

        assertThatCode(() -> {
            demographicDao.getDemographicById(id);
            entityManager.flush();
        }).doesNotThrowAnyException();
        assertThat(storedPrefName(id)).isEqualTo(STORED_PREF_NAME);
    }

    @Test
    void shouldLeavePrefNameUnchanged_whenTheClientSearchListsThePatient() {
        // ClientManagerImpl.search runs this query in its read-write transaction, which flushes
        // every listed patient on commit (the chart's attach-note-to-client search).
        Integer id = createPatient(STORED_PREF_NAME, LONG_ALIAS);
        ClientSearchFormBean criteria = new ClientSearchFormBean();
        criteria.setDemographicNo(String.valueOf(id));

        List<Demographic> found = demographicDao.search(criteria);

        assertThat(found).extracting(Demographic::getDemographicNo).containsExactly(id);
        assertThat(sessionIsDirty()).isFalse();
        assertThat(storedPrefName(id)).isEqualTo(STORED_PREF_NAME);
    }

    @Test
    void shouldStoreTheEnteredPreferredName_whenAPatientWithAnAliasIsSaved() {
        Demographic demographic = new Demographic();
        demographic.setFirstName("FAKE-Pat");
        demographic.setLastName("FAKE-PrefName");
        demographic.setHcType("ON");
        demographic.setPatientStatus("AC");
        demographic.setProviderNo("999998");
        demographic.setYearOfBirth("1980");
        demographic.setMonthOfBirth("01");
        demographic.setDateOfBirth("15");
        demographic.setSex("F");
        demographic.setAlias("FAKE-Alias");
        demographic.setPrefName("FAKE-Typed");

        demographicDao.save(demographic);

        assertThat(storedPrefName(demographic.getDemographicNo())).isEqualTo("FAKE-Typed");
    }
}
