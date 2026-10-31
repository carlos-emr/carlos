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
package io.github.carlos_emr.carlos.demographic.cdsimport.cli;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportAccessService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportJobService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsImportSubmissionService;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Properties;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.PropertiesPropertySource;

/** Actual shared-engine runtime; instantiated only after independent root-ticket verification. */
public final class CdsSpringRuntime implements CdsCliRuntime {
    private final AnnotationConfigApplicationContext context;
    private final CdsImportService service;
    private final CdsImportActorDto actor;
    private final CdsImportCommand command;
    private final String resolvedRunId;
    private final CdsDelegationVerifier.Delegation delegation;
    private final java.util.TimeZone previousTimeZone;

    public CdsSpringRuntime(CdsCliConfiguration configuration, CdsCliArguments arguments,
            CdsDelegationVerifier.Delegation delegation) throws Exception {
        this.delegation = delegation;
        previousTimeZone=java.util.TimeZone.getDefault();
        CdsCliLogging.configure();
        // Legacy native helpers still obtain properties and focused DAOs through
        // their standard singleton bridges. Set production selection before any
        // of those helpers initialize; caps never reaches this class's constructor.
        System.setProperty("carlos_override_properties", configuration.path().toString());
        Properties effective = new Properties();
        // Match the package-owned JDBC defaults loaded by applicationContext.xml;
        // an explicit operator property retains precedence. Ordinary WARs ship
        // the empty defaults file, Debian replaces it with its MariaDB default.
        try (InputStream input=CdsSpringRuntime.class.getResourceAsStream("/jdbc-defaults.properties")) {
            if (input==null) throw new CdsCliBootstrapException(null);
            byte[] bytes=input.readNBytes(65537);
            if (bytes.length>65536) throw new CdsCliBootstrapException(null);
            effective.load(new java.io.ByteArrayInputStream(bytes));
        }
        effective.putAll(configuration.properties());
        effective.setProperty("cds.import.worker.enabled", "false");
        // Native calendar/date helpers must use the same configured zone as
        // the instance. This dedicated one-shot JVM has no unrelated workers;
        // capability probes never create it or alter process timezone state.
        String zone=effective.getProperty("cds.import.clinicalZone", "").trim();
        if (!zone.isEmpty()) java.util.TimeZone.setDefault(java.util.TimeZone.getTimeZone(java.time.ZoneId.of(zone)));
        context = new AnnotationConfigApplicationContext();
        try {
            context.getEnvironment().setActiveProfiles("cds-import-cli");
            context.getEnvironment().getPropertySources().addFirst(new PropertiesPropertySource("cdsCli", effective));
            context.registerBean("cdsImportProperties", Properties.class, () -> effective);
            context.register(CdsCliSpringConfiguration.class);
            context.refresh();
            SpringUtils.setBeanFactory(context);
            service = context.getBean(CdsImportService.class);
            CdsImportAccessService access = context.getBean(CdsImportAccessService.class);
            if (arguments.submissionKey() != null) {
                var resolved = context.getBean(CdsImportSubmissionService.class)
                        .resolve(delegation.operator(), arguments.provider(), arguments.submissionKey());
                command = resolved.command(); actor = resolved.actor(); resolvedRunId = resolved.runId();
            } else {
                resolvedRunId = arguments.runId();
                if ("import".equals(arguments.operation())) command = arguments.command(delegation.nonce());
                else {
                    command = context.getBean(CdsImportJobService.class).load(arguments.runId()).command();
                    if (!command.providerNo().equals(arguments.provider())) throw new SecurityException("CDS_PROVIDER_BINDING_DENIED");
                }
                actor = access.delegatedActor(delegation.operator(), command);
            }
        } catch (Throwable failure) {
            try { context.close(); }
            catch (RuntimeException cleanupFailure) { failure.addSuppressed(cleanupFailure); }
            finally { java.util.TimeZone.setDefault(previousTimeZone); }
            if (failure instanceof Error fatal) throw fatal;
            if (failure instanceof SecurityException denied) throw denied;
            if (failure instanceof CdsImportException incomplete) throw incomplete;
            throw new CdsCliBootstrapException(CdsCliLogging.bootstrapFailure(effective, failure));
        }
    }

    @Override public String resolveRunId(String requested) { return resolvedRunId; }
    @Override public String accept() throws Exception { return service.accept(actor, command).id(); }
    @Override public void observe(String runId, String operation) throws Exception {
        service.observe(actor, runId, operation, delegation.operatorUid(), delegation.nonce());
    }
    @Override public void execute(String runId, boolean resume) throws Exception {
        if (resume) service.resumeAndWait(actor, runId); else service.runAccepted(actor, runId);
    }
    @Override public void cancel(String runId) throws Exception { service.cancel(actor, runId); }
    @Override public Map<String, Object> summary(String runId) throws Exception { return service.summary(actor, runId); }

    @Override public Map<String, Object> reports(String runId) throws Exception {
        Map<String, Object> selected = new LinkedHashMap<>();
        for (String type : new String[]{"event", "error", "receipt", "units"}) {
            // The facade authorizes and verifies selected manifests and file
            // hashes. Only these generated protected locations are advertised;
            // report contents and original source paths never go to general logs.
            Path path = service.report(actor, runId, type);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            try (InputStream input = new DigestInputStream(Files.newInputStream(path), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            selected.put(type, Map.of("protectedPath", path.toString(), "bytes", Files.size(path),
                    "sha256", HexFormat.of().formatHex(digest.digest())));
        }
        return Map.of("event", "PROTECTED_REPORTS", "runId", runId, "reports", selected);
    }

    @Override public void close() { try {context.close(); } finally {java.util.TimeZone.setDefault(previousTimeZone); } }
}
