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

import com.sun.tools.attach.VirtualMachine;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

/** Disposable, same-UID Tomcat test only; the selected session secret travels over stdin/private files. */
final class CdsSyntheticSessionExpiry {
    private static int exitCode;
    public static void main(String[] arguments) {
        Path directory = null;
        try {
            if ((arguments.length != 2 && arguments.length != 3) || !arguments[0].matches("[1-9][0-9]{0,9}")
                    || !arguments[1].matches("/[A-Za-z0-9_-]{1,64}")) throw new IllegalArgumentException();
            boolean preflight = arguments.length == 3;
            if (preflight && !"--preflight".equals(arguments[2])) throw new IllegalArgumentException();
            long pid = Long.parseLong(arguments[0]);
            var process = ProcessHandle.of(pid).orElseThrow();
            long started = process.info().startInstant().orElseThrow().toEpochMilli();
            long uid = ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).longValue();
            if (((Number) Files.getAttribute(Path.of("/proc", arguments[0]), "unix:uid")).longValue() != uid)
                throw new IllegalStateException();
            byte[] command = Files.readAllBytes(Path.of("/proc", arguments[0], "cmdline"));
            if (command.length > 65536 || Arrays.stream(new String(command, StandardCharsets.UTF_8).split("\u0000"))
                    .filter("org.apache.catalina.startup.Bootstrap"::equals).count() != 1) throw new IllegalStateException();
            String session = "";
            if (preflight) {
                if (System.in.read() != -1) throw new IllegalArgumentException();
            } else {
                byte[] input = System.in.readNBytes(257);
                if (input.length > 256 || System.in.read() != -1) throw new IllegalArgumentException();
                session = new String(input, StandardCharsets.US_ASCII);
                if (!session.matches("[A-Fa-f0-9]{16,128}(?:\\.[A-Za-z0-9_-]{1,64})?\\n")) throw new IllegalArgumentException();
                session = session.substring(0, session.length() - 1);
            }
            directory = Files.createTempDirectory("cds-selected-session-", PosixFilePermissions.asFileAttribute(
                    PosixFilePermissions.fromString("rwx------")));
            Path request = directory.resolve("request"), jar = directory.resolve("agent.jar");
            Files.createFile(request, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (var output = new DataOutputStream(Files.newOutputStream(request))) {
                output.writeUTF("CDS_SELECTED_SESSION_1"); output.writeLong(pid); output.writeLong(started);
                output.writeUTF("localhost"); output.writeUTF(arguments[1]); output.writeBoolean(preflight); output.writeUTF(session);
            }
            session = null;
            var manifest = new Manifest();
            var attributes = manifest.getMainAttributes();
            attributes.putValue("Manifest-Version", "1.0");
            attributes.putValue("Agent-Class", "CdsSyntheticSessionExpiryAgent");
            attributes.putValue("Can-Redefine-Classes", "false"); attributes.putValue("Can-Retransform-Classes", "false");
            Files.createFile(jar, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (var output = new JarOutputStream(Files.newOutputStream(jar), manifest);
                    var code = CdsSyntheticSessionExpiryAgent.class.getResourceAsStream("CdsSyntheticSessionExpiryAgent.class")) {
                if (code == null) throw new IllegalStateException();
                output.putNextEntry(new JarEntry("CdsSyntheticSessionExpiryAgent.class"));
                code.transferTo(output); output.closeEntry();
            }
            if (!process.isAlive() || process.info().startInstant().orElseThrow().toEpochMilli() != started)
                throw new IllegalStateException();
            VirtualMachine virtualMachine = VirtualMachine.attach(arguments[0]);
            try { virtualMachine.loadAgent(jar.toString(), directory.toString()); }
            finally { virtualMachine.detach(); }
            Path result = directory.resolve("result");
            if (!Files.isRegularFile(result, LinkOption.NOFOLLOW_LINKS) || Files.size(result) > 1024)
                throw new IllegalStateException();
            try (var proof = new DataInputStream(Files.newInputStream(result))) {
                if (!(preflight ? "CDS_SELECTED_SESSION_READY_1" : "CDS_SELECTED_SESSION_EXPIRED_1").equals(proof.readUTF()) || proof.readLong() != pid
                        || proof.readLong() != started || !arguments[1].equals(proof.readUTF())
                        || proof.read() != -1) throw new IllegalStateException();
            }
            String resultFlag = preflight ? "\"preflight\":true,\"supported\":true," : "\"expired\":true,";
            System.out.println("{" + resultFlag + "\"scope\":\"selected-HttpSession\",\"naturalTimeout\":false,\"atomicReference\":true,\"pid\":"
                    + pid + ",\"startEpochMillis\":" + started + "}");
        } catch (Throwable failure) {
            // Native reflection/attach exceptions can contain application values. Never print causes/arguments.
            System.err.println("SYNTHETIC_NATIVE_SESSION_EXPIRY_FAILED");
            exitCode = 1;
        } finally {
            if (directory != null) {
                try {
                    for (String name : new String[]{"request", "result", "agent.jar"}) Files.deleteIfExists(directory.resolve(name));
                    Files.delete(directory);
                } catch (Exception failedCleanup) {
                    System.err.println("SYNTHETIC_NATIVE_SESSION_EVIDENCE_CLEANUP_FAILED");
                    exitCode = 1;
                }
            }
        }
        if (exitCode != 0) System.exit(exitCode);
    }
}
