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

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsPaths;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/** Durable ownership for source copies made before database acceptance. */
@Service
public class CdsPreacceptService {
    private static final ObjectMapper JSON=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .enable(com.fasterxml.jackson.core.StreamReadFeature.STRICT_DUPLICATE_DETECTION).build())
            .enable(com.fasterxml.jackson.databind.DeserializationFeature.FAIL_ON_TRAILING_TOKENS);
    private final CdsImportConfigurationService configuration;
    private final CdsImportLedgerService ledger;
    private final Clock clock;
    private final long indexLockWaitNanos;
    private static final Set<Path> ACTIVE=new java.util.HashSet<>();
    private static final ReentrantLock INDEX_MUTEX=new ReentrantLock();
    @Autowired
    public CdsPreacceptService(CdsImportConfigurationService configuration, CdsImportLedgerService ledger) {
        this(configuration, ledger, Clock.systemUTC());
    }
    CdsPreacceptService(CdsImportConfigurationService configuration, CdsImportLedgerService ledger, Clock clock) {
        this(configuration, ledger, clock, 5000);
    }
    CdsPreacceptService(CdsImportConfigurationService configuration, CdsImportLedgerService ledger, Clock clock, long indexLockWaitMillis) {
        if (indexLockWaitMillis<1 || indexLockWaitMillis>30000) throw new IllegalArgumentException("Invalid preaccept index lock wait");
        this.configuration=configuration; this.ledger=ledger; this.clock=clock;
        this.indexLockWaitNanos=TimeUnit.MILLISECONDS.toNanos(indexLockWaitMillis);
    }

    public record Ownership(Path root, String generation, String intentDigest, String operator, String submissionKey, long createdAt) {
        @Override public String toString() {return "CdsPreacceptOwnership[protected]"; }
    }

    public static final class IoGuard implements AutoCloseable {
        private final Path path; private boolean closed;
        private IoGuard(Path path) {this.path=path; }
        @Override public void close() {synchronized (ACTIVE){if (!closed){ACTIVE.remove(path); closed=true; }}}
    }
    public IoGuard enter(Path root) {
        IoGuard guard=tryEnter(root); if (guard==null)throw new CdsImportException("PREACCEPT_IO_BUSY"); return guard;
    }
    private static IoGuard tryEnter(Path root) {
        Path key=root.toAbsolutePath().normalize();
        synchronized (ACTIVE) {if (ACTIVE.size()>=64 || !ACTIVE.add(key))return null; return new IoGuard(key); }
    }

    /** Caller holds enter(root) and accept.lock continuously through journal, copy and ledger acceptance. */
    public Ownership begin(CdsImportActorDto actor, CdsImportCommand command, Path jobRoot, FileLock owner) throws IOException {
        CdsImportLedgerService.requireNoTransaction(); requireLock(owner);
        Path staging=configuration.stagingRoot().toRealPath(LinkOption.NOFOLLOW_LINKS);
        Path root=CdsPaths.within(jobRoot, staging);
        synchronized (ACTIVE){if (!ACTIVE.contains(root.toAbsolutePath().normalize()))throw new CdsImportException("PREACCEPT_IO_GUARD_REQUIRED"); }
        if (!privateDirectory(staging) || !privateDirectory(root)) throw new CdsImportException("PREACCEPT_STORAGE_NOT_PRIVATE");
        String expected="job-"+CdsImportKeys.hash(actor.operator(), command.submissionKey());
        if (!root.equals(staging.resolve(expected)) || command.input().toAbsolutePath().normalize().startsWith(root)) {
            throw new CdsImportException("PREACCEPT_ROOT_OWNERSHIP_INVALID");
        }
        ObjectNode request=JSON.createObjectNode(); request.set("actor", JSON.valueToTree(actor)); request.set("command", JSON.valueToTree(command));
        String requestDigest=hash(JSON.writeValueAsBytes(request));
        Path seal=root.resolve("acceptance.seal");
        if (Files.exists(seal, LinkOption.NOFOLLOW_LINKS)) {
            Ownership existing=ownership(root);
            JsonNode journal=journal(existing);
            if (!journal.path("requestSha256").asText().equals(requestDigest)) throw new CdsImportException("PREACCEPT_REQUEST_CONFLICT");
            appendIndex(existing); return existing;
        }
        try (DirectoryStream<Path> entries=Files.newDirectoryStream(root)) {
            for (Path entry:entries) if (!entry.getFileName().toString().equals("accept.lock")) {
                throw new CdsImportException("PREACCEPT_UNJOURNALED_SOURCE_REQUIRES_REVIEW");
            }
        }
        String generation=UUID.randomUUID().toString(); long created=clock.millis();
        ObjectNode body=JSON.createObjectNode().put("version", 1).put("generation", generation).put("createdAt", created)
                .put("operator", actor.operator()).put("submissionKey", command.submissionKey()).put("rootName", expected)
                .put("requestSha256", requestDigest).put("originalSourceModifiedByImporter", false);
        body.set("request", request);
        byte[] bytes=JSON.writeValueAsBytes(body);
        if (bytes.length>65536) throw new CdsImportException("PREACCEPT_JOURNAL_CAPACITY_EXCEEDED");
        String digest=hash(bytes); Path document=root.resolve("acceptance-"+digest+".json");
        writeNew(document, root, bytes);
        publishSame(seal, root, JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("generation", generation)
                .put("intentSha256", digest).put("document", document.getFileName().toString())));
        Ownership owned=new Ownership(root, generation, digest, actor.operator(), command.submissionKey(), created);
        appendIndex(owned); return owned;
    }

    /** A missing acknowledgement after this marker is always uncertain, even after the TTL. */
    public void accepting(Ownership owned, Path jobDocument, String sourceDigest, FileLock owner) throws IOException {
        requireLock(owner); verifyOwner(owned);
        Path document=CdsPaths.within(jobDocument, owned.root()); byte[] bytes=read(document, 65536);
        if (!document.getFileName().toString().equals("job-"+hash(bytes)+".json")) throw new CdsImportException("PREACCEPT_JOB_PROOF_INVALID");
        publishSame(owned.root().resolve("accepting.seal"), owned.root(), JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1)
                .put("generation", owned.generation()).put("intentSha256", owned.intentDigest())
                .put("jobDocument", document.getFileName().toString()).put("sourceSha256", sourceDigest)));
    }

    public void accepted(Ownership owned, String runId, FileLock owner) throws IOException {
        requireLock(owner); verifyOwner(owned);
        publishSame(owned.root().resolve("accepted.seal"), owned.root(), JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1)
                .put("generation", owned.generation()).put("intentSha256", owned.intentDigest()).put("runId", runId)));
    }

    /** Bounded administrative janitor; never treats missing database acknowledgement as proof of rollback. */
    public Map<String, Object> cleanup(int maxCandidates, int maxDeletes) {
        CdsImportLedgerService.requireNoTransaction();
        if (maxCandidates<1 || maxCandidates>100 || maxDeletes<16 || maxDeletes>10000) throw new IllegalArgumentException("Invalid preaccept cleanup bounds");
        Map<String, Object> result=new LinkedHashMap<>();
        for (String key:new String[]{"processed", "busy", "retainedAccepted", "unexpired", "uncertain", "deletedFiles", "deletedRoots", "alreadyAbsent"}) result.put(key, 0L);
        result.put("fullPassComplete", false); result.put("more", false); result.put("state", "RUNNING");
        try {
            Path root=configuration.stagingRoot();
            if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) {result.put("state", "COMPLETE"); result.put("fullPassComplete", true); return Map.copyOf(result); }
            if (!privateDirectory(root)) throw new CdsImportException("PREACCEPT_STORAGE_INVALID");
            Path index=root.resolve(".preaccept-index.jsonl"), seal=root.resolve(".preaccept-index.seal");
            if (!Files.exists(index, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.exists(seal, LinkOption.NOFOLLOW_LINKS)) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                try (DirectoryStream<Path> candidates=Files.newDirectoryStream(root)) {
                    int visited=0;
                    for (Path candidate:candidates) {
                        if (++visited>maxCandidates) {result.put("more", true); return Map.copyOf(result); }
                        if (candidate.getFileName().toString().matches("job-[0-9a-f]{64}|preaccept-retired-[0-9a-f-]{36}")) {
                            try (IoGuard process=tryEnter(candidate)) {
                            if (process==null){increment(result, "busy", 1); continue; }
                            Path owner=candidate.resolve("accept.lock");
                            if (Files.isRegularFile(owner, LinkOption.NOFOLLOW_LINKS)) {
                                try (FileChannel channel=FileChannel.open(owner, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); FileLock held=tryLock(channel)) {
                                    if (held==null) {increment(result, "busy", 1); continue; }
                                }
                            }
                            throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                            }
                        }
                    }
                }
                result.put("state", "COMPLETE"); result.put("fullPassComplete", true); return Map.copyOf(result);
            }
            try (IoGuard process=tryEnter(root.resolve(".preaccept-sweep.lock"))) {
            if (process==null){result.put("busy", 1L); return Map.copyOf(result); }
            try (FileChannel gate=channel(root.resolve(".preaccept-sweep.lock")); FileLock lock=tryLock(gate)) {
                if (lock==null) {result.put("busy", 1L); return Map.copyOf(result); }
                JsonNode committed=JSON.readTree(read(seal, 65536)); long end=committed.path("bytes").asLong(-1);
                if (end<0 || Files.size(index)<end) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                try (FileChannel proof=FileChannel.open(index, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {verifyTail(proof, committed, end); }
                Path cursorFile=root.resolve(".preaccept-cursor.json");
                JsonNode cursor=Files.exists(cursorFile, LinkOption.NOFOLLOW_LINKS)?JSON.readTree(read(cursorFile, 65536)):JSON.createObjectNode();
                long offset=cursor.path("offset").asLong(0), uncertain=cursor.path("uncertain").asLong(0);
                if (offset<0 || offset>end || uncertain<0) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                int remaining=maxDeletes;
                try (FileChannel input=FileChannel.open(index, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                    input.position(offset);
                    while (offset<end && ((Number) result.get("processed")).longValue()<maxCandidates && remaining>0) {
                        long before=offset; byte[] line=readLine(input, end); offset=input.position();
                        if (line==null) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                        JsonNode envelope=JSON.readTree(line); String encoded=envelope.path("payload").asText();
                        if (encoded.length()>8192) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                        byte[] data=java.util.Base64.getDecoder().decode(encoded);
                        if (!hash(data).equals(envelope.path("sha256").asText())) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                        JsonNode row=JSON.readTree(data); increment(result, "processed", 1);
                        Outcome outcome=retire(root, row, remaining);
                        increment(result, outcome.counter(), 1); increment(result, "deletedFiles", outcome.deleted()); remaining-=outcome.operations();
                        if (outcome.counter().equals("uncertain")) uncertain++;
                        if (outcome.partial()) {offset=before; break; }
                    }
                }
                boolean complete=offset==end && JSON.readTree(read(seal, 65536)).path("bytes").asLong(-1)==end;
                result.put("uncertain", uncertain); result.put("fullPassComplete", complete); result.put("more", !complete);
                result.put("state", uncertain>0?"REVIEW_REQUIRED":complete?"COMPLETE":"RUNNING");
                replace(cursorFile, root, JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1)
                        .put("offset", complete?0:offset).put("uncertain", complete?0:uncertain)));
                return Map.copyOf(result);
            }
            }
        } catch (IOException | RuntimeException unavailable) {
            result.put("uncertain", ((Number) result.get("uncertain")).longValue()+1); result.put("state", "REVIEW_REQUIRED");
            result.put("fullPassComplete", false); result.put("code", "PREACCEPT_CLEANUP_RECONCILIATION_REQUIRED"); return Map.copyOf(result);
        }
    }

    private Outcome retire(Path staging, JsonNode entry, int budget) throws IOException {
        String name=entry.path("rootName").asText(), generation=entry.path("generation").asText();
        if (!name.matches("job-[0-9a-f]{64}") || !generation.matches("[0-9a-f-]{36}")) return new Outcome("uncertain", 0, false);
        Path retired=staging.resolve("preaccept-retired-"+generation), root=Files.exists(retired, LinkOption.NOFOLLOW_LINKS)?retired:staging.resolve(name);
        if (!Files.exists(root, LinkOption.NOFOLLOW_LINKS)) return new Outcome("alreadyAbsent", 0, false);
        if (!privateDirectory(root)) return new Outcome("uncertain", 0, false);
        Path ownerFile=root.resolve("accept.lock");
        if (!Files.isRegularFile(ownerFile, LinkOption.NOFOLLOW_LINKS)) return new Outcome("uncertain", 0, false);
        try (IoGuard process=tryEnter(root)) {
        if (process==null)return new Outcome("busy", 0, false);
        try (FileChannel owner=FileChannel.open(ownerFile, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS); FileLock lock=tryLock(owner)) {
            if (lock==null) return new Outcome("busy", 0, false);
            Ownership ownership=ownership(root);
            if (!ownership.generation().equals(generation) && root.equals(staging.resolve(name))) return new Outcome("alreadyAbsent", 0, false);
            if (!ownership.generation().equals(generation) || !ownership.intentDigest().equals(entry.path("intentSha256").asText())
                    || !ownership.operator().equals(entry.path("operator").asText()) || !ownership.submissionKey().equals(entry.path("submissionKey").asText())
                    || ownership.createdAt()!=entry.path("createdAt").asLong(-1)
                    || !name.equals("job-"+CdsImportKeys.hash(ownership.operator(), ownership.submissionKey()))) return new Outcome("uncertain", 0, false);
            if (!root.equals(retired)) {
                if (Files.exists(root.resolve("accepted.seal"), LinkOption.NOFOLLOW_LINKS)) {
                    JsonNode accepted=JSON.readTree(read(root.resolve("accepted.seal"), 65536));
                    return marker(accepted, ownership) && accepted.path("runId").asText().matches("[A-Za-z0-9_.:-]{1,64}")
                            ?new Outcome("retainedAccepted", 0, false):new Outcome("uncertain", 0, false);
                }
                var accepted=ledger.findSubmission(ownership.operator(), ownership.submissionKey());
                if (accepted!=null) {accepted(ownership, accepted.id(), lock); return new Outcome("retainedAccepted", 0, false); }
                if (Files.exists(root.resolve("accepting.seal"), LinkOption.NOFOLLOW_LINKS)) return new Outcome("uncertain", 0, false);
                long ttl=configuration.number("cds.import.preacceptRetentionMillis", 86_400_000L);
                if (clock.millis()-ownership.createdAt()<ttl) return new Outcome("unexpired", 0, false);
                publishSame(root.resolve("retirement.seal"), root, JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1)
                        .put("generation", generation).put("intentSha256", ownership.intentDigest()).put("unacceptedBeforeRename", true)));
                Files.move(root, retired, StandardCopyOption.ATOMIC_MOVE); CdsPaths.forceDirectory(staging); root=retired;
            } else {
                JsonNode proof=JSON.readTree(read(root.resolve("retirement.seal"), 65536));
                if (!marker(proof, ownership) || !proof.path("unacceptedBeforeRename").asBoolean()) return new Outcome("uncertain", 0, false);
            }
            return deleteKnown(root, budget);
        } catch (OverlappingFileLockException busy) {return new Outcome("busy", 0, false); }
        }
    }

    private Outcome deleteKnown(Path root, int budget) throws IOException {
        Ownership owned=ownership(root);
        String intentName="acceptance-"+owned.intentDigest()+".json";
        Set<String> controls=Set.of("accept.lock", "acceptance.seal", "retirement.seal", "indexed.seal", intentName);
        long[] deleted={0}, operations={0}; boolean[] uncertain={false}, partial={false}; Set<Path> changed=new java.util.HashSet<>();
        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override public FileVisitResult preVisitDirectory(Path directory, BasicFileAttributes attrs) {
                if (operations[0]>=budget) {partial[0]=true; return FileVisitResult.TERMINATE; }
                if (!directory.equals(root) && !ownedDirectory(root.relativize(directory))) {uncertain[0]=true; return FileVisitResult.TERMINATE; }
                return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path relative=root.relativize(file);
                boolean pendingMetadata=relative.getNameCount()==1 && relative.toString().matches(
                        "\\.(?:preaccept-marker|job-document)-"+java.util.regex.Pattern.quote(owned.generation())+"-[0-9a-f-]{36}\\.pending");
                if (relative.getNameCount()==1 && controls.contains(relative.toString())) {
                    if (!attrs.isRegularFile()) {uncertain[0]=true; return FileVisitResult.TERMINATE; }
                    return FileVisitResult.CONTINUE;
                }
                if (!attrs.isRegularFile() || !(pendingMetadata && attrs.size()<=65536) && !ownedFile(relative)) {uncertain[0]=true; return FileVisitResult.TERMINATE; }
                if (operations[0]>=budget) {partial[0]=true; return FileVisitResult.TERMINATE; }
                if (relative.getNameCount()==1 && !pendingMetadata && !file.getFileName().toString().equals("job-"+hash(read(file, 65536))+".json")) {
                    uncertain[0]=true; return FileVisitResult.TERMINATE;
                }
                Files.delete(file); deleted[0]++; operations[0]++; changed.add(file.getParent()); return FileVisitResult.CONTINUE;
            }
            @Override public FileVisitResult postVisitDirectory(Path directory, IOException failed) throws IOException {
                if (failed!=null) throw failed;
                if (!directory.equals(root)) {
                    if (operations[0]>=budget) {partial[0]=true; return FileVisitResult.TERMINATE; }
                    try {Files.delete(directory); operations[0]++; changed.add(directory.getParent()); }
                    catch (java.nio.file.DirectoryNotEmptyException retained) {uncertain[0]=true; return FileVisitResult.TERMINATE; }
                }
                return FileVisitResult.CONTINUE;
            }
        });
        for (Path directory:changed) if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) CdsPaths.forceDirectory(directory);
        if (uncertain[0]) return new Outcome("uncertain", deleted[0], operations[0], false);
        if (partial[0] || operations[0]+controls.size()+1>budget) return new Outcome("processedOnly", deleted[0], operations[0], true);
        // Keep the immutable intent until all payload entries have been retired.
        for (String control:controls) if (!control.equals("acceptance.seal")) {if (Files.deleteIfExists(root.resolve(control))) {deleted[0]++; operations[0]++; }}
        Files.delete(root.resolve("acceptance.seal")); deleted[0]++; operations[0]++;
        Files.delete(root); operations[0]++; CdsPaths.forceDirectory(root.getParent()); return new Outcome("deletedRoots", deleted[0], operations[0], false);
    }

    private static boolean ownedDirectory(Path relative) {
        if (relative.getNameCount()>2) return false;
        String top=relative.getName(0).toString();
        if (!(top.equals("snapshot") || top.equals("snapshot.pending") || top.matches("snapshot-abandoned-[0-9a-f-]{36}"))) return false;
        return relative.getNameCount()==1 || Set.of("members", "names").contains(relative.getName(1).toString());
    }
    private static boolean ownedFile(Path relative) {
        if (relative.getNameCount()==1) return relative.toString().matches("job-[0-9a-f]{64}\\.json");
        Path parent=relative.getParent(); if (!ownedDirectory(parent)) return false;
        String name=relative.getFileName().toString();
        if (relative.getNameCount()==2) return Set.of("files.jsonl", "snapshot.seal", "source-archive.bin").contains(name);
        return parent.getFileName().toString().equals("members")?name.matches("[0-9]{12}\\.bin"):name.matches("[0-9a-f]{64}");
    }

    private void appendIndex(Ownership owned) throws IOException {
        long started=System.nanoTime();
        Path root=configuration.stagingRoot(), index=root.resolve(".preaccept-index.jsonl"), seal=root.resolve(".preaccept-index.seal");
        // One entry per generation, even when a large source copy is repeatedly resumed.
        Path marker=owned.root().resolve("indexed.seal"); if (Files.exists(marker, LinkOption.NOFOLLOW_LINKS)) return;
        acquireIndexMutex(started);
        try {
        try (FileChannel gate=channel(root.resolve(".preaccept-index.lock")); FileLock lock=acquireIndexFileLock(gate, started); FileChannel file=channel(index)) {
            remainingIndexWait(started);
            long end=0;
            if (Files.exists(seal, LinkOption.NOFOLLOW_LINKS)) {
                JsonNode prior=JSON.readTree(read(seal, 65536)); end=prior.path("bytes").asLong(-1);
                if (end<0 || file.size()<end) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
                verifyTail(file, prior, end);
            } else if (file.size()!=0) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
            // Only an uncommitted append may follow this seal: no source copy starts before sealing.
            file.truncate(end); file.position(end);
            byte[] entry=JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("rootName", owned.root().getFileName().toString())
                    .put("generation", owned.generation()).put("intentSha256", owned.intentDigest()).put("operator", owned.operator())
                    .put("submissionKey", owned.submissionKey()).put("createdAt", owned.createdAt()));
            byte[] row=JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("payload", java.util.Base64.getEncoder().encodeToString(entry)).put("sha256", hash(entry)));
            ByteBuffer data=ByteBuffer.allocate(row.length+1).put(row).put((byte) '\n'); data.flip(); while (data.hasRemaining())file.write(data);
            file.force(true); byte[] committed=new byte[row.length+1]; System.arraycopy(row, 0, committed, 0, row.length); committed[row.length]='\n';
            replace(seal, root, JSON.writeValueAsBytes(JSON.createObjectNode().put("version", 1).put("bytes", file.position())
                    .put("lastOffset", end).put("lastLength", committed.length).put("lastSha256", hash(committed))));
        }
        } finally {INDEX_MUTEX.unlock(); }
        publishSame(marker, owned.root(), JSON.writeValueAsBytes(JSON.createObjectNode().put("generation", owned.generation()).put("intentSha256", owned.intentDigest())));
    }

    /** One admission deadline covers both JVM and cross-process lock contention. */
    private void acquireIndexMutex(long started) {
        try {
            if (!INDEX_MUTEX.tryLock(remainingIndexWait(started), TimeUnit.NANOSECONDS)) throw new CdsImportException("PREACCEPT_INDEX_TIMEOUT");
        } catch (InterruptedException cancelled) {throw indexCancelled(); }
    }
    private FileLock acquireIndexFileLock(FileChannel channel, long started) throws IOException {
        while (true) {
            remainingIndexWait(started);
            FileLock acquired=tryLock(channel);
            if (acquired!=null) return acquired;
            try {TimeUnit.NANOSECONDS.sleep(Math.min(remainingIndexWait(started), TimeUnit.MILLISECONDS.toNanos(25))); }
            catch (InterruptedException cancelled) {throw indexCancelled(); }
        }
    }
    private long remainingIndexWait(long started) {
        if (Thread.currentThread().isInterrupted()) throw indexCancelled();
        long remaining=indexLockWaitNanos-(System.nanoTime()-started);
        if (remaining<=0) throw new CdsImportException("PREACCEPT_INDEX_TIMEOUT");
        return remaining;
    }
    private static CdsImportException indexCancelled() {
        Thread.currentThread().interrupt(); return new CdsImportException("PREACCEPT_INDEX_CANCELLED");
    }

    private Ownership ownership(Path root) throws IOException {
        JsonNode seal=JSON.readTree(read(root.resolve("acceptance.seal"), 65536)); String digest=seal.path("intentSha256").asText();
        if (!digest.matches("[0-9a-f]{64}") || !seal.path("document").asText().equals("acceptance-"+digest+".json")) throw new CdsImportException("PREACCEPT_JOURNAL_INVALID");
        byte[] bytes=read(root.resolve(seal.path("document").asText()), 65536); if (!hash(bytes).equals(digest)) throw new CdsImportException("PREACCEPT_JOURNAL_INVALID");
        JsonNode body=JSON.readTree(bytes);
        if (body.path("version").asInt()!=1 || !body.path("generation").asText().equals(seal.path("generation").asText())
                || body.path("originalSourceModifiedByImporter").asBoolean(true)) throw new CdsImportException("PREACCEPT_JOURNAL_INVALID");
        return new Ownership(root, body.path("generation").asText(), digest, body.path("operator").asText(), body.path("submissionKey").asText(), body.path("createdAt").asLong(-1));
    }
    private JsonNode journal(Ownership owned) throws IOException {return JSON.readTree(read(owned.root().resolve("acceptance-"+owned.intentDigest()+".json"), 65536)); }
    private void verifyOwner(Ownership expected) throws IOException {
        Ownership actual=ownership(expected.root()); if (!actual.equals(expected)) throw new CdsImportException("PREACCEPT_GENERATION_CHANGED");
    }
    private static boolean marker(JsonNode value, Ownership owned) {
        return value.path("generation").asText().equals(owned.generation()) && value.path("intentSha256").asText().equals(owned.intentDigest());
    }
    private static boolean privateDirectory(Path path) throws IOException {
        return !Files.isSymbolicLink(path) && Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)
                && Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS).equals(PosixFilePermissions.fromString("rwx------"));
    }
    private static void requireLock(FileLock lock) {if (lock==null || !lock.isValid() || lock.isShared()) throw new CdsImportException("PREACCEPT_LOCK_REQUIRED"); }
    private static FileChannel channel(Path path) throws IOException {return FileChannel.open(path, Set.of(StandardOpenOption.CREATE, StandardOpenOption.READ, StandardOpenOption.WRITE, LinkOption.NOFOLLOW_LINKS), PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))); }
    private static FileLock tryLock(FileChannel channel) throws IOException {try {return channel.tryLock(); }catch (OverlappingFileLockException busy){return null; }}
    private static byte[] read(Path file, int maximum) throws IOException {return CdsImportJobService.boundedFile(file, maximum); }
    private static byte[] readLine(FileChannel channel, long end) throws IOException {
        java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream(512); ByteBuffer buffer=ByteBuffer.allocate(4096);
        while (channel.position()<end) {
            long start=channel.position(); buffer.clear(); buffer.limit((int) Math.min(buffer.capacity(), end-start)); int n=channel.read(buffer);
            if (n<1)throw new IOException("PREACCEPT_INDEX_TRUNCATED");
            for (int i=0; i<n; i++) {int value=buffer.array()[i]&255;
                if (value=='\n') {channel.position(start+i+1); return bytes.toByteArray(); }
                if (bytes.size()>=65536)throw new IOException("PREACCEPT_INDEX_ROW_LIMIT"); bytes.write(value);
            }
        }
        return bytes.size()==0?null:bytes.toByteArray();
    }
    private static void verifyTail(FileChannel file, JsonNode seal, long end) throws IOException {
        long offset=seal.path("lastOffset").asLong(-1), length=seal.path("lastLength").asLong(-1);
        if (end<1 || offset<0 || length<1 || length>65536 || offset>end || length!=end-offset) throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
        ByteBuffer bytes=ByteBuffer.allocate((int) length); file.position(offset);
        while (bytes.hasRemaining()) if (file.read(bytes)<1)throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
        if (!hash(bytes.array()).equals(seal.path("lastSha256").asText()))throw new CdsImportException("PREACCEPT_CATALOG_RECONCILIATION_REQUIRED");
    }
    private static String hash(byte[] bytes) {return CdsPaths.hex(CdsPaths.sha256().digest(bytes)); }
    private static void writeNew(Path file, Path root, byte[] bytes) throws IOException {try (OutputStream output=CdsPaths.newPrivateFile(file, root)){output.write(bytes); }CdsPaths.forceFile(file); CdsPaths.forceDirectory(root); }
    private static void publishSame(Path file, Path root, byte[] bytes) throws IOException {
        if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            if (!java.util.Arrays.equals(bytes, read(file, 65536)))throw new CdsImportException("PREACCEPT_MARKER_CONFLICT");
            return;
        }
        String generation=JSON.readTree(bytes).path("generation").asText();
        if (!generation.matches("[0-9a-f-]{36}")) throw new CdsImportException("PREACCEPT_MARKER_INVALID");
        Path pending=root.resolve(".preaccept-marker-"+generation+"-"+UUID.randomUUID()+".pending"); writeNew(pending, root, bytes);
        Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE); CdsPaths.forceDirectory(root);
    }
    private static void replace(Path file, Path root, byte[] bytes) throws IOException {Path pending=root.resolve(".preaccept-"+UUID.randomUUID()+".pending"); writeNew(pending, root, bytes); Files.move(pending, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); CdsPaths.forceDirectory(root); }
    private static void increment(Map<String, Object> counters, String key, long amount) {if (!key.equals("processedOnly"))counters.put(key, ((Number) counters.getOrDefault(key, 0L)).longValue()+amount); }
    private record Outcome(String counter, long deleted, long operations, boolean partial) {
        Outcome(String counter, long deleted, boolean partial) {this(counter, deleted, deleted, partial); }
    }
}
