/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.demographic.pageUtil;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * @author Ronnie Cheng
 */
public class PGPEncrypt {
    String bin;
    String cmd;
    String key;
    String env;

    public PGPEncrypt() {
        CarlosProperties op = CarlosProperties.getInstance();
        this.bin = StringUtils.noNull(op.getProperty("PGP_BIN"));
        if (StringUtils.empty(this.bin))
            MiscUtils.getLogger().debug("Warning: PGP binary executable (PGP_BIN) not set!");
        this.cmd = StringUtils.noNull(op.getProperty("PGP_CMD"));
        if (StringUtils.empty(this.cmd))
            MiscUtils.getLogger().debug("Warning: PGP encryption command (PGP_CMD) not set!");
        this.key = StringUtils.noNull(op.getProperty("PGP_KEY"));
        if (StringUtils.empty(this.key)) MiscUtils.getLogger().debug("Warning: PGP encryption key (PGP_KEY) not set!");
        this.env = StringUtils.noNull(op.getProperty("PGP_ENV"));
        if (StringUtils.empty(this.env))
            MiscUtils.getLogger().debug("Warning: PGP environment variable (PGP_ENV) not set!");
    }

    // FindSecBugs COMMAND_INJECTION: only static touch and configured PGP argv arrays run in a validated directory.
    // Do not add request-controlled command fragments under this suppression.
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "only a static touch command and configured PGP argv arrays run in a PathValidationUtils-validated directory; no request-controlled command fragments")
    public boolean check(String dirName) throws Exception {
        if (!Util.checkDir(dirName)) {
            MiscUtils.getLogger().debug("Error! Cannot write to directory [" + dirName + "]");
            return false;
        }
        Runtime rt = Runtime.getRuntime();
        String[] env = {""};
        File dir = PathValidationUtils.resolveConfiguredDirectory(dirName, "PGP directory");

        boolean rtrn = false;
        try {
            Process touch = rt.exec("touch null.tmp", env, dir);
            if (!awaitProcess(touch)) {
                return false;
            }
            if (touch.exitValue() == 0) {
                String[] cmd = {this.bin, this.cmd, "null.tmp", this.key};
                env[0] = this.env;
                Process proc = rt.exec(cmd, env, dir);
                // Drain stdout AND stderr concurrently: reading them sequentially deadlocks if the
                // child fills the stderr pipe buffer while the parent is still blocked reading stdout.
                Thread outDrain = drainAsync(proc.getInputStream());
                Thread errDrain = drainAsync(proc.getErrorStream());
                if (awaitProcess(proc)) {
                    joinQuietly(outDrain);
                    joinQuietly(errDrain);
                    if (proc.exitValue() == 0) {
                        Util.cleanFile("null.tmp.pgp", dirName);
                        rtrn = true;
                    }
                }
                Util.cleanFile("null.tmp", dirName);
            }
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return rtrn;
    }

    /** Maximum time to wait for a PGP subprocess before forcibly terminating it. */
    private static final long PROCESS_TIMEOUT_SECONDS = 120L;

    /**
     * Waits for {@code proc} up to {@link #PROCESS_TIMEOUT_SECONDS}, forcibly terminating it (and
     * returning {@code false}) if it does not exit — a bare {@code waitFor()} with no timeout can hang
     * the calling request thread indefinitely on a wedged PGP child.
     */
    private static boolean awaitProcess(Process proc) {
        try {
            if (proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                return true;
            }
            proc.destroyForcibly();
            MiscUtils.getLogger().error("PGP subprocess timed out after " + PROCESS_TIMEOUT_SECONDS + "s and was terminated");
            return false;
        } catch (InterruptedException e) {
            proc.destroyForcibly();
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Consumes and discards a subprocess stream on a daemon thread so its pipe never fills.
     * Child output is intentionally never logged: external tools may emit patient-bearing filenames,
     * configured paths, key identifiers, or other values this class cannot reliably classify.
     *
     * @param stream the child's stdout or stderr; closed by this thread
     * @return the started daemon thread, so the caller can join it before reaping the process
     */
    private static Thread drainAsync(InputStream stream) {
        Thread t = new Thread(() -> {
            try (InputStream input = stream) {
                input.transferTo(OutputStream.nullOutputStream());
            } catch (IOException ignored) {
                // Stream closed as the process ended; nothing actionable.
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private static void joinQuietly(Thread t) {
        try {
            t.join(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // FindSecBugs COMMAND_INJECTION: PGP invocation uses an argv array from trusted configuration in a validated work directory.
    // Do not add request-controlled command fragments under this suppression.
    @SuppressFBWarnings(value = "COMMAND_INJECTION", justification = "PGP invocation uses an argv array from trusted configuration in a PathValidationUtils-validated work directory; no shell expansion")
    boolean encrypt(String srcFile, String workDir) throws Exception {
        if (!Util.checkDir(workDir)) {
            MiscUtils.getLogger().debug("Error! Cannot write to directory [" + workDir + "]");
            return false;
        }
        if (StringUtils.empty(srcFile)) {
            MiscUtils.getLogger().debug("Error! Source file not given; nothing to encrypt!");
            return false;
        }
        Runtime rt = Runtime.getRuntime();
        String[] env = {this.env};
        String[] cmd = new String[4];
        cmd[0] = this.bin;
        cmd[1] = this.cmd;
        cmd[2] = srcFile;
        cmd[3] = this.key;
        File dir = PathValidationUtils.resolveConfiguredDirectory(workDir, "PGP work directory");

        try {
            Process proc = rt.exec(cmd, env, dir);
            // Drain both streams concurrently and bound the wait — the previous code drained neither
            // stream and used an unbounded waitFor(), so any child output could deadlock the request
            // thread indefinitely.
            Thread outDrain = drainAsync(proc.getInputStream());
            Thread errDrain = drainAsync(proc.getErrorStream());
            if (awaitProcess(proc)) {
                joinQuietly(outDrain);
                joinQuietly(errDrain);
                if (proc.exitValue() == 0) return true;
            }
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return false;
    }
}
