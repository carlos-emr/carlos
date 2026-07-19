/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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
 * This software was written for CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.PMmodule.dao;

import io.github.carlos_emr.carlos.test.base.OpenOTestBase;
import io.github.carlos_emr.carlos.PMmodule.model.Program;
import io.github.carlos_emr.carlos.PMmodule.model.ProgramProvider;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.model.security.Secrole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.orm.hibernate5.HibernateTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

/**
 * Integration tests for ProgramProviderDAO multi-parameter query methods.
 *
 * @since 2026-02-03
 * @see ProgramProviderDAO
 */
@DisplayName("ProgramProviderDAO Integration Tests")
@Tag("integration")
@Tag("dao")
@Tag("pmmodule")
@Transactional
public class ProgramProviderDAOIntegrationTest extends OpenOTestBase {

    @Autowired
    @Qualifier("programProviderDAO")
    private ProgramProviderDAO programProviderDAO;

    @Autowired
    private HibernateTemplate hibernateTemplate;

    private Long testProgramId1;
    private Long testProgramId2;
    private Long testRoleId1;
    private Long testRoleId2;
    private String testProviderNo1;
    private String testProviderNo2;

    @BeforeEach
    void setUp() {
        // Use short prefix to fit provider_no VARCHAR(6) constraint
        String uniquePrefix = String.valueOf(System.nanoTime() % 10000);

        // Create parent Provider records (provider_no is VARCHAR(6))
        testProviderNo1 = uniquePrefix + "1";
        testProviderNo2 = uniquePrefix + "2";
        createProvider(testProviderNo1, "Test", "Provider1");
        createProvider(testProviderNo2, "Test", "Provider2");

        // Create parent Program records
        Program program1 = createProgram("Test Program 1");
        Program program2 = createProgram("Test Program 2");
        testProgramId1 = (long) program1.getId();
        testProgramId2 = (long) program2.getId();

        // Create parent Secrole records
        Secrole role1 = createSecrole("Doctor");
        Secrole role2 = createSecrole("Nurse");
        testRoleId1 = role1.getId();
        testRoleId2 = role2.getId();

        hibernateTemplate.flush();
    }

    private Provider createProvider(String providerNo, String firstName, String lastName) {
        Provider provider = new Provider();
        provider.setProviderNo(providerNo);
        provider.setFirstName(firstName);
        provider.setLastName(lastName);
        provider.setStatus("1");
        provider.setProviderType("doctor");
        provider.setSex("M");
        provider.setSpecialty("");
        hibernateTemplate.save(provider);
        return provider;
    }

    private Program createProgram(String name) {
        Program program = new Program();
        program.setName(name);
        program.setType("community");
        program.setProgramStatus("active");
        hibernateTemplate.save(program);
        return program;
    }

    private Secrole createSecrole(String roleName) {
        Secrole role = new Secrole();
        role.setRoleName(roleName);
        role.setDescription("Test role: " + roleName);
        hibernateTemplate.save(role);
        return role;
    }

    private ProgramProvider createProgramProvider(String providerNo, Long programId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        hibernateTemplate.save(pp);
        return pp;
    }

    private ProgramProvider createProgramProvider(String providerNo, Long programId, Long roleId) {
        ProgramProvider pp = new ProgramProvider();
        pp.setProviderNo(providerNo);
        pp.setProgramId(programId);
        pp.setRoleId(roleId);
        hibernateTemplate.save(pp);
        return pp;
    }

    @Nested
    @DisplayName("getProgramProviderByProviderProgramId (2 params)")
    class GetByProviderProgramId {

        @Test
        @Tag("query")
        @DisplayName("should find program provider when both params match")
        void shouldFind_whenBothParamsMatch() {
            ProgramProvider match = createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId(testProviderNo1, testProgramId1);

            assertThat(results)
                .hasSize(1)
                .extracting(ProgramProvider::getId)
                .containsExactly(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return empty when provider doesn't match")
        void shouldReturnEmpty_whenProviderDoesntMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO
                .getProgramProviderByProviderProgramId("NONEXISTENT", testProgramId1);

            assertThat(results).isEmpty();
        }
    }

    @Nested
    @DisplayName("getProgramProvider (2 params)")
    class GetProgramProviderTwoParams {

        @Test
        @Tag("query")
        @DisplayName("should return single program provider when both params match")
        void shouldReturnSingle_whenBothParamsMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1);

            assertThat(found).isNotNull();
            assertThat(found.getProviderNo()).isEqualTo(testProviderNo1);
            assertThat(found.getProgramId()).isEqualTo(testProgramId1);
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when no match")
        void shouldReturnNull_whenNoMatch() {
            createProgramProvider(testProviderNo1, testProgramId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, 999999L);

            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("getProgramProvider (3 params)")
    class GetProgramProviderThreeParams {

        @Test
        @Tag("query")
        @DisplayName("should find when all three parameters match")
        void shouldFind_whenAllThreeParamsMatch() {
            ProgramProvider match = createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            createProgramProvider(testProviderNo1, testProgramId1, testRoleId2);
            createProgramProvider(testProviderNo1, testProgramId2, testRoleId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, testRoleId1);

            assertThat(found).isNotNull();
            assertThat(found.getId()).isEqualTo(match.getId());
        }

        @Test
        @Tag("query")
        @DisplayName("should return null when role doesn't match")
        void shouldReturnNull_whenRoleDoesntMatch() {
            createProgramProvider(testProviderNo1, testProgramId1, testRoleId1);
            hibernateTemplate.flush();

            ProgramProvider found = programProviderDAO.getProgramProvider(testProviderNo1, testProgramId1, 999999L);

            assertThat(found).isNull();
        }
    }

    @Nested
    @DisplayName("Single parameter queries (baseline)")
    class SingleParamQueries {

        @Test
        @Tag("read")
        @DisplayName("should get program providers by provider number")
        void shouldGetByProviderNo() {
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            createProgramProvider(testProviderNo2, testProgramId1);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO.getProgramProviderByProviderNo(testProviderNo1);

            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProviderNo().equals(testProviderNo1));
        }

        @Test
        @Tag("read")
        @DisplayName("should get program providers by program ID")
        void shouldGetByProgramId() {
            createProgramProvider(testProviderNo1, testProgramId1);
            createProgramProvider(testProviderNo2, testProgramId1);
            createProgramProvider(testProviderNo1, testProgramId2);
            hibernateTemplate.flush();

            List<ProgramProvider> results = programProviderDAO.getProgramProviders(testProgramId1);

            assertThat(results)
                .hasSize(2)
                .allMatch(pp -> pp.getProgramId().equals(testProgramId1));
        }
    }
}
