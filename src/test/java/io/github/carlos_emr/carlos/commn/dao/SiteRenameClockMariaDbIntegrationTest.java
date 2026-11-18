/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderSite;
import io.github.carlos_emr.carlos.commn.model.Site;
import io.github.carlos_emr.carlos.test.base.IsolatedJpaTestDatabase;
import io.github.carlos_emr.carlos.utility.OscarTrackingBasicDataSource;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.TimeZone;
import java.util.UUID;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/** Site rename and sync over real pooled clinic-zone connections on a separately zoned server (#6001).
 * @since 2026-11-17
 */
@Tag("integration")
@Tag("mariadb-native")
@Isolated
@EnabledIfEnvironmentVariable(named = "CARLOS_TEST_MARIADB_DAO", matches = "true")
class SiteRenameClockMariaDbIntegrationTest {
    private static TimeZone originalZone;
    private static IsolatedJpaTestDatabase ownedDatabase;

    @BeforeAll static void selectClinicZone() throws Exception {
        originalZone = TimeZone.getDefault();
        TimeZone.setDefault(TimeZone.getTimeZone("America/Toronto"));
        try { ownedDatabase = new IsolatedJpaTestDatabase("site_clock_6001"); }
        catch (Exception | Error failure) { TimeZone.setDefault(originalZone); throw failure; }
    }

    @AfterAll static void restoreZone() throws Exception {
        try { if (ownedDatabase != null) ownedDatabase.close(); }
        finally { TimeZone.setDefault(originalZone); }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseClinicTime_whenTheDatabaseSessionClockDiffers(boolean splitZone) throws Exception {
        try (var fixture = new Fixture(splitZone)) {
            long before = System.currentTimeMillis();
            fixture.rename();
            Date renamed = fixture.readRenameTime();
            assertThat(renamed.getTime()).isBetween(before / 1000 * 1000,
                    System.currentTimeMillis() + 2000);
            assertThat(fixture.sql.queryForObject("SELECT location FROM appointment WHERE appointment_no=?",
                    String.class, fixture.unrelated)).isEqualTo(fixture.otherName);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldIncludeTheLaterUnrelatedEdit_whenSyncContinuesFromTheRenamedSite(boolean splitZone) throws Exception {
        try (var fixture = new Fixture(splitZone)) {
            fixture.rename();
            Date cursor = fixture.readRenameTime();
            // Give the ordinary record a current clinic-clock version before its real
            // entity edit, so second-boundary scheduling cannot create a timestamp tie.
            fixture.sql.update("UPDATE appointment SET updatedatetime=? WHERE appointment_no=?",
                    new Date(System.currentTimeMillis() / 1000 * 1000), fixture.unrelated);
            fixture.transactions.executeWithoutResult(status -> {
                var appointment = fixture.em.find(Appointment.class, fixture.unrelated);
                appointment.setNotes("Synthetic later ordinary edit");
                fixture.em.flush();
            });
            var results = fixture.appointments.findByUpdateDate(cursor, 5000);
            assertThat(results).extracting(Appointment::getId).contains(fixture.unrelated);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldKeepTheNextSecondFloor_whenThePersistedVersionIsAlreadyLater(boolean splitZone) throws Exception {
        try (var fixture = new Fixture(splitZone)) {
            Date future = new Date(System.currentTimeMillis() / 1000 * 1000 + 180_000);
            fixture.sql.update("UPDATE appointment SET updatedatetime=? WHERE appointment_no=?", future, fixture.renamed);
            fixture.rename();
            assertThat(fixture.readRenameTime().getTime()).isEqualTo(future.getTime() + 1000);
        }
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldUseClinicTime_whenThePersistedVersionIsNull(boolean splitZone) throws Exception {
        try (var fixture = new Fixture(splitZone)) {
            fixture.sql.update("UPDATE appointment SET updatedatetime=NULL WHERE appointment_no=?", fixture.renamed);
            long before = System.currentTimeMillis();
            fixture.rename();
            assertThat(fixture.readRenameTime().getTime()).isBetween(before / 1000 * 1000,
                    System.currentTimeMillis() + 2000);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
        final Object previousRegion = CarlosProperties.getInstance().get("billregion");
        final JdbcTemplate sql;
        final EntityManager em;
        final TransactionTemplate transactions;
        final SiteDao sites;
        final OscarAppointmentDao appointments;
        final int site;
        final int renamed;
        final int unrelated;
        final String tag = "S" + UUID.randomUUID().toString().replace("-", "").substring(0, 7);
        final String originalName = "FAKE " + tag + " old";
        final String otherName = "FAKE " + tag + " other";
        final String renamedName = "FAKE " + tag + " new";

        Fixture(boolean splitZone) throws Exception {
            assertThat(ZoneId.systemDefault()).isEqualTo(ZoneId.of("America/Toronto"));
            String clinicOffset = ZoneId.systemDefault().getRules().getOffset(Instant.now()).getId();
            if (clinicOffset.equals("Z")) clinicOffset = "+00:00";
            String sessionOffset = splitZone ? "+00:00" : clinicOffset;
            assertThat(clinicOffset).isNotEqualTo("+00:00");
            var database = ownedDatabase;
            var original = (DriverManagerDataSource) database.dataSource();
            var pool = new OscarTrackingBasicDataSource();
            try {
                context.registerBean(IsolatedJpaTestDatabase.class, () -> database, definition -> definition.setDestroyMethodName(""));
                // LOCAL is a supported explicit operator override and uses the clinic JVM zone.
                // The normalizer percent-encodes slash-based zone IDs, a separate connection defect.
                pool.setUrl(original.getUrl() + "&connectionTimeZone=LOCAL");
                pool.setUsername(original.getUsername()); pool.setPassword(original.getPassword());
                pool.setConnectionInitSqls(List.of("SET SESSION time_zone='" + sessionOffset + "'"));
                pool.setMaxTotal(3);
                context.registerBean(DataSource.class, () -> pool, definition -> definition.setDestroyMethodName("close"));
                context.register(Config.class); context.refresh();
                CarlosProperties.getInstance().setProperty("billregion", "BC");
                sql = new JdbcTemplate(pool);
                if (!"flyway".equals(System.getenv("CARLOS_TEST_MARIADB_SCHEMA"))) {
                    sql.execute("CREATE TABLE IF NOT EXISTS rschedule(avail_hour TEXT)");
                    sql.execute("CREATE TABLE IF NOT EXISTS scheduledate(reason VARCHAR(255))");
                    sql.execute("ALTER TABLE appointment MODIFY updatedatetime DATETIME NULL");
                }
                assertThat(sql.queryForObject("SELECT @@session.time_zone", String.class)).isEqualTo(sessionOffset);
                assertThat(pool.getUrl()).contains("connectionTimeZone=LOCAL");
                // The fixture and actual DAOs observe the same transaction-bound entity manager.
                em = org.springframework.orm.jpa.SharedEntityManagerCreator.createSharedEntityManager(
                        context.getBean(EntityManagerFactory.class));
                transactions = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
                sites = context.getBean(SiteDao.class); appointments = context.getBean(OscarAppointmentDao.class);
                int[] ids = transactions.execute(status -> {
                    Site location = new Site(); location.setName(originalName); location.setShortName(tag);
                    location.setBgColor("#FFFFFF");
                    em.persist(location);
                    Appointment first = appointment(originalName); em.persist(first);
                    Appointment other = appointment(otherName); em.persist(other);
                    em.flush();
                    return new int[]{location.getId(), first.getId(), other.getId()};
                });
                site = ids[0]; renamed = ids[1]; unrelated = ids[2];
                // Keep the renamed appointment older, while the ordinary appointment's own
                // next-second version ensures a later edit compares strictly after clinic-now.
                sql.update("UPDATE appointment SET updatedatetime=? WHERE appointment_no=?",
                        new Date(System.currentTimeMillis() - 60_000), renamed);
            } catch (Exception | Error failure) {
                try { context.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                try { pool.close(); } catch (Exception cleanup) { failure.addSuppressed(cleanup); }
                restoreRegion();
                throw failure;
            }
        }

        private static Appointment appointment(String location) {
            var appointment = new Appointment(); appointment.setProviderNo("999998");
            appointment.setAppointmentDate(java.sql.Date.valueOf("2027-01-01"));
            appointment.setStartTime(java.sql.Time.valueOf("10:00:00"));
            appointment.setEndTime(java.sql.Time.valueOf("10:14:00")); appointment.setLocation(location);
            return appointment;
        }

        void rename() {
            try (var spring = mockStatic(SpringUtils.class)) {
                spring.when(() -> SpringUtils.getBean(ProviderSiteDao.class)).thenReturn(context.getBean(ProviderSiteDao.class));
                spring.when(() -> SpringUtils.getBean(ProviderDao.class)).thenReturn(mock(ProviderDao.class));
                transactions.executeWithoutResult(status -> {
                    Site changed = em.find(Site.class, site); em.detach(changed);
                    changed.setName(renamedName); sites.save(changed);
                });
            }
        }

        Date readRenameTime() {
            return sql.queryForObject("SELECT updatedatetime FROM appointment WHERE appointment_no=?", Date.class, renamed);
        }

        @Override public void close() {
            try { context.close(); }
            finally { restoreRegion(); }
        }

        private void restoreRegion() {
            if (previousRegion == null) CarlosProperties.getInstance().remove("billregion");
            else CarlosProperties.getInstance().put("billregion", previousRegion);
        }
    }

    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource, IsolatedJpaTestDatabase database) {
            var factory = new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(dataSource);
            factory.setManagedTypes(PersistenceManagedTypes.of(Site.class.getName(), Provider.class.getName(),
                    ProviderSite.class.getName(), Appointment.class.getName()));
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(database.jpaProperties());
            return factory;
        }
        @Bean PlatformTransactionManager transactions(EntityManagerFactory factory) { return new JpaTransactionManager(factory); }
        @Bean SiteDao siteDao() { return new SiteDaoImpl(); }
        @Bean ProviderSiteDao providerSites() { return new ProviderSiteDaoImpl(); }
        @Bean OscarAppointmentDao appointments() { return new OscarAppointmentDaoImpl(); }
    }
}
