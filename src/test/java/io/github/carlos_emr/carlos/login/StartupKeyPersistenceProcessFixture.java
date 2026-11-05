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
package io.github.carlos_emr.carlos.login;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.utility.EncryptionUtils;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

/** A separate JVM that loads a blank configuration before a coordinated real startup. */
public final class StartupKeyPersistenceProcessFixture {
    private StartupKeyPersistenceProcessFixture() {
    }

    /**
     * Loads configuration, waits for its peer, then boots and encrypts a synthetic credential.
     *
     * @param args String[] override, home, webapp root, ready marker, start marker and ciphertext path
     * @throws Exception if configuration, startup, coordination or encryption fails
     */
    public static void main(String[] args) throws Exception {
        System.setProperty("carlos_override_properties", args[0]);
        System.setProperty("user.home", args[1]);
        CarlosProperties.getInstance();
        Files.createFile(Path.of(args[3]));
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
        while (!Files.exists(Path.of(args[4]))) {
            if (System.nanoTime() >= deadline) {
                throw new IllegalStateException("Concurrent startup barrier timed out");
            }
            Thread.sleep(10);
        }
        ServletContext context = (ServletContext) Proxy.newProxyInstance(
                StartupKeyPersistenceProcessFixture.class.getClassLoader(), new Class<?>[]{ServletContext.class},
                (proxy, method, arguments) -> switch (method.getName()) {
                    case "getResource" -> Path.of(args[2]).toUri().toURL();
                    case "getClassLoader" -> StartupKeyPersistenceProcessFixture.class.getClassLoader();
                    default -> null;
                });
        new Startup().contextInitialized(new ServletContextEvent(context));
        Files.writeString(Path.of(args[5]), EncryptionUtils.encrypt("concurrent-startup-credential"));
    }
}
