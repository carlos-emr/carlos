/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/** Operator-provisioned dedicated sender keys. Missing/bad/insecure files are never regenerated. */
final class PortalMyCarlosDeliveryKeyring {
    static final String PROPERTY = "patient_portal.mycarlos.delivery.keyring_file";
    private PortalMyCarlosDeliveryKeyring() {}

    static PortalMyCarlosDeliveryCipher load(String configuredFile) {
        try {
            if (configuredFile == null || configuredFile.isBlank() || Files.isSymbolicLink(Path.of(configuredFile))) throw invalid();
            Path path = PathValidationUtils.validateConfiguredFile(configuredFile, "myCarlos sender keyring").toPath();
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            if (permissions.stream().anyMatch(permission -> permission.name().startsWith("GROUP_")
                    || permission.name().startsWith("OTHERS_"))
                    || !Files.getOwner(path, LinkOption.NOFOLLOW_LINKS).getName().equals(System.getProperty("user.name"))) throw invalid();
            // A secure file inside a replaceable directory still permits a key substitution race.
            // Provision under a service/root-owned directory chain, outside shared temporary paths.
            for (Path parent = path.getParent(); parent != null; parent = parent.getParent()) {
                var directory = Files.getPosixFilePermissions(parent, LinkOption.NOFOLLOW_LINKS);
                String owner = Files.getOwner(parent, LinkOption.NOFOLLOW_LINKS).getName();
                if (directory.contains(PosixFilePermission.GROUP_WRITE) || directory.contains(PosixFilePermission.OTHERS_WRITE)
                        || !(owner.equals(System.getProperty("user.name")) || owner.equals("root"))) throw invalid();
            }
            byte[] content;
            try (var channel = Files.newByteChannel(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS)) {
                if (channel.size() == 0 || channel.size() > 65536) throw invalid();
                ByteBuffer buffer = ByteBuffer.allocate(65537);
                while (channel.read(buffer) > 0) { if (!buffer.hasRemaining()) throw invalid(); }
                content = java.util.Arrays.copyOf(buffer.array(), buffer.position());
            }
            return parse(content);
        } catch (Exception failure) {
            // File paths, content, credentials and provider diagnostics do not cross this boundary.
            throw invalid();
        }
    }

    static PortalMyCarlosDeliveryCipher parse(byte[] content) {
        try {
            if (content == null || content.length == 0 || content.length > 65536) throw invalid();
            String text = StandardCharsets.US_ASCII.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(content)).toString();
            Map<String, String> values = new HashMap<>();
            for (String line : text.split("\\r?\\n", -1)) {
                String entry = line.strip();
                if (entry.isEmpty() || entry.startsWith("#")) continue;
                int separator = entry.indexOf('=');
                if (separator <= 0 || entry.indexOf('=', separator + 1) >= 0) throw invalid();
                String name = entry.substring(0, separator), value = entry.substring(separator + 1);
                if (values.putIfAbsent(name, value) != null) throw invalid();
            }
            if (!"1".equals(values.remove("format"))) throw invalid();
            int current = keyId(values.remove("current"));
            Map<Integer, byte[]> keys = new HashMap<>();
            for (var entry : values.entrySet()) {
                if (!entry.getKey().startsWith("key.")) throw invalid();
                int id = keyId(entry.getKey().substring(4));
                if (keys.putIfAbsent(id, PortalMyCarlosDeviceApprovalCodec.exact32(entry.getValue())) != null) throw invalid();
            }
            return new PortalMyCarlosDeliveryCipher(current, keys);
        } catch (Exception failure) { throw invalid(); }
    }

    private static int keyId(String value) {
        long parsed = PortalMyCarlosDeviceApprovalCodec.decimal(value);
        if (parsed <= 0 || parsed > Integer.MAX_VALUE) throw invalid();
        return (int) parsed;
    }
    private static IllegalStateException invalid() {
        return new IllegalStateException("myCarlos sender keyring is missing, invalid, or insecure");
    }
}
