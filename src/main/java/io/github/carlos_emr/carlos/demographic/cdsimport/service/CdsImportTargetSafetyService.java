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

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsJdbcAdmissionGuard;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Finite native writer catalogue gate. Verification holds metadata locks until owner close. */
@Service
public class CdsImportTargetSafetyService {
    private static final int MAXIMUM_TARGETS=256;
    private static final int MAXIMUM_TRIGGERS=1024;
    private static final ObjectMapper JSON=new ObjectMapper(JsonFactory.builder()
            .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION).build());
    private final CdsImportConfigurationService configuration;
    private final Set<String> required;

    @Autowired
    public CdsImportTargetSafetyService(CdsImportConfigurationService configuration) {
        this(configuration, productionTargets());
    }

    /** Fixture-only target set; production always uses the canonical native writer catalogue. */
    CdsImportTargetSafetyService(CdsImportConfigurationService configuration, Set<String> required) {
        this.configuration=configuration; this.required=Set.copyOf(required);
    }

    private static Set<String> productionTargets() {
        Set<String> targets=new LinkedHashSet<>(CdsTargetReceiptService.targetTables());
        targets.addAll(CdsJdbcAdmissionGuard.administrativeTables());
        targets.addAll(Set.of("ctl_document", "casemgmt_issue_notes", "log", "demographicArchive", "appointmentArchive"));
        return Set.copyOf(targets);
    }

    /** Must execute under the drained owner before replay verification or any native unit. */
    public void verify(CdsImportMaintenanceService.Owner owner) { owner.verifyTargets(this); }

    /** Only the maintenance owner can supply its private physical connection. */
    void verifyAndPin(Connection connection) {
        try {
            connection.setAutoCommit(false);
            var catalogue=tables(connection);
            Set<String> targets=resolveTargets(catalogue);
            for (String table:targets) {
                try (var statement=connection.createStatement()) {
                    statement.setQueryTimeout(10);
                    try (var ignored=statement.executeQuery("SELECT 1 FROM "+quote(table)+" LIMIT 0")) {
                        // Transaction-duration metadata lock; no patient rows or row locks.
                    }
                }
            }
            // Re-read engines after metadata pins, rather than trusting a pre-pin DDL snapshot.
            catalogue=tables(connection);
            if (!targets.containsAll(resolveTargets(catalogue))) throw problem("IMPORT_TARGET_CATALOGUE_CHANGED");
            for (String table:targets) {
                if (!"InnoDB".equals(catalogue.get(table))) throw problem("IMPORT_TARGET_ENGINE_UNSUPPORTED");
            }
            verifyTriggers(connection, targets);
            // Deliberately no commit: MDL pins remain on the dedicated owner connection.
        } catch (SQLException failure) {
            rollback(connection); throw problem("IMPORT_TARGET_CATALOGUE_UNAVAILABLE");
        } catch (RuntimeException failure) {
            rollback(connection); throw failure;
        }
    }

    private Map<String, String> tables(Connection connection) throws SQLException {
        Map<String, String> found=new HashMap<>();
        try (var query=connection.prepareStatement("SELECT TABLE_NAME,ENGINE FROM information_schema.TABLES "
                + "WHERE TABLE_SCHEMA=DATABASE() AND TABLE_TYPE='BASE TABLE'")) {
            query.setQueryTimeout(10);
            try (var rows=query.executeQuery()) { while (rows.next()) found.put(rows.getString(1), rows.getString(2)); }
        }
        return found;
    }

    private Set<String> resolveTargets(Map<String, String> catalogue) {
        Set<String> targets=new java.util.TreeSet<>();
        for (String logical:required) {
            boolean present=false;
            for (String physical:catalogue.keySet()) {
                // Receipt keys include historical aliases differing only by table-name case.
                if (logical.equalsIgnoreCase(physical)) { targets.add(physical); present=true; }
            }
            if (!present) throw problem("IMPORT_TARGET_TABLE_MISSING");
        }
        for (String physical:catalogue.keySet()) {
            if (physical.toLowerCase(Locale.ROOT).startsWith("cds_import_")) targets.add(physical);
        }
        if (targets.size()>MAXIMUM_TARGETS) throw problem("IMPORT_TARGET_CATALOGUE_REQUIRES_REVIEW");
        return targets;
    }

    private void verifyTriggers(Connection connection, Set<String> targets) throws SQLException {
        requireTriggerVisibility(connection, targets);
        List<Trigger> triggers=new ArrayList<>();
        try (var query=connection.prepareStatement("""
                SELECT TRIGGER_SCHEMA,TRIGGER_NAME,EVENT_OBJECT_TABLE,EVENT_MANIPULATION,ACTION_TIMING,
                    ACTION_STATEMENT,SQL_MODE,DEFINER,CHARACTER_SET_CLIENT,COLLATION_CONNECTION,
                    DATABASE_COLLATION,ACTION_ORDER
                FROM information_schema.TRIGGERS WHERE TRIGGER_SCHEMA=DATABASE()
                """)) {
            query.setQueryTimeout(10);
            query.setFetchSize(Integer.MIN_VALUE); // Connector/J streams definitions instead of buffering a catalogue of bodies.
            try (var rows=query.executeQuery()) {
                while (rows.next()) {
                    if (!targets.contains(rows.getString(3))) continue;
                    if (triggers.size()>=MAXIMUM_TRIGGERS) throw problem("IMPORT_TARGET_TRIGGER_REQUIRES_REVIEW");
                    triggers.add(new Trigger(rows.getString(1)+"."+rows.getString(2), triggerDigest(rows)));
                }
            }
        }
        if (triggers.isEmpty()) return;
        Map<String, String> approved=approvals();
        for (Trigger trigger:triggers) {
            String expected=approved.get(trigger.key());
            if (expected==null || !MessageDigest.isEqual(HexFormat.of().parseHex(expected), HexFormat.of().parseHex(trigger.digest()))) {
                throw problem("IMPORT_TARGET_TRIGGER_APPROVAL_REQUIRED");
            }
        }
    }

    /** An empty TRIGGERS result is proof only when this account can see every target's triggers. */
    private void requireTriggerVisibility(Connection connection, Set<String> targets) throws SQLException {
        String account;
        try (var query=connection.createStatement(); var rows=query.executeQuery("SELECT CURRENT_USER()")) {
            if (!rows.next()) throw problem("IMPORT_TARGET_TRIGGER_VISIBILITY_REQUIRED");
            account=rows.getString(1);
        }
        int separator=account.lastIndexOf('@');
        if (separator<0) throw problem("IMPORT_TARGET_TRIGGER_VISIBILITY_REQUIRED");
        String grantee="'"+account.substring(0, separator)+"'@'"+account.substring(separator+1)+"'";
        try (var query=connection.prepareStatement("""
                SELECT 1 FROM information_schema.USER_PRIVILEGES WHERE GRANTEE=? AND PRIVILEGE_TYPE='TRIGGER'
                UNION ALL SELECT 1 FROM information_schema.SCHEMA_PRIVILEGES
                WHERE GRANTEE=? AND DATABASE() LIKE TABLE_SCHEMA ESCAPE ? AND PRIVILEGE_TYPE='TRIGGER'
                """)) {
            query.setQueryTimeout(10); query.setString(1, grantee); query.setString(2, grantee); query.setString(3, "\\");
            try (var rows=query.executeQuery()) { if (rows.next()) return; }
        }
        Set<String> visible=new java.util.HashSet<>();
        try (var query=connection.prepareStatement("SELECT TABLE_NAME FROM information_schema.TABLE_PRIVILEGES "
                + "WHERE GRANTEE=? AND TABLE_SCHEMA=DATABASE() AND PRIVILEGE_TYPE='TRIGGER'")) {
            query.setQueryTimeout(10); query.setString(1, grantee);
            try (var rows=query.executeQuery()) { while (rows.next()) visible.add(rows.getString(1)); }
        }
        if (!visible.containsAll(targets)) throw problem("IMPORT_TARGET_TRIGGER_VISIBILITY_REQUIRED");
    }

    private record Trigger(String key, String digest) { }

    /** SHA-256 of twelve ordered nullable UTF-8 fields, each prefixed by a signed 4-byte length. */
    static String triggerDigest(ResultSet rows) throws SQLException {
        try {
            MessageDigest digest=MessageDigest.getInstance("SHA-256");
            for (int index=1; index<=12; index++) {
                String value=rows.getString(index);
                if (value!=null && value.length()>1_048_576) throw problem("IMPORT_TARGET_TRIGGER_REQUIRES_REVIEW");
                byte[] bytes=value==null ? null : value.getBytes(StandardCharsets.UTF_8);
                int length=bytes==null ? -1 : bytes.length;
                digest.update(new byte[]{(byte) (length>>>24), (byte) (length>>>16), (byte) (length>>>8), (byte) length});
                if (bytes!=null) digest.update(bytes);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(impossible); }
    }

    private Map<String, String> approvals() {
        String configured=configuration.value("cds.import.approvedTriggerManifest", "");
        if (configured.isBlank()) throw problem("IMPORT_TARGET_TRIGGER_APPROVAL_REQUIRED");
        try {
            Path path=Path.of(configured);
            if (!path.isAbsolute() || !path.equals(path.normalize())) throw problem("IMPORT_TRIGGER_MANIFEST_UNTRUSTED");
            for (Path element=path; element!=null; element=element.getParent()) {
                if (Files.isSymbolicLink(element)
                        || ((Number) Files.getAttribute(element, "unix:uid", LinkOption.NOFOLLOW_LINKS)).longValue()!=0) {
                    throw problem("IMPORT_TRIGGER_MANIFEST_UNTRUSTED");
                }
                Set<PosixFilePermission> mode=Files.getPosixFilePermissions(element, LinkOption.NOFOLLOW_LINKS);
                if (mode.contains(PosixFilePermission.GROUP_WRITE) || mode.contains(PosixFilePermission.OTHERS_WRITE)) {
                    throw problem("IMPORT_TRIGGER_MANIFEST_UNTRUSTED");
                }
            }
            if (!Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS) || Files.size(path)>1_048_576) throw problem("IMPORT_TRIGGER_MANIFEST_UNTRUSTED");
            var root=JSON.readTree(Files.readString(path, StandardCharsets.UTF_8));
            if (!root.isObject() || root.size()!=2 || root.path("format").asInt()!=1 || !root.path("approvedTriggers").isObject()) {
                throw problem("IMPORT_TRIGGER_MANIFEST_INVALID");
            }
            Map<String, String> approved=new HashMap<>();
            var fields=root.path("approvedTriggers").fields();
            while (fields.hasNext()) {
                var entry=fields.next();
                if (approved.size()>=MAXIMUM_TRIGGERS || entry.getKey().length()>256 || !entry.getValue().isTextual()
                        || !entry.getValue().asText().matches("[0-9a-f]{64}")) throw problem("IMPORT_TRIGGER_MANIFEST_INVALID");
                approved.put(entry.getKey(), entry.getValue().asText());
            }
            return approved;
        } catch (java.io.IOException | UnsupportedOperationException | IllegalArgumentException failure) {
            throw problem("IMPORT_TRIGGER_MANIFEST_UNTRUSTED");
        }
    }
    private static String quote(String identifier) { return "`"+identifier.replace("`", "``")+"`"; }
    private static void rollback(Connection connection) {
        try { connection.rollback(); } catch (SQLException ignored) { /* Owner close remains fail-closed. */ }
    }
    private static CdsImportException problem(String code) { return new CdsImportException(code); }
}
