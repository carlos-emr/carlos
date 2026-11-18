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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.AllergyDaoImpl;
import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDaoImpl;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAppointmentLookupsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsAppointmentNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsProviderMatchDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsAppointmentPersister;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportConfigurationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsNativePreparationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsProviderResolver;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.TreeMap;
import javax.sql.DataSource;
import org.apache.commons.dbcp2.BasicDataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.persistenceunit.PersistenceManagedTypes;
import org.springframework.orm.jpa.support.PersistenceAnnotationBeanPostProcessor;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The CARLOS source carriers of an allergy and an appointment through rows MariaDB actually
 * stores: read by the owning DAO's export capture read, mapped, written into a schema-valid
 * patient document, read by the CARLOS importer and, for the appointment, written back to
 * MariaDB by the importer's persister and read again. A values-in, values-out proof that the
 * carrier loses nothing between two CARLOS installations.
 */
@Tag("integration")
@Tag("cds-export")
@EnabledIfSystemProperty(named = "cds.export.test.properties", matches = ".+")
class CdsExportSourceCarrierMariaDbIntegrationTest {
    @Configuration
    @EnableTransactionManagement(proxyTargetClass = true)
    static class Config {}

    private static final String HASH = "a".repeat(64), PATIENT_KEY = "b".repeat(64);
    @TempDir Path directory;
    private BasicDataSource source;
    private AnnotationConfigApplicationContext context;
    private JdbcTemplate sql;
    private TransactionTemplate read, write;
    private final int patient =
            610_000_000 + java.util.concurrent.ThreadLocalRandom.current().nextInt(40_000_000);

    @BeforeEach
    void connect() throws Exception {
        var properties = new Properties();
        try (var input =
                Files.newInputStream(Path.of(System.getProperty("cds.export.test.properties")))) {
            properties.load(input);
        }
        if (!properties
                        .getProperty("jdbcUrl", "")
                        .matches("jdbc:mariadb://[^/]+/cds_export_test(?:\\?.*)?")
                || !"true".equals(properties.getProperty("ownedDisposableDatabase")))
            throw new IllegalArgumentException("Owned CDS database required");
        source = new BasicDataSource();
        source.setMaxTotal(4);
        source.setUrl(properties.getProperty("jdbcUrl"));
        source.setUsername(properties.getProperty("jdbcUser"));
        source.setPassword(properties.getProperty("jdbcPassword"));
        sql = new JdbcTemplate(source);
        assertThat(sql.queryForObject("select version()", String.class)).contains("MariaDB");
        context = new AnnotationConfigApplicationContext();
        context.register(Config.class);
        context.registerBean("dataSource", DataSource.class, () -> source);
        context.registerBean(
                "entityManagerFactory",
                LocalContainerEntityManagerFactoryBean.class,
                () -> {
                    var factory = new LocalContainerEntityManagerFactoryBean();
                    factory.setDataSource(source);
                    factory.setPersistenceUnitName("entityManagerFactory");
                    factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
                    factory.setManagedTypes(
                            PersistenceManagedTypes.of(
                                    Allergy.class.getName(), Appointment.class.getName()));
                    factory.setJpaPropertyMap(
                            Map.of(
                                    "hibernate.hbm2ddl.auto",
                                    "none",
                                    "hibernate.dialect",
                                    "org.hibernate.dialect.MariaDBDialect"));
                    return factory;
                });
        context.registerBean(
                "transactionManager",
                PlatformTransactionManager.class,
                () ->
                        new JpaTransactionManager(
                                context.getBean(jakarta.persistence.EntityManagerFactory.class)));
        context.registerBean(PersistenceAnnotationBeanPostProcessor.class);
        context.register(AllergyDaoImpl.class, OscarAppointmentDaoImpl.class);
        context.refresh();
        read = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        read.setReadOnly(true);
        read.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);
        read.setTimeout(120);
        write = new TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        write.setTimeout(30);
    }

    @AfterEach
    void removeOwnedRows() throws Exception {
        try {
            if (sql != null) {
                sql.update(
                        "delete from allergies where demographic_no in (?,?)", patient, patient + 1);
                sql.update(
                        "delete from appointment where demographic_no in (?,?)",
                        patient,
                        patient + 1);
            }
        } finally {
            try {
                if (context != null) context.close();
            } finally {
                if (source != null) source.close();
            }
        }
    }

    @Test
    void shouldCarryAStoredAllergyRowWithValuesCdsHasNoElementFor_throughExportAndImport()
            throws Exception {
        // An allergy as an OSCAR 19 migration leaves it: a recording provider and First Databank
        // identifiers that no CARLOS screen writes and CDS 5.2 has no element for.
        sql.update(
                "insert into allergies(allergyid,demographic_no,DESCRIPTION,TYPECODE,archived,"
                        + "position,lastUpdateDate,providerNo,HICL_SEQNO,HIC_SEQNO,AGCSP,AGCCS,"
                        + "reaction,severity_of_reaction,start_date)"
                        + " values(?,?,'Synthetic agent',0,0,0,'2024-02-29 01:02:03','999998',"
                        + "11,22,33,44,'Synthetic café reaction','2','2020-02-29')",
                patient,
                patient);
        var row =
                row(
                        "allergies",
                        read.execute(
                                status ->
                                        context.getBean(AllergyDaoImpl.class)
                                                .findCdsExportRowsAfter(patient, null, 4)));
        assertThat(row.fields()).containsEntry("providerNo", "999998");
        var mapped = new CdsExportAllergyMapper().map(patient, row, List.of());
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        String carrier = mapped.value().sourceContext();
        assertThat(carrier).isNotNull();
        var received =
                transfer(
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS),
                        List.of(
                                new CdsExportXmlComposer.Section(
                                        new CdsExportIdentityDto(
                                                PATIENT_KEY,
                                                CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS,
                                                "c".repeat(64)),
                                        xml ->
                                                new CdsExportAllergyXmlWriter()
                                                        .write(mapped.value(), xml))),
                        "allergy");
        var record = section(received, "AllergiesAndAdverseReactions");
        var parser = new CdsFieldParser();
        // The importer reads the allergy from its standard elements...
        var allergy = new CdsClinicalNormalizationService(parser).allergy(record);
        assertThat(allergy.description()).isEqualTo("Synthetic agent");
        assertThat(allergy.reaction()).isEqualTo("Synthetic café reaction");
        // ...and receives the carrier unchanged, with every stored column exactly.
        String content = residuals(record).get(CdsNativeExtensionContract.ALLERGY_SOURCE_CONTEXT);
        assertThat(content).isEqualTo(carrier);
        var stored = new com.fasterxml.jackson.databind.ObjectMapper().readTree(content).get("fields");
        row.fields()
                .forEach(
                        (column, value) -> {
                            assertThat(stored.has(column)).as(column).isTrue();
                            if (value == null) assertThat(stored.get(column).isNull()).isTrue();
                            else assertThat(stored.get(column).asText()).isEqualTo(value);
                        });
        assertThat(stored.get("HICL_SEQNO").asText()).isEqualTo("11");
    }

    @Test
    void shouldRestoreAStoredAppointmentExactly_inAnotherChartThroughExportAndImport()
            throws Exception {
        // A booking as the add form stores it: 23:30 for 60 minutes (end 00:29), with the
        // scheduler values CDS has no field for, a status code with no definition, a provider
        // number with no provider record and notes longer than the CDS element.
        String notes = "Synthetic café note ".repeat(1620);
        assertThat(notes.codePointCount(0, notes.length())).isBetween(32001, 32768);
        sql.update(
                "insert into appointment(appointment_no,provider_no,appointment_date,start_time,"
                        + "end_time,name,demographic_no,program_id,notes,reason,location,resources,"
                        + "type,style,billing,status,urgency,bookingSource,createdatetime,"
                        + "updatedatetime,creator,lastupdateuser)"
                        + " values(?,'GONE','2026-11-02','23:30:00','00:29:00','Fixture,Synthetic',"
                        + "?,0,?,'Follow up','Main Clinic','Room 2','Follow-up visit','1','B','ZZ',"
                        + "'critical','OSCAR','2026-10-01 09:00:00','2026-10-01 09:00:00',"
                        + "'999998','999998')",
                patient,
                patient,
                notes);
        var appointments = context.getBean(OscarAppointmentDaoImpl.class);
        var row =
                row(
                        "appointment",
                        read.execute(
                                status -> appointments.findCdsExportRowsAfter(patient, null, 4)));
        var lookups =
                new CdsExportAppointmentLookupsDto(HASH, Map.of(), Map.of(), Map.of(), Set.of());
        var mapped =
                new CdsExportAppointmentMapper()
                        .map(
                                patient,
                                row,
                                lookups,
                                ZoneId.of("America/Toronto"),
                                demographicRow());
        // Not held: each of these used to hold the patient.
        assertThat(mapped.nativeRowRepresentable()).isTrue();
        var carrier = CdsExportAppointmentSourceMapper.map(1, row, lookups, mapped.complete());
        var received =
                transfer(
                        Set.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.APPOINTMENTS,
                                CdsCategory.NEW_CATEGORY),
                        List.of(
                                new CdsExportXmlComposer.Section(
                                        new CdsExportIdentityDto(
                                                PATIENT_KEY,
                                                CdsCategory.APPOINTMENTS,
                                                "c".repeat(64)),
                                        xml ->
                                                new CdsExportAppointmentXmlWriter()
                                                        .write(mapped.value(), xml)),
                                new CdsExportXmlComposer.Section(
                                        new CdsExportIdentityDto(
                                                PATIENT_KEY,
                                                CdsCategory.NEW_CATEGORY,
                                                "e".repeat(64)),
                                        xml -> CdsExportAppointmentSourceMapper.write(carrier, xml))),
                        "appointment");
        var element = section(received, "Appointments");
        var parser = new CdsFieldParser();
        // What any receiver reads.
        assertThat(parser.firstText(element, "AppointmentTime", 64)).isEqualTo("23:30:00-05:00");
        assertThat(parser.firstText(element, "Duration", 8)).isEqualTo("60");
        assertThat(parser.firstText(element, "AppointmentStatus", 250)).isEqualTo("ZZ");
        assertThat(parser.firstText(element, "Provider/Name/LastName", 50)).isNull();
        assertThat(parser.firstText(element, "AppointmentNotes", 32768))
                .endsWith(CdsExportAppointmentMapper.SHORTENED_MARK);
        // The receiving CARLOS: the real importer persister writes the row to MariaDB.
        var settings = new Properties();
        settings.setProperty("cds.import.clinicalZone", "America/Toronto");
        var providers = mock(CdsProviderResolver.class);
        when(providers.resolve(any(), any(), any(), any(), anyBoolean()))
                .thenReturn(
                        new CdsProviderMatchDto(
                                null, CdsProviderMatchDto.Outcome.SOURCE_IDENTITY_ABSENT));
        var preparation = mock(CdsNativePreparationService.class);
        var demographics = mock(io.github.carlos_emr.carlos.commn.dao.DemographicDao.class);
        var chart = new io.github.carlos_emr.carlos.commn.model.Demographic();
        chart.setLastName("Fixture");
        chart.setFirstName("Synthetic");
        when(demographics.getDemographicById(patient + 1)).thenReturn(chart);
        var importer =
                new CdsAppointmentPersister(
                        parser,
                        new CdsClinicalNormalizationService(parser),
                        new CdsImportConfigurationService(settings),
                        providers,
                        preparation,
                        appointments,
                        demographics);
        var command =
                new CdsImportCommand(
                        directory,
                        "owned-submission",
                        "999998",
                        null,
                        null,
                        "clinic",
                        "pcon-2024-02",
                        false,
                        false,
                        true,
                        0,
                        null,
                        false);
        importer.prepare("run", 1, "unit", element, command);
        var prepared = ArgumentCaptor.forClass(Object.class);
        verify(preparation)
                .store(eq("run"), eq(1L), eq("unit"), anyString(), eq(command), prepared.capture());
        var dto = (CdsAppointmentNativeDto) prepared.getValue();
        assertThat(dto.representable()).isTrue();
        when(preparation.load(
                        "run", 1, "unit", element.sha256(), command, CdsAppointmentNativeDto.class))
                .thenReturn(dto);
        var written =
                write.execute(
                        status -> {
                            try {
                                return importer.write(
                                        "run", 1, "unit", patient + 1, element, command, n -> {});
                            } catch (java.io.IOException failure) {
                                throw new java.io.UncheckedIOException(failure);
                            }
                        });
        assertThat(written).containsKey("appointment");
        var restored =
                row(
                        "appointment",
                        read.execute(
                                status -> appointments.findCdsExportRowsAfter(patient + 1, null, 4)));
        // The exact stored slot, the complete notes and the scheduler context, as stored by the
        // sender.
        for (String column :
                List.of(
                        "appointment_date",
                        "start_time",
                        "end_time",
                        "notes",
                        "location",
                        "resources",
                        "type",
                        "style",
                        "billing",
                        "urgency",
                        "bookingSource"))
            assertThat(restored.fields().get(column))
                    .as(column)
                    .isEqualTo(row.fields().get(column));
        // By decision the status arrives as the sender's status text, not as a code.
        assertThat(restored.fields().get("status")).isEmpty();
        assertThat(restored.fields().get("imported_status")).isEqualTo("ZZ");
    }

    /**
     * The captured row as text. A value larger than the capture read returns inline is fetched
     * by its key and must have the byte length and digest the capture read witnessed.
     */
    private CdsExportCapturedTextRowDto row(String table, List<CdsExportRawRowDto> rows)
            throws Exception {
        assertThat(rows).hasSize(1);
        var captured = rows.getFirst();
        String key = table.equals("allergies") ? "allergyid" : "appointment_no";
        var text = new LinkedHashMap<String, String>();
        for (var entry : captured.cells().entrySet()) {
            var cell = entry.getValue();
            if (cell.sqlNull()) {
                text.put(entry.getKey(), null);
                continue;
            }
            byte[] bytes = cell.inlineBytes();
            if (bytes == null) {
                assertThat(entry.getKey()).isEqualTo("notes");
                bytes =
                        sql.queryForObject(
                                        "select notes from appointment where appointment_no=?",
                                        String.class,
                                        captured.id())
                                .getBytes(StandardCharsets.UTF_8);
                assertThat((long) bytes.length).isEqualTo(cell.bytes());
                assertThat(
                                java.util.HexFormat.of()
                                        .formatHex(
                                                java.security.MessageDigest.getInstance("SHA-256")
                                                        .digest(bytes)))
                        .isEqualTo(cell.sha256());
            }
            text.put(entry.getKey(), new String(bytes, StandardCharsets.UTF_8));
        }
        assertThat(text.get(key)).isEqualTo(Long.toString(captured.id()));
        return new CdsExportCapturedTextRowDto(
                table, CdsExportPatientTableSourceReader.key(captured.id()), text);
    }

    private CdsExportCapturedTextRowDto demographicRow() {
        var fields = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> fields.put(column, null));
        fields.put("demographic_no", Integer.toString(patient));
        fields.put("first_name", "Synthetic");
        fields.put("last_name", "Fixture");
        return new CdsExportCapturedTextRowDto(
                "demographic", String.format(java.util.Locale.ROOT, "%010d", patient), fields);
    }

    /** Composes the patient document, passes it through the package's XML gate, and parses it. */
    private List<CdsRecordDto> transfer(
            Set<CdsCategory> selected, List<CdsExportXmlComposer.Section> clinical, String name)
            throws Exception {
        var demographic = new TreeMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> demographic.put(column, null));
        demographic.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var mapped =
                new CdsExportDemographicMapper()
                        .map(new CdsExportDemographicRowDto(27, demographic));
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(
                                PATIENT_KEY, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().write(mapped, xml)));
        sections.addAll(clinical);
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .compose(
                        output,
                        HASH,
                        PATIENT_KEY,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        8L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        ignored -> {},
                        () -> {});
        var folder = Files.createTempDirectory(directory, name + "-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(folder.resolve("patient.xml"), output.toByteArray()),
                        folder.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        return records;
    }

    private static CdsRecordDto section(List<CdsRecordDto> records, String name) {
        return records.stream().filter(r -> r.section().equals(name)).findFirst().orElseThrow();
    }

    /** Every residual DataElement of a received record, name to content. */
    private static Map<String, String> residuals(CdsRecordDto record) throws Exception {
        var parser = new CdsFieldParser();
        var names = new ArrayList<String>();
        var contents = new ArrayList<String>();
        parser.forEachField(
                record,
                field -> {
                    try {
                        String path = CdsFieldParser.unindexed(field.path());
                        if (path.equals("ResidualInfo/DataElement/Name"))
                            names.add(parser.readToken(record, field, 128));
                        if (path.equals("ResidualInfo/DataElement/Content"))
                            contents.add(parser.readToken(record, field, 1 << 20));
                    } catch (java.io.IOException invalid) {
                        throw new java.io.UncheckedIOException(invalid);
                    }
                });
        var result = new LinkedHashMap<String, String>();
        for (int i = 0; i < names.size(); i++) result.put(names.get(i), contents.get(i));
        return result;
    }
}
