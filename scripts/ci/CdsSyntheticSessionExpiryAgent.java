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

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.lang.instrument.Instrumentation;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;

/** Finite test agent: no transformations or application hooks; expires one native Session reference. */
public final class CdsSyntheticSessionExpiryAgent {
    private CdsSyntheticSessionExpiryAgent() { }
    public static void agentmain(String location, Instrumentation unused) {
        Path directory = null;
        long pid = 0, started = 0;
        String context = null;
        try {
            directory = Path.of(location);
            if (!directory.isAbsolute() || !directory.getFileName().toString().startsWith("cds-selected-session-")
                    || !Files.isDirectory(directory, LinkOption.NOFOLLOW_LINKS)
                    || !Files.getPosixFilePermissions(directory).equals(PosixFilePermissions.fromString("rwx------")))
                throw new IllegalArgumentException();
            long uid = ((Number) Files.getAttribute(Path.of("/proc/self"), "unix:uid")).longValue();
            Path request = directory.resolve("request");
            if (!Files.isRegularFile(request, LinkOption.NOFOLLOW_LINKS) || Files.size(request) > 2048
                    || ((Number) Files.getAttribute(directory, "unix:uid")).longValue() != uid
                    || ((Number) Files.getAttribute(request, "unix:uid")).longValue() != uid
                    || !Files.getPosixFilePermissions(request).equals(PosixFilePermissions.fromString("rw-------")))
                throw new IllegalArgumentException();
            String host, session; boolean preflight;
            try (var input = new DataInputStream(Files.newInputStream(request))) {
                if (!"CDS_SELECTED_SESSION_1".equals(input.readUTF())) throw new IllegalArgumentException();
                pid = input.readLong(); started = input.readLong(); host = input.readUTF(); context = input.readUTF(); preflight = input.readBoolean(); session = input.readUTF();
                if (input.read() != -1 || pid != ProcessHandle.current().pid()
                        || started != ProcessHandle.current().info().startInstant().orElseThrow().toEpochMilli()
                        || !"localhost".equals(host) || !context.matches("/[A-Za-z0-9_-]{1,64}")
                        || (preflight ? !session.isEmpty() : !session.matches("[A-Fa-f0-9]{16,128}(?:\\.[A-Za-z0-9_-]{1,64})?"))) throw new IllegalArgumentException();
            }
            Class<?> bootstrap = Class.forName("org.apache.catalina.startup.Bootstrap", false, ClassLoader.getSystemClassLoader());
            var daemonField = bootstrap.getDeclaredField("daemon"); daemonField.setAccessible(true);
            Object daemon = daemonField.get(null);
            var catalinaField = bootstrap.getDeclaredField("catalinaDaemon"); catalinaField.setAccessible(true);
            Object catalina = catalinaField.get(daemon), server = call(catalina, "getServer");
            Object services = call(server, "findServices"), manager = null;
            for (int index = 0; index < Array.getLength(services); index++) {
                Object engine = call(Array.get(services, index), "getContainer");
                if (engine == null) continue;
                Object selectedHost = call(engine, "findChild", host);
                if (selectedHost == null) continue;
                Object selectedContext = call(selectedHost, "findChild", context);
                if (selectedContext == null) continue;
                Object selectedManager = call(selectedContext, "getManager");
                if (selectedManager == null || manager != null) throw new IllegalStateException();
                manager = selectedManager;
            }
            if (manager == null) throw new IllegalStateException();
            if (preflight) {
                Class<?> type = manager.getClass().getMethod("findSession", String.class).getReturnType();
                if (type.getMethod("expire").getReturnType() != void.class
                        || type.getMethod("isValid").getReturnType() != boolean.class) throw new IllegalStateException();
            } else {
                Object selected = call(manager, "findSession", session); session = null;
                // Work on the actual reference. Concurrent removal cannot cause a missing-ID MBean log.
                if (selected == null || !Boolean.TRUE.equals(call(selected, "isValid"))) throw new IllegalStateException();
                call(selected, "expire");
                if (!Boolean.FALSE.equals(call(selected, "isValid"))) throw new IllegalStateException();
            }
            Path result = directory.resolve("result");
            Files.createFile(result, PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------")));
            try (var output = new DataOutputStream(Files.newOutputStream(result))) {
                output.writeUTF(preflight ? "CDS_SELECTED_SESSION_READY_1" : "CDS_SELECTED_SESSION_EXPIRED_1"); output.writeLong(pid); output.writeLong(started); output.writeUTF(context);
            }
        } catch (Throwable failure) {
            // Do not rethrow to the attach listener: a native cause may include sensitive session attributes.
            // A missing exact success receipt makes the driver fail closed with a fixed public code.
        }
    }
    private static Object call(Object receiver, String method) throws ReflectiveOperationException {
        return receiver.getClass().getMethod(method).invoke(receiver);
    }
    private static Object call(Object receiver, String method, String value) throws ReflectiveOperationException {
        return receiver.getClass().getMethod(method, String.class).invoke(receiver, value);
    }
}
