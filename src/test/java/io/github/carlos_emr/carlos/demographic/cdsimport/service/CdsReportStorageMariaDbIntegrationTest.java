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

import io.github.carlos_emr.carlos.commn.dao.DocumentDaoImpl;
import io.github.carlos_emr.carlos.commn.model.CtlDocument;
import io.github.carlos_emr.carlos.commn.model.CtlDocumentPK;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.commn.model.DocumentReview;
import io.github.carlos_emr.carlos.commn.model.Provider;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

/** Actual Document lifecycle, merge and projection behavior on the complete synthetic Flyway schema. */
@Tag("integration")
@EnabledIfSystemProperty(named="cds.test.properties",matches=".+")
class CdsReportStorageMariaDbIntegrationTest {
    private static SessionFactory factory;
    private static final Date CLOCK=Date.from(Instant.parse("2024-01-02T03:04:05Z"));

    @BeforeAll
    static void openFactory() throws Exception {
        Properties jdbc=new Properties();
        try(var input=Files.newInputStream(Path.of(System.getProperty("cds.test.properties")))) { jdbc.load(input); }
        var source=new DriverManagerDataSource(jdbc.getProperty("jdbcUrl").replace("jdbc:mariadb:","jdbc:mysql:"),
                jdbc.getProperty("jdbcUser"),jdbc.getProperty("jdbcPassword"));
        try(var connection=source.getConnection(); var query=connection.createStatement();
                var rows=query.executeQuery("SELECT DATABASE(),VERSION()")) {
            assertThat(rows.next()).isTrue();
            assertThat(rows.getString(1)).matches("[A-Za-z0-9_]*(test|fixture)[A-Za-z0-9_]*");
            assertThat(rows.getString(2)).contains("MariaDB");
        }
        try(var connection=source.getConnection(); var query=connection.createStatement();
                var rows=query.executeQuery("SELECT IS_NULLABLE,CHARACTER_MAXIMUM_LENGTH FROM information_schema.COLUMNS "
                        +"WHERE TABLE_SCHEMA=DATABASE() AND TABLE_NAME='document' AND COLUMN_NAME='cds_content_precision'")) {
            assertThat(rows.next()).as("Actual migration75 is required").isTrue();
            assertThat(rows.getString(1)).isEqualTo("YES"); assertThat(rows.getLong(2)).isGreaterThanOrEqualTo(16);
        }
        Configuration configuration=new Configuration();
        for(Class<?> type:List.of(Document.class,DocumentReview.class,Provider.class,CtlDocument.class,CtlDocumentPK.class))
            configuration.addAnnotatedClass(type);
        configuration.getProperties().put("hibernate.connection.datasource",source);
        configuration.setProperty("hibernate.dialect","org.hibernate.dialect.MariaDBDialect");
        configuration.setProperty("hibernate.hbm2ddl.auto","none");
        configuration.setProperty("hibernate.boot.allow_jdbc_metadata_access","false");
        configuration.setProperty("hibernate.jdbc.time_zone","UTC");
        factory=configuration.buildSessionFactory();
    }
    @AfterAll static void closeFactory() { if(factory!=null) factory.close(); }

    @Test
    void shouldKeepContentTimestampNull_whenImportedSourceHasNoExactClock() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session);
                for(String precision:List.of("UNKNOWN","YEAR","MONTH","FULL_DATE")) {
                    Document value=document(precision,null); session.persist(value); session.flush(); int id=value.getId(); session.clear();
                    Document read=session.find(Document.class,id);
                    assertThat(read.getCdsContentPrecision()).isEqualTo(precision);
                    assertThat(read.getContentdatetime()).isNull(); assertThat(read.getUpdatedatetime()).isNotNull();
                    assertThat(read.getReviewer()).isEmpty(); assertThat(read.getReviewdatetime()).isNull();
                }
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldRetainTheProvidedTimestamp_whenImportedSourceHasAnExactClock() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session);
                for(String precision:List.of("LOCAL_DATE_TIME","OFFSET_DATE_TIME")) {
                    Document value=document(precision,CLOCK); session.persist(value); session.flush(); int id=value.getId(); session.clear();
                    Document read=session.find(Document.class,id);
                    assertThat(read.getCdsContentPrecision()).isEqualTo(precision);
                    assertThat(read.getContentdatetime()).isEqualTo(CLOCK);
                }
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldRetainOrdinaryNativeTimestampFallback_whenNoImportMarkerExists() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session); long before=System.currentTimeMillis();
                Document value=document(null,null); session.persist(value); session.flush(); int id=value.getId(); session.clear();
                Document read=session.find(Document.class,id);
                assertThat(read.getCdsContentPrecision()).isNull();
                assertThat(read.getContentdatetime().getTime()).isBetween(before-1000,System.currentTimeMillis()+1000);
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldPreserveSourcePrecisionAndUnknownTime_whenMetadataIsEditedAfterReload() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session); Document value=document("UNKNOWN",null); session.persist(value); session.flush(); int id=value.getId(); session.clear();
                Document read=session.find(Document.class,id); read.setDocdesc("Unrelated metadata edit"); session.flush(); session.clear();
                Document edited=session.find(Document.class,id);
                assertThat(edited.getCdsContentPrecision()).isEqualTo("UNKNOWN"); assertThat(edited.getContentdatetime()).isNull();
                assertThat(edited.getDocdesc()).isEqualTo("Unrelated metadata edit");
                assertThatThrownBy(()->edited.setCdsContentPrecision(null)).hasMessage("CDS_CONTENT_PRECISION_IMMUTABLE");
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldRetainTheInsertOnlyMarker_whenDetachedLegacyMetadataOmitsIt() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session); Document original=document("YEAR",null); session.persist(original); session.flush(); int id=original.getId(); session.clear();
                Document detached=document(null,null); detached.setDocumentNo(id); detached.setDocdesc("Detached metadata edit");
                Document merged=session.merge(detached); session.flush();
                assertThat(merged.getCdsContentPrecision()).isEqualTo("YEAR"); assertThat(merged.getContentdatetime()).isNull();
                session.clear(); Document reloaded=session.find(Document.class,id);
                assertThat(reloaded.getCdsContentPrecision()).isEqualTo("YEAR"); assertThat(reloaded.getContentdatetime()).isNull();
            } finally { transaction.rollback(); }
        }
    }

    @Test
    void shouldExposePrecisionInActualLightweightQuery_whenImportedDocumentIsListed() {
        try(Session session=factory.openSession()) {
            var transaction=session.beginTransaction();
            try {
                strict(session); Document original=document("FULL_DATE",null);
                original.setObservationdate(java.sql.Date.valueOf("2024-01-02")); session.persist(original); session.flush();
                CtlDocument link=new CtlDocument(); link.setId(new CtlDocumentPK("demographic",999998,original.getId())); link.setStatus("A");
                session.persist(link); session.flush(); session.clear();
                var dao=new DocumentDaoImpl(); ReflectionTestUtils.setField(dao,"entityManager",session);
                assertThat(dao.findDocumentDTOsByDemographicNo(999998)).anySatisfy(dto->{
                    assertThat(dto.getDocumentNo()).isEqualTo(original.getId());
                    assertThat(dto.getCdsContentPrecision()).isEqualTo("FULL_DATE"); assertThat(dto.getContentdatetime()).isNull();
                });
            } finally { transaction.rollback(); }
        }
    }

    private static Document document(String precision,Date clock) {
        Document value=new Document(); value.setDoctype("CDS"); value.setDocdesc("Synthetic report "+UUID.randomUUID());
        value.setDocfilename("synthetic-not-published.txt"); value.setDoccreator("T099"); value.setResponsible("");
        value.setStatus(Document.STATUS_ACTIVE); value.setContenttype("text/plain;charset=UTF-8");
        value.setReviewer(""); value.setNumberofpages(0); value.setCdsContentPrecision(precision); value.setContentdatetime(clock);
        return value;
    }
    private static void strict(Session session) {
        session.doWork(connection->{try(var statement=connection.createStatement()) {statement.execute("SET SESSION sql_mode='STRICT_ALL_TABLES'");}});
    }
}
