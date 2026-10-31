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

import com.fasterxml.jackson.core.StreamReadConstraints;
import com.fasterxml.jackson.core.StreamReadFeature;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.sun.security.auth.module.UnixSystem;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Verifies root-issued, immutable, expiring exact-argv proof. Environment usernames never authorize an import. */
public final class CdsDelegationVerifier {
    private static final Path ROOT = Path.of("/run/carlos-cds-delegations");
    private static final Set<String> FIELDS = Set.of("protocol", "operator", "operatorUid", "provider", "operation",
            "argvSha256", "nonce", "issuedEpochSeconds", "expiresEpochSeconds");
    private CdsDelegationVerifier() { }

    public record Delegation(String operator, long operatorUid, String nonce) {
        @Override public String toString() { return "Delegation[protected]"; }
    }

    public static Delegation verify(Map<String, String> environment, CdsCliArguments arguments,
            String[] exactArguments, Instant now) throws IOException {
        String selected = environment.get("CARLOS_CDS_DELEGATION_TICKET");
        if (selected == null) throw denied();
        Path ticket;
        try { ticket = Path.of(selected); }
        catch (RuntimeException invalid) { throw denied(); }
        if (!ticket.isAbsolute() || !ROOT.equals(ticket.getParent())
                || !ticket.getFileName().toString().matches("[0-9a-f-]{36}\\.json")) throw denied();
        UnixSystem process = new UnixSystem();
        if (process.getUid() == 0 || !"carlos".equals(process.getUsername())) throw denied();
        long group = process.getGid();
        for (Path cursor = ticket; cursor != null; cursor = cursor.getParent()) {
            if (Files.isSymbolicLink(cursor)) throw denied();
            int uid = (int) Files.getAttribute(cursor, "unix:uid", LinkOption.NOFOLLOW_LINKS);
            int mode = (int) Files.getAttribute(cursor, "unix:mode", LinkOption.NOFOLLOW_LINKS);
            if (uid != 0 || (mode & 0022) != 0) throw denied();
            if (cursor.equals(ticket) || cursor.equals(ROOT)) {
                int gid = (int) Files.getAttribute(cursor, "unix:gid", LinkOption.NOFOLLOW_LINKS);
                if (gid != group || (mode & 07777) != (cursor.equals(ticket) ? 0640 : 0750)) throw denied();
            }
        }
        return verifyDocument(CdsCliConfiguration.readBounded(ticket, 65536), ticket.getFileName().toString(),
                arguments, exactArguments, now);
    }

    static Delegation verifyDocument(byte[] bytes, String filename, CdsCliArguments arguments,
            String[] exactArguments, Instant now) throws IOException {
        if (bytes.length > 65536) throw denied();
        var factory = com.fasterxml.jackson.core.JsonFactory.builder()
                .enable(StreamReadFeature.STRICT_DUPLICATE_DETECTION)
                .streamReadConstraints(StreamReadConstraints.builder().maxNestingDepth(8).maxStringLength(4096)
                        .maxNumberLength(20).build()).build();
        JsonNode json = JsonMapper.builder(factory).enable(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).build()
                .readTree(bytes);
        if (json == null || !json.isObject()) throw denied();
        Set<String> fields = new HashSet<>(); json.fieldNames().forEachRemaining(fields::add);
        if (!fields.equals(FIELDS) || !json.path("protocol").isIntegralNumber()
                || !json.path("protocol").canConvertToInt() || json.path("protocol").intValue() != 1) throw denied();
        String operator = text(json, "operator"), nonce = text(json, "nonce");
        if (!operator.matches("[A-Za-z_][A-Za-z0-9_.-]{0,63}\\$?")) throw denied();
        try { if (!UUID.fromString(nonce).toString().equals(nonce)) throw denied(); }
        catch (IllegalArgumentException failure) { throw denied(); }
        if (!filename.equals(nonce + ".json")
                || !arguments.provider().equals(text(json, "provider"))
                || !arguments.operation().equals(text(json, "operation"))) throw denied();
        long uid = integer(json, "operatorUid"), issued = integer(json, "issuedEpochSeconds"), expires = integer(json, "expiresEpochSeconds");
        long seconds = now.getEpochSecond();
        if (uid < 0 || uid > 4294967295L || issued < 1 || expires <= issued || expires - issued > 300
                || issued > seconds + 30 || issued < seconds - 330 || expires <= seconds) throw denied();
        String claimed = text(json, "argvSha256");
        if (!claimed.matches("[0-9a-f]{64}") || !MessageDigest.isEqual(HexFormat.of().parseHex(claimed), digest(exactArguments))) throw denied();
        return new Delegation(operator, uid, nonce);
    }

    public static byte[] digest(String[] arguments) {
        try {
            MessageDigest sha = MessageDigest.getInstance("SHA-256");
            for (String argument : arguments) {
                byte[] bytes = argument.getBytes(StandardCharsets.UTF_8);
                sha.update(ByteBuffer.allocate(4).putInt(bytes.length).array()); sha.update(bytes);
            }
            return sha.digest();
        } catch (NoSuchAlgorithmException impossible) { throw new IllegalStateException("SHA-256 unavailable"); }
    }

    private static String text(JsonNode json, String name) throws IOException {
        JsonNode value = json.path(name); if (!value.isTextual()) throw denied(); return value.textValue();
    }
    private static long integer(JsonNode json, String name) throws IOException {
        JsonNode value = json.path(name); if (!value.isIntegralNumber() || !value.canConvertToLong()) throw denied(); return value.longValue();
    }
    private static IOException denied() { return new IOException("CDS_DELEGATION_DENIED"); }
}
