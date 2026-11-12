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
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.CdsExportNoteAccessDao;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDao;
import io.github.carlos_emr.carlos.commn.dao.OscarLogDaoImpl;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathBeanDefinitionScanner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.core.type.filter.AssignableTypeFilter;

/** Both historical web bean names remain installed; export binds to the canonical owners. */
@Tag("unit")
@Tag("cds-export")
class CdsExportLegacyDaoWiringUnitTest {
    @Test
    void shouldResolveOneAuditOwner_whenTheLegacyXmlDefinitionPrecedesComponentScanning() {
        verifyAuditOwner(true);
    }

    @Test
    void shouldResolveOneAuditOwner_whenComponentScanningPrecedesTheLegacyXmlDefinition() {
        verifyAuditOwner(false);
    }

    @Test
    void shouldResolveTheSameAuditOwner_inTheIsolatedCliImportContext() {
        try (var context = auditContext()) {
            context.register(CliAuditConfiguration.class);
            context.refresh();
            assertThat(context.getBeanNamesForType(OscarLogDao.class))
                    .containsExactly("oscarLogDao");
            assertThat(context.getBean(CdsExportAuditPersister.class)).isNotNull();
        }
    }

    private static AnnotationConfigApplicationContext auditContext() {
        var context = new AnnotationConfigApplicationContext();
        context.setAllowBeanDefinitionOverriding(true);
        context.getBeanFactory()
                .registerSingleton("entityManagerFactory", mock(EntityManagerFactory.class));
        return context;
    }

    private static void verifyAuditOwner(boolean xmlFirst) {
        try (var context = auditContext()) {
            if (xmlFirst) registerLegacyAuditOwner(context);
            var scanner = new ClassPathBeanDefinitionScanner(context, false);
            scanner.addIncludeFilter(new AssignableTypeFilter(OscarLogDaoImpl.class));
            scanner.scan(OscarLogDaoImpl.class.getPackageName());
            if (!xmlFirst) registerLegacyAuditOwner(context);
            context.register(CdsExportAuditPersister.class);
            context.refresh();
            assertThat(context.getBeanNamesForType(OscarLogDao.class))
                    .containsExactly("oscarLogDao");
            assertThat(context.getBean(CdsExportAuditPersister.class)).isNotNull();
        }
    }

    private static void registerLegacyAuditOwner(AnnotationConfigApplicationContext context) {
        // applicationContext.xml's existing explicit bean keeps this canonical identity.
        context.registerBeanDefinition(
                "oscarLogDao", new RootBeanDefinition(OscarLogDaoImpl.class));
    }

    @Configuration(proxyBeanMethods = false)
    @Import({OscarLogDaoImpl.class, CdsExportAuditPersister.class})
    static class CliAuditConfiguration {}

    @Test
    void shouldUseCanonicalOwners_whenLegacyWebContextContainsDuplicateDaoTypes() throws Exception {
        var issues = mock(CaseManagementIssueDAO.class);
        var oldIssues = mock(CaseManagementIssueDAO.class);
        var notes = mock(CaseManagementNoteDAO.class);
        var oldNotes = mock(CaseManagementNoteDAO.class);
        var documents = mock(DocumentDao.class);
        var mergedDocuments = mock(DocumentDao.class);
        var scopes = mock(CdsExportNoteAccessDao.class);
        var projection = mock(CdsExportNoteScopeProjectionService.class);
        when(documents.countCdsExportRows(27)).thenReturn(5L);
        when(issues.countCdsExportRows(27)).thenReturn(3L);
        when(notes.countCdsExportIssueLinks(27)).thenReturn(4L);
        when(projection.read(27, 8192)).thenReturn(List.of());
        when(scopes.readScopes(27, 8192)).thenReturn(List.of());
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getBeanFactory().registerSingleton("documentDaoImpl", documents);
            context.getBeanFactory().registerSingleton("documentDao", mergedDocuments);
            context.getBeanFactory().registerSingleton("caseManagementIssueDAO", issues);
            context.getBeanFactory().registerSingleton("CaseManagementIssueDAO", oldIssues);
            context.getBeanFactory().registerSingleton("caseManagementNoteDAO", notes);
            context.getBeanFactory().registerSingleton("CaseManagementNoteDAO", oldNotes);
            context.getBeanFactory().registerSingleton("cdsExportNoteAccessDao", scopes);
            context.getBeanFactory()
                    .registerSingleton("cdsExportNoteScopeProjectionService", projection);
            context.register(
                    CdsExportCaseIssueSourceReader.class,
                    CdsExportNoteSourceReader.class,
                    CdsExportNoteIssueLinkSourceReader.class,
                    CdsExportDocumentSourceReader.class,
                    CdsExportDocumentFileSourceReader.class);
            context.refresh();
            assertThat(context.getBean(CdsExportDocumentSourceReader.class).count(27)).isEqualTo(5);
            assertThat(context.getBean(CdsExportDocumentFileSourceReader.class).count(27))
                    .isEqualTo(5);
            assertThat(context.getBean(CdsExportCaseIssueSourceReader.class).count(27))
                    .isEqualTo(3);
            assertThat(context.getBean(CdsExportNoteIssueLinkSourceReader.class).count(27))
                    .isEqualTo(4);
            var prepared =
                    context.getBean(CdsExportNoteSourceReader.class)
                            .forCapture(27, mock(CdsExportRunControl.class));
            assertThat(prepared.count(27)).isZero();
            verify(issues).countCdsExportRows(27);
            verify(notes).countCdsExportIssueLinks(27);
            verify(notes).countCdsExportRows(27);
            verify(documents, times(2)).countCdsExportRows(27);
            verifyNoInteractions(oldIssues, oldNotes, mergedDocuments);
        }
    }
}
