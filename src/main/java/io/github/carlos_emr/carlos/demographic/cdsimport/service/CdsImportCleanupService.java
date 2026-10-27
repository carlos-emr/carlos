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

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportCleanupDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportFileDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;

/** Reconciles only proved run-owned artifacts. Accepted evidence and resumable native publications are retained explicitly. */
@Service
public class CdsImportCleanupService {
    private static final ObjectMapper JSON=new ObjectMapper().enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private static final Pattern PUBLICATION=Pattern.compile("cds-([a-f0-9]{64})-([a-z]{1,16})-([a-f0-9]{64})\\.([a-z0-9]{1,8})");
    private final CdsImportLedgerService ledger;
    private final CdsImportFileService files;
    private static final org.apache.logging.log4j.Logger LOG=org.apache.logging.log4j.LogManager.getLogger(CdsImportCleanupService.class);
    private final CdsImportConfigurationService configuration;
    private final CdsUploadService uploads;
    private final CdsPreacceptService preaccept;

    public CdsImportCleanupService(CdsImportLedgerService ledger, CdsImportFileService files, CdsImportConfigurationService configuration, CdsUploadService uploads, CdsPreacceptService preaccept) {
        this.ledger=ledger; this.files=files; this.configuration=configuration; this.uploads=uploads; this.preaccept=preaccept;
    }

    /** Bounded global janitor. It never guesses that an unacknowledged acceptance rolled back. */
    public Map<String, Object> housekeeping() {
        CdsImportLedgerService.requireNoTransaction(); Map<String, Object> result=new LinkedHashMap<>(); Set<String> codes=new LinkedHashSet<>(); Map<String, Object> source=Map.of();
        try { result.put("expiredUploadsRemoved", uploads.cleanupExpired(32)); }
        catch (IOException | RuntimeException failure) { codes.add("IMPORT_UPLOAD_CLEANUP_FAILED"); LOG.error("CDS housekeeping failed: IMPORT_UPLOAD_CLEANUP_FAILED"); }
        try {
            Map<String, Object> supplied=preaccept.cleanup(32, 200);
            if (supplied.size()>32) throw new CdsImportException("CLEANUP_METRICS_INVALID"); Map<String, Object> safe=new LinkedHashMap<>();
            for (var entry:supplied.entrySet()) {
                Object value=entry.getValue(); if (!entry.getKey().matches("[A-Za-z][A-Za-z0-9]{0,63}") || !(value instanceof Number || value instanceof Boolean || value instanceof String text && text.matches("[A-Z0-9_]{1,96}"))) throw new CdsImportException("CLEANUP_METRICS_INVALID");
                safe.put(entry.getKey(), value);
            }
            source=Map.copyOf(safe); result.put("preaccept", source);
        } catch (RuntimeException failure) { codes.add("IMPORT_PREACCEPT_CLEANUP_FAILED"); LOG.error("CDS housekeeping failed: IMPORT_PREACCEPT_CLEANUP_FAILED"); }
        if ("FAILED".equals(source.get("state"))) codes.add("IMPORT_PREACCEPT_CLEANUP_FAILED");
        boolean uncertain="REVIEW_REQUIRED".equals(source.get("state")) || source.get("uncertain") instanceof Number count && count.longValue()>0;
        if (uncertain) LOG.error("CDS housekeeping retained uncertain preaccept artifacts: IMPORT_PREACCEPT_REQUIRES_REVIEW");
        result.put("state", !codes.isEmpty()?"FAILED":uncertain?"REVIEW_REQUIRED":"COMPLETE"); result.put("safeCodes", java.util.List.copyOf(codes));
        result.put("fullPassComplete", Boolean.TRUE.equals(source.get("fullPassComplete")));
        return java.util.Collections.unmodifiableMap(result);
    }

    /** Caller retains the coordinator; no database transaction spans filesystem work. */
    public CdsImportCleanupDto reconcile(String run, long epoch, Path currentReportDirectory) throws IOException {
        CdsImportLedgerService.requireNoTransaction(); check(run, epoch);
        Path job=CdsPaths.within(Path.of(ledger.snapshotLocation(run)), configuration.stagingRoot()).getParent();
        Path current=CdsPaths.within(currentReportDirectory, job);
        if (!reportOwned(current, run, epoch, true)) throw new CdsImportException("CLEANUP_REPORT_OWNER_PROOF_REQUIRED");
        Catalogue catalogue=new Catalogue();
        Path document=PathValidationUtils.getRequiredDocumentDirectory().toPath().toAbsolutePath().normalize();
        pending(run, epoch, document, catalogue);
        catalogue(run, epoch, job, current, catalogue);
        String after="";
        while (true) {
            check(run, epoch); var page=files.ownedFiles(run, after, 200); if (page.isEmpty()) break;
            for (var item:page) {
                CdsImportFileDto file=item.file(); after=file.id();
                Path path=Path.of(file.location()).toAbsolutePath().normalize();
                if (path.startsWith(job)) {
                    if (!"PUBLISHED".equals(file.state())) catalogue.problem("CLEANUP_SOURCE_LIFECYCLE_REQUIRES_REVIEW");
                    if (!regularSize(path, file.byteLength())) catalogue.problem("CLEANUP_SOURCE_ARTIFACT_UNAVAILABLE");
                    // The filesystem catalogue already includes these retained accepted-source paths.
                    continue;
                }
                if (!path.startsWith(document)) { catalogue.retain("UNKNOWN_PUBLICATION_LOCATION", -1); catalogue.problem("CLEANUP_UNKNOWN_PUBLICATION_LOCATION"); continue; }
                CdsPaths.within(path, document);
                if ("DELETED".equals(file.state())) {
                    if (Files.exists(path, LinkOption.NOFOLLOW_LINKS) || item.referenceCount()!=0) catalogue.problem("CLEANUP_DELETION_REQUIRES_RECONCILIATION");
                    continue;
                }
                if (!"PUBLISHED".equals(file.state()) || !regularSize(path, file.byteLength())) {
                    catalogue.retain("UNCERTAIN_PUBLICATION", -1); catalogue.problem("CLEANUP_PUBLICATION_REQUIRES_RECONCILIATION"); continue;
                }
                if (item.referenceCount()>0) { catalogue.retain("REFERENCED_NATIVE_PUBLICATION", file.byteLength()); continue; }
                var name=PUBLICATION.matcher(path.getFileName().toString());
                if (!name.matches() || !name.group(3).equals(file.digest())) {
                    catalogue.retain("UNCERTAIN_PUBLICATION", -1); catalogue.problem("CLEANUP_UNPROVED_PUBLICATION_OWNER"); continue;
                }
                try {
                    var unit=ledger.unitStatus(run, name.group(1));
                    if (!"EXPECTED".equals(unit.state())) {
                        catalogue.retain("UNCERTAIN_PUBLICATION", -1); catalogue.problem("CLEANUP_UNREFERENCED_ADVANCED_PUBLICATION");
                    } else catalogue.retain("RESUMABLE_NATIVE_PUBLICATION", file.byteLength());
                } catch (CdsImportException unknown) {
                    catalogue.retain("UNCERTAIN_PUBLICATION", -1); catalogue.problem("CLEANUP_UNPROVED_PUBLICATION_OWNER");
                }
            }
        }
        check(run, epoch);
        return catalogue.result();
    }

    private void catalogue(String run, long epoch, Path job, Path current, Catalogue catalogue) throws IOException {
        Files.walkFileTree(job, Set.of(), 128, new SimpleFileVisitor<>() {
            private long visited;
            private Path reportDirectory;
            private String reportReason;
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attributes) throws IOException {
                if (++visited%100==0) check(run, epoch);
                if (directory.equals(current)) return FileVisitResult.SKIP_SUBTREE;
                if (directory.getParent()!=null && directory.getParent().equals(job) && directory.getFileName().toString().startsWith("reports-")) {
                    reportDirectory=directory;
                    if (reportOwned(directory, run, epoch, false)) reportReason=Files.isRegularFile(directory.resolve("report-set.json"), LinkOption.NOFOLLOW_LINKS)?"DURABLE_OPERATOR_REPORT_SET":"OPERATOR_REPORT_ATTEMPT_FOR_RECOVERY";
                    else { reportReason="UNPROVED_OPERATOR_REPORT_ARTIFACT"; catalogue.problem("CLEANUP_REPORT_OWNER_REQUIRES_REVIEW"); }
                }
                if (attributes.isSymbolicLink() || !attributes.isDirectory()) { catalogue.problem("CLEANUP_UNKNOWN_ARTIFACT_RETAINED"); return FileVisitResult.SKIP_SUBTREE; }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) throws IOException {
                if (++visited%100==0) check(run, epoch);
                if (!attributes.isRegularFile() || attributes.isSymbolicLink()) { catalogue.retain("UNKNOWN_ACCEPTED_ARTIFACT", -1); catalogue.problem("CLEANUP_UNKNOWN_ARTIFACT_RETAINED"); return FileVisitResult.CONTINUE; }
                Path relative=job.relativize(file); String first=relative.getName(0).toString();
                String reason=first.equals("reviews") || first.equals("corrections")?"REVIEW_EVIDENCE":
                        first.startsWith("reports-")?(reportDirectory!=null && job.resolve(first).equals(reportDirectory)?reportReason:"UNPROVED_OPERATOR_REPORT_ARTIFACT"):
                        first.equals("parsed")?"PARSED_SOURCE_FOR_RECONCILIATION":
                        first.equals("snapshot")?"ACCEPTED_SOURCE_SNAPSHOT":
                        first.startsWith("snapshot")?"ACCEPTED_SOURCE_ATTEMPT_FOR_RECOVERY":"ACCEPTED_JOB_EVIDENCE";
                catalogue.retain(reason, attributes.size()); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFileFailed(Path file, IOException failure) {
                catalogue.retain("UNREADABLE_ACCEPTED_ARTIFACT", -1); catalogue.problem("CLEANUP_ARTIFACT_UNREADABLE"); return FileVisitResult.CONTINUE;
            }
        });
    }

    private void pending(String run, long epoch, Path document, Catalogue catalogue) throws IOException {
        Path root=CdsPaths.within(document.resolve(".cds-pending").resolve(run), document);
        if (!exists(root)) return;
        requirePrivate(root.getParent()); requirePrivate(root);
        try (DirectoryStream<Path> operations=Files.newDirectoryStream(root)) {
            for (Path operation:operations) {
                check(run, epoch);
                if (!operation.getFileName().toString().matches("[a-f0-9-]{36}")) { catalogue.problem("CLEANUP_UNKNOWN_PRIVATE_OPERATION"); continue; }
                try { operation(run, epoch, document, root, operation, catalogue); }
                catch (CdsImportException | IOException invalid) {
                    if (invalid instanceof CdsImportException known && "OWNER_FENCED".equals(known.getCode())) throw known;
                    catalogue.problem("CLEANUP_PRIVATE_OPERATION_REQUIRES_REVIEW");
                    catalogue.retain("UNCERTAIN_PRIVATE_OPERATION", -1);
                }
            }
        }
    }

    private void operation(String run, long epoch, Path document, Path runRoot, Path operation, Catalogue catalogue) throws IOException {
        requirePrivate(operation); Path journal=operation.resolve("owner.json"), payload=operation.resolve("payload.partial");
        try (DirectoryStream<Path> content=Files.newDirectoryStream(operation)) {
            if (!content.iterator().hasNext()) { check(run, epoch); Files.delete(operation); CdsPaths.forceDirectory(runRoot); catalogue.directoryRemoved(); return; }
        }
        if (!Files.isRegularFile(journal, LinkOption.NOFOLLOW_LINKS) || Files.size(journal)>65536) throw new CdsImportException("CLEANUP_OWNER_PROOF_MISSING");
        CdsDocumentPublicationService.PendingOwner owner;
        try (InputStream input=Files.newInputStream(journal, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(65537); if (bytes.length>65536) throw new CdsImportException("CLEANUP_OWNER_PROOF_CAPACITY");
            owner=JSON.readValue(bytes, CdsDocumentPublicationService.PendingOwner.class);
        }
        if (owner.protocol()!=1 || !run.equals(owner.runId()) || owner.ownerEpoch()<1 || owner.ownerEpoch()>epoch
                || !operation.getFileName().toString().equals(owner.operationId()) || owner.unitKey()==null || !owner.unitKey().matches("[a-f0-9]{64}")
                || owner.variant()==null || !owner.variant().matches("[a-z]{1,16}") || owner.extension()==null || !owner.extension().matches("[a-z0-9]{1,8}")
                || owner.digest()==null || !owner.digest().matches("[a-f0-9]{64}") || owner.byteLength()<0 || owner.byteLength()>configuration.limits().maxExpandedBytes()) throw new CdsImportException("CLEANUP_OWNER_PROOF_INVALID");
        ledger.unitStatus(run, owner.unitKey());
        String expected=CdsDocumentPublicationService.filename(owner.unitKey(), owner.variant(), owner.extension(), owner.digest());
        if (!expected.equals(owner.finalFilename())) throw new CdsImportException("CLEANUP_DESTINATION_PROOF_INVALID");
        int entries=0;
        try (DirectoryStream<Path> content=Files.newDirectoryStream(operation)) {
            for (Path candidate:content) {
                if (++entries>2 || !(candidate.equals(journal) || candidate.equals(payload)) || !Files.isRegularFile(candidate, LinkOption.NOFOLLOW_LINKS)) throw new CdsImportException("CLEANUP_UNKNOWN_OPERATION_CONTENT");
            }
        }
        Path destination=CdsPaths.within(document.resolve(expected), document);
        boolean linked=exists(destination);
        if (!linked && files.findLocation(destination.toString())!=null) throw new CdsImportException("CLEANUP_REGISTERED_PUBLICATION_MISSING");
        if (linked) {
            verifyBytes(run, epoch, destination, owner.digest(), owner.byteLength());
            files.reconcilePublication(run, epoch, destination.toString(), owner.digest(), owner.byteLength());
            // The subsequent owned-file page accounts for the retained final generation once.
        }
        // No final path means this exact owned private payload never became a native reference.
        // A published/unknown final path is retained; only the private alias/journal are retired here.
        check(run, epoch);
        boolean hadPayload=exists(payload);
        long payloadBytes=hadPayload?Files.size(payload):0;
        long journalBytes=Files.size(journal);
        Files.deleteIfExists(payload); CdsPaths.forceDirectory(operation);
        Files.delete(journal); CdsPaths.forceDirectory(operation);
        Files.delete(operation); CdsPaths.forceDirectory(runRoot); catalogue.directoryRemoved();
        if (hadPayload) catalogue.remove(linked?"PUBLISHED_PRIVATE_ALIAS":"ABANDONED_PRIVATE_PAYLOAD", payloadBytes);
        catalogue.remove("PRIVATE_OWNERSHIP_JOURNAL", journalBytes);
    }

    private void verifyBytes(String run, long epoch, Path path, String expected, long length) throws IOException {
        BasicFileAttributes before=Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (!before.isRegularFile() || before.size()!=length) throw new CdsImportException("CLEANUP_FINAL_PUBLICATION_CHANGED");
        MessageDigest digest=CdsPaths.sha256(); long total=0, next=16L<<20;
        try (InputStream input=Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] buffer=new byte[65536]; int count;
            while ((count=input.read(buffer))!=-1) {
                total=Math.addExact(total, count); if (total>length) throw new CdsImportException("CLEANUP_FINAL_PUBLICATION_CHANGED"); digest.update(buffer, 0, count);
                if (total>=next){check(run, epoch); next=Math.addExact(total, 16L<<20); }
            }
        }
        BasicFileAttributes after=Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS);
        if (total!=length || !java.util.Objects.equals(before.fileKey(), after.fileKey()) || before.size()!=after.size()
                || !before.lastModifiedTime().equals(after.lastModifiedTime()) || !CdsPaths.hex(digest.digest()).equals(expected)) throw new CdsImportException("CLEANUP_FINAL_PUBLICATION_CHANGED");
    }

    private static boolean reportOwned(Path directory, String run, long epoch, boolean current) throws IOException {
        if (!directory.getFileName().toString().matches("reports-[0-9]+-[a-f0-9-]{36}")) return false;
        Path file=directory.resolve("owner.json");
        if (!Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS) || Files.size(file)>8192) return false;
        try (InputStream input=Files.newInputStream(file, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
            byte[] bytes=input.readNBytes(8193); if (bytes.length>8192) return false;
            var owner=JSON.readTree(bytes); long ownerEpoch=owner.path("ownerEpoch").asLong(-1);
            return owner.path("protocol").asInt()==1 && run.equals(owner.path("runId").asText()) && ownerEpoch>=0
                    && (current?ownerEpoch==epoch:ownerEpoch<=epoch) && directory.getFileName().toString().equals(owner.path("directory").asText());
        }
    }

    private static boolean exists(Path path) throws IOException {
        try { Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); return true; }
        catch (NoSuchFileException missing){return false; }
    }
    private static boolean regularSize(Path path, long length) throws IOException {
        try { var attributes=Files.readAttributes(path, BasicFileAttributes.class, LinkOption.NOFOLLOW_LINKS); return attributes.isRegularFile() && attributes.size()==length; }
        catch (NoSuchFileException missing){return false; }
    }
    private static void requirePrivate(Path path) throws IOException {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS) || Files.isSymbolicLink(path)
                || Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).stream().anyMatch(permission -> permission.name().startsWith("GROUP_") || permission.name().startsWith("OTHERS_"))) throw new CdsImportException("CLEANUP_PRIVATE_OWNER_REQUIRED");
    }
    private void check(String run, long epoch) {
        if (Thread.currentThread().isInterrupted()) throw new CdsImportException("CLEANUP_INTERRUPTED");
        if (ledger.status(run).ownerEpoch()!=epoch) throw new CdsImportException("OWNER_FENCED");
    }
    private static final class Catalogue {
        private final Map<String, CdsImportCleanupDto.Count> retained=new LinkedHashMap<>(), removed=new LinkedHashMap<>();
        private final Set<String> codes=new LinkedHashSet<>();
        private long removedDirectories;
        void retain(String reason, long bytes){add(retained, reason, bytes); }
        void remove(String reason, long bytes){add(removed, reason, bytes); }
        void directoryRemoved(){removedDirectories=Math.addExact(removedDirectories, 1); }
        void problem(String code){if (codes.size()<32) codes.add(code); }
        private void add(Map<String, CdsImportCleanupDto.Count> target, String reason, long bytes) {
            CdsImportCleanupDto.Count old=target.getOrDefault(reason, new CdsImportCleanupDto.Count(0, 0L));
            target.put(reason, new CdsImportCleanupDto.Count(Math.addExact(old.artifacts(), 1), bytes<0 || old.logicalBytes()==null?null:Math.addExact(old.logicalBytes(), bytes)));
        }
        CdsImportCleanupDto result(){return new CdsImportCleanupDto(codes.isEmpty()?"COMPLETE":"FAILED", retained, removed, java.util.List.copyOf(codes), true, removedDirectories); }
    }
}
