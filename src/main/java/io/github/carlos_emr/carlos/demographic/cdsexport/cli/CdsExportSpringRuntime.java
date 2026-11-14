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
package io.github.carlos_emr.carlos.demographic.cdsexport.cli;

import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessConfiguration;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessDelegationVerifier;
import io.github.carlos_emr.carlos.demographic.cds.cli.CdsProcessLogging;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportJobDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportSelectionFileService;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportService;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.IOException;
import java.util.List;
import java.util.Properties;
import java.util.TimeZone;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;

/** One-shot export context uses production configuration, the shared persistence factories and no background dispatchers. */
public final class CdsExportSpringRuntime implements CdsExportCliRuntime {
    private final AnnotationConfigApplicationContext context;
    private final CdsExportService service;
    private final CdsExportSelectionFileService selections;
    private final CdsExportCliArguments arguments;
    private final CdsProcessDelegationVerifier.Delegation delegation;
    private final TimeZone previousZone;
    private final String resolved;

    // Other threads only read this reference to call Thread's thread-safe interrupt API.
    @SuppressWarnings("java:S3077")
    private volatile Thread active;

    public CdsExportSpringRuntime(
            CdsProcessConfiguration configuration,
            CdsExportCliArguments arguments,
            CdsProcessDelegationVerifier.Delegation delegation)
            throws IOException {
        this.arguments = arguments;
        this.delegation = delegation;
        previousZone = TimeZone.getDefault();
        CdsProcessLogging.configure();
        System.setProperty("carlos_override_properties", configuration.path().toString());
        Properties effective = new Properties();
        try (var input =
                CdsExportSpringRuntime.class.getResourceAsStream("/jdbc-defaults.properties")) {
            if (input == null) throw new IOException("CDS_EXPORT_BOOTSTRAP_FAILED");
            byte[] bytes = input.readNBytes(65537);
            if (bytes.length > 65536) throw new IOException("CDS_EXPORT_BOOTSTRAP_FAILED");
            effective.load(new java.io.ByteArrayInputStream(bytes));
        }
        effective.putAll(configuration.properties());
        effective.setProperty("cds.import.worker.enabled", "false");
        effective.setProperty("cds.export.worker.enabled", "false");
        String zone = effective.getProperty("cds.export.clinicalZone", "").strip();
        if (SetOfWork.OPERATIONS.contains(arguments.operation()) && zone.isEmpty())
            throw new IllegalStateException("CDS_EXPORT_CLINICAL_ZONE_REQUIRED");
        if (!zone.isEmpty()) {
            try {
                TimeZone.setDefault(TimeZone.getTimeZone(java.time.ZoneId.of(zone)));
            } catch (java.time.DateTimeException invalid) {
                // Status, cancellation, diagnostics and retention do not interpret clinical
                // clocks. A malformed active zone must not strand those recovery commands.
                if (SetOfWork.OPERATIONS.contains(arguments.operation())) throw invalid;
            }
        }
        context = new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().setActiveProfiles("cds-export-cli");
            context.getEnvironment()
                    .getPropertySources()
                    .addFirst(new PropertiesPropertySource("cdsExportCli", effective));
            context.registerBean("cdsExportProperties", Properties.class, () -> effective);
            context.register(CdsExportCliSpringConfiguration.class);
            context.refresh();
            SpringUtils.setBeanFactory(context);
            service = context.getBean(CdsExportService.class);
            selections = context.getBean(CdsExportSelectionFileService.class);
            resolved =
                    arguments.operation().equals("status") && arguments.submissionKey() != null
                            ? service.submissionDelegated(
                                            delegation.operator(),
                                            arguments.provider(),
                                            arguments.submissionKey())
                                    .id()
                            : arguments.runId();
        } catch (Throwable failure) {
            try {
                context.close();
            } catch (RuntimeException cleanup) {
                failure.addSuppressed(cleanup);
            } finally {
                TimeZone.setDefault(previousZone);
            }
            if (failure instanceof Error fatal) throw fatal;
            if (failure instanceof RuntimeException denied) throw denied;
            if (failure instanceof IOException io) throw io;
            throw new IOException("CDS_EXPORT_BOOTSTRAP_FAILED");
        }
    }

    private static final class SetOfWork {
        private static final java.util.Set<String> OPERATIONS =
                java.util.Set.of("export", "resume");
    }

    @Override
    public String resolveRunId() {
        return resolved;
    }

    @Override
    public String accept() throws IOException {
        if (!service.qualifiedProfiles().contains(arguments.profile()))
            throw new IllegalStateException("CDS_EXPORT_PROFILE_NOT_QUALIFIED");
        List<Integer> patients =
                arguments.patientsFile() == null
                        ? List.of()
                        : selections.capture(arguments.patientsFile());
        return service.acceptDelegated(
                        delegation.operator(), arguments.command(patients, delegation.nonce()))
                .id();
    }

    @Override
    public CdsExportJobDto status(String runId) {
        return service.observeDelegated(delegation.operator(), arguments.provider(), runId);
    }

    @Override
    public void resume(String runId) {
        service.resumeDelegated(delegation.operator(), arguments.provider(), runId);
    }

    @Override
    public void cancel(String runId) {
        service.cancelDelegated(delegation.operator(), arguments.provider(), runId);
    }

    @Override
    public CdsExportJobDto execute(String runId) {
        active = Thread.currentThread();
        try {
            return service.executeDelegated(delegation.operator(), arguments.provider(), runId);
        } finally {
            active = null;
        }
    }

    @Override
    public long rendererHandoffMillis(String runId) {
        return service.rendererHandoffMillis(delegation.operator(), arguments.provider(), runId);
    }

    @Override
    public io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDiagnosticDto report(
            String runId) {
        return service.reportDelegated(delegation.operator(), arguments.provider(), runId);
    }

    @Override
    public void download(String runId) throws IOException {
        var actor =
                service.downloadActorDelegated(delegation.operator(), arguments.provider(), runId);
        try (var download =
                context.getBean(
                                io.github.carlos_emr.carlos.demographic.cdsexport.service
                                        .CdsExportArtifactService.class)
                        .open(actor, runId, arguments.artifactId())) {
            new CdsExportCliOutput().writeNew(arguments.output(), download::writeTo);
            download.complete();
        }
    }

    @Override
    public void interruptWait() {
        Thread waiting = active;
        if (waiting != null) waiting.interrupt();
    }

    @Override
    public void close() {
        try {
            context.close();
        } finally {
            TimeZone.setDefault(previousZone);
        }
    }
}
