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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.commn.dao.CdsImportIdentityDao;
import io.github.carlos_emr.carlos.commn.model.CdsImportIdentity;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsIdentityDecisionDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsPatientIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsHealthCardAuthority;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.*;

/** Real authority-aware native and batch identity queries, with every synthetic row rolled back. */
@Tag("integration")
class CdsIdentityMariaDbIntegrationTest {
    private static SessionFactory factory;
    @TempDir Path temp;
    private final String hin =
            Long.toString(
                    new java.security.SecureRandom().nextLong(1_000_000_000L, 9_999_999_999L));
    private final String run = UUID.randomUUID().toString();
    private final LocalDate birth = LocalDate.of(2000, 1, 2);

    @BeforeAll
    static void openFactory() throws Exception {
        String selector = System.getProperty("cds.test.properties");
        Assumptions.assumeTrue(
                selector != null && !selector.isBlank(),
                "Explicit protected synthetic MariaDB tier required");
        Properties p = new Properties();
        try (var in = Files.newInputStream(Path.of(selector))) {
            p.load(in);
        }
        var source =
                new DriverManagerDataSource(
                        p.getProperty("jdbcUrl").replace("jdbc:mariadb:", "jdbc:mysql:"),
                        p.getProperty("jdbcUser"),
                        p.getProperty("jdbcPassword"));
        try (var connection = source.getConnection();
                var statement = connection.createStatement();
                var result = statement.executeQuery("SELECT DATABASE()")) {
            assertThat(result.next()).isTrue();
            assertThat(result.getString(1)).matches("[A-Za-z0-9_]*(test|fixture)[A-Za-z0-9_]*");
        }
        Configuration configuration = new Configuration();
        for (Class<?> type :
                new Class<?>[] {
                    Demographic.class,
                    DemographicMerged.class,
                    Provider.class,
                    CdsImportIdentity.class
                }) configuration.addAnnotatedClass(type);
        configuration.getProperties().put("hibernate.connection.datasource", source);
        configuration.setProperty("hibernate.hbm2ddl.auto", "none");
        configuration.setProperty("hibernate.dialect", "org.hibernate.dialect.MariaDBDialect");
        configuration.setProperty("hibernate.boot.allow_jdbc_metadata_access", "false");
        configuration.setProperty("hibernate.jdbc.time_zone", "UTC");
        factory = configuration.buildSessionFactory();
    }

    @AfterAll
    static void closeFactory() {
        if (factory != null) factory.close();
    }

    @Test
    void shouldCreateAndMatchIndependentNativeCharts_forSameNumericHinFromTwoKnownProvinces() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                Demographic ontario = nativePatient(session, "CA-ON", birth),
                        bc = nativePatient(session, "BC", birth);
                var resolver = resolver(session);
                assertThat(
                                resolver.resolve(source("ON", birth), command(true))
                                        .targetDemographicNo())
                        .isEqualTo(ontario.getDemographicNo());
                assertThat(
                                resolver.resolve(source("CA-BC", birth), command(true))
                                        .targetDemographicNo())
                        .isEqualTo(bc.getDemographicNo());
                assertThat(resolver.resolve(source("ON", birth), command(false)).outcome())
                        .isEqualTo(Outcome.INELIGIBLE);
                assertThat(resolver.resolve(source("QC", birth), command(true)).outcome())
                        .isEqualTo(Outcome.NO_MATCH);
                Demographic quebec = nativePatient(session, "QC", birth);
                assertThat(
                                resolver.resolve(source("CA-QC", birth), command(true))
                                        .targetDemographicNo())
                        .isEqualTo(quebec.getDemographicNo());
                assertThat(
                                resolver.matchesCanonical(
                                        source("ON", birth),
                                        ontario.getDemographicNo(),
                                        command(true)))
                        .isTrue();
                assertThat(
                                resolver.matchesCanonical(
                                        source("BC", birth), bc.getDemographicNo(), command(true)))
                        .isTrue();
                assertThat(
                                resolver.matchesCanonical(
                                        source("QC", birth),
                                        quebec.getDemographicNo(),
                                        command(true)))
                        .isTrue();
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldKeepDuplicateSameAuthorityUnresolvedByDobOrName_andInvalidateCanonicalReplay() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                Demographic original = nativePatient(session, "ON", birth);
                nativePatient(session, "CA-ON", birth.plusDays(1));
                var resolver = resolver(session);
                assertThat(resolver.resolve(source("ON", birth), command(true)).outcome())
                        .isEqualTo(Outcome.AMBIGUOUS);
                assertThat(
                                resolver.matchesCanonical(
                                        source("ON", birth),
                                        original.getDemographicNo(),
                                        command(true)))
                        .isFalse();
                Demographic bc = nativePatient(session, "BC", birth);
                assertThat(
                                resolver.resolve(source("BC", birth), command(true))
                                        .targetDemographicNo())
                        .isEqualTo(bc.getDemographicNo());
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldKeepUnknownNativeAuthorityAsConflict_evenWhenOneKnownProvinceAppearsUnique() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                Demographic original = nativePatient(session, "ON", birth);
                nativePatient(session, "-90", birth);
                var resolver = resolver(session);
                assertThat(resolver.resolve(source("ON", birth), command(true)).outcome())
                        .isEqualTo(Outcome.CONFLICTING_IDENTITY);
                assertThat(resolver.resolve(source("BC", birth), command(true)).outcome())
                        .isEqualTo(Outcome.CONFLICTING_IDENTITY);
                assertThat(
                                resolver.matchesCanonical(
                                        source("ON", birth),
                                        original.getDemographicNo(),
                                        command(true)))
                        .isFalse();
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldNeverAutoMergeUnknownSourceAuthority_withExactReplayRequiringNoOtherHinClaim() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                Demographic original = nativePatient(session, "-90", birth);
                var resolver = resolver(session);
                assertThat(resolver.resolve(source("-90", birth), command(true)).outcome())
                        .isEqualTo(Outcome.CONFLICTING_IDENTITY);
                assertThat(
                                resolver.matchesCanonical(
                                        source("-90", birth),
                                        original.getDemographicNo(),
                                        command(true)))
                        .isTrue();
                nativePatient(session, "BC", birth);
                assertThat(
                                resolver.matchesCanonical(
                                        source("-90", birth),
                                        original.getDemographicNo(),
                                        command(true)))
                        .isFalse();
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldKeepWrongDobAndMissingHinFallbackInReview_insteadOfAutomaticMerge() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                nativePatient(session, "ON", birth);
                var resolver = resolver(session);
                assertThat(
                                resolver.resolve(source("ON", birth.plusDays(1)), command(true))
                                        .outcome())
                        .isEqualTo(Outcome.CONFLICTING_IDENTITY);
                var noHin =
                        new CdsPatientIdentityDto(
                                "source", "Synthetic", "Authority", birth, null, null, "F", "AC");
                assertThat(resolver.resolve(noHin, command(true)).outcome())
                        .isEqualTo(Outcome.INVALID_IDENTITY);
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldScopeKnownAuthoritiesAndCopyKeys_whileBlockingSameOrUnknownAuthorityClaims() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                CdsImportIdentityDao dao = new CdsImportIdentityDao();
                ReflectionTestUtils.setField(dao, "entityManager", session);
                batch(session, "one", "ON", true, hin);
                assertThat(dao.healthNumberCollision(run, "clinical", "two", hin, "BC")).isFalse();
                batch(session, "two", "BC", true, hin);
                assertThat(dao.healthNumberCollision(run, "clinical", "three", hin, "ON")).isTrue();
                assertThat(dao.healthNumberCollision(run, "clinical", "three", hin, "QC"))
                        .isFalse();
                assertThat(dao.healthNumberCollision(run, "clinical", "three", hin, null)).isTrue();
                assertThat(dao.healthNumberCollision(run, "other-copy", "three", hin, "ON"))
                        .isFalse();
                batch(session, "unknown", null, true, hin);
                assertThat(dao.healthNumberCollision(run, "clinical", "three", hin, "QC")).isTrue();
                batch(session, "declined", null, false, "not-an-active-hin");
                assertThat(
                                dao.healthNumberCollision(
                                        run, "clinical", "next", "not-an-active-hin", "ON"))
                        .isFalse();
                assertThat(dao.clinicalCollationsMatch()).isTrue();
            } finally {
                tx.rollback();
            }
        }
    }

    @Test
    void shouldMatchPinnedVocabularyForGeneratedNativeAuthority_withoutGuessingSentinels() {
        try (Session session = factory.openSession()) {
            var tx = session.beginTransaction();
            try {
                Set<String> all = new LinkedHashSet<>(CdsHealthCardAuthority.PINNED_US);
                all.addAll(CdsHealthCardAuthority.CANADIAN);
                CdsHealthCardAuthority.CANADIAN.forEach(code -> all.add("CA-" + code));
                all.addAll(List.of("-50", "-70", "-90", "CA-XX", "NY", "on", "ON ", "CA-ON "));
                for (String authority : all) {
                    Demographic value = nativePatient(session, authority, birth);
                    Object actual =
                            session.createNativeQuery(
                                            "select cds_identity_issuer from demographic where"
                                                    + " demographic_no=:id")
                                    .setParameter("id", value.getDemographicNo())
                                    .getSingleResult();
                    assertThat(actual)
                            .as("Exact authority vocabulary mapping")
                            .isEqualTo(CdsHealthCardAuthority.known(authority));
                }
            } finally {
                tx.rollback();
            }
        }
    }

    private Demographic nativePatient(Session session, String issuer, LocalDate date) {
        Demographic patient =
                Demographic.create(
                        "Synthetic",
                        "Authority",
                        "F",
                        Integer.toString(date.getMonthValue()),
                        Integer.toString(date.getDayOfMonth()),
                        Integer.toString(date.getYear()),
                        hin,
                        "");
        patient.setPrefName("");
        patient.setHcType(issuer);
        patient.setPatientStatus("AC");
        patient.setLastUpdateDate(new Date());
        session.persist(patient);
        session.flush();
        return patient;
    }

    private void batch(
            Session session, String patient, String issuer, boolean comparable, String number) {
        CdsImportIdentity row = new CdsImportIdentity();
        row.setId(
                UUID.randomUUID().toString().replace("-", "")
                        + UUID.randomUUID().toString().replace("-", ""));
        row.setRunId(run);
        row.setPatientKey(patient);
        row.setCopyKey("clinical");
        row.setFirstName("Synthetic");
        row.setLastName("Authority");
        row.setBirthDate(birth);
        row.setHin(number);
        row.setIssuer(issuer);
        row.setComparable(comparable);
        session.persist(row);
        session.flush();
    }

    private CdsPatientIdentityResolver resolver(Session session) {
        var resolver = new CdsPatientIdentityResolver(new CdsFieldParser());
        ReflectionTestUtils.setField(resolver, "entityManager", session);
        return resolver;
    }

    private CdsPatientIdentityDto source(String issuer, LocalDate date) {
        return new CdsPatientIdentityDto(
                "source", "Synthetic", "Authority", date, hin, issuer, "F", "AC");
    }

    private CdsImportCommand command(boolean merge) {
        return new CdsImportCommand(
                temp,
                "authority",
                "999998",
                null,
                null,
                "authority-test",
                "cds-5.2",
                merge,
                false,
                false,
                0,
                null,
                false);
    }
}
