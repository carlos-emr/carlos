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

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

import io.github.carlos_emr.carlos.utility.MiscUtils;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.util.StringUtils;

/**
 * Provides PGP encryption for demographic export files.
 *
 * <p>Wraps the system's PGP command-line binary to encrypt files using a
 * configured encryption key. PGP settings are loaded from CarlosProperties:</p>
 * <ul>
 *   <li>{@code PGP_BIN} - Path to the PGP binary executable</li>
 *   <li>{@code PGP_CMD} - The encryption command to execute</li>
 *   <li>{@code PGP_KEY} - The encryption key identifier</li>
 *   <li>{@code PGP_ENV} - Environment variable for the PGP process</li>
 * </ul>
 *
 * @see io.github.carlos_emr.CarlosProperties
 * @since 2026-03-17
 */
public class PGPEncrypt {
    String bin;
    String cmd;
    String key;
    String env;

    /**
     * Constructs a PGPEncrypt instance, loading configuration from CarlosProperties.
     *
     * <p>Logs warnings if any of the required PGP properties are not configured.</p>
     */
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

    /**
     * Verifies that PGP encryption is functional by encrypting a test file.
     *
     * <p>Creates a temporary file, encrypts it, and cleans up both files to confirm
     * that the PGP binary, command, and key are correctly configured.</p>
     *
     * @param dirName String the working directory to use for the test
     * @return boolean true if encryption is functional, false otherwise
     * @throws Exception if an unexpected error occurs during the check
     */
    public boolean check(String dirName) throws Exception {
        if (!Util.checkDir(dirName)) {
            MiscUtils.getLogger().debug("Error! Cannot write to directory [" + dirName + "]");
            return false;
        }
        Runtime rt = Runtime.getRuntime();
        String[] env = {""};
        File dir = new File(dirName);

        boolean rtrn = false;
        try {
            Process proc = rt.exec("touch null.tmp", env, dir);
            int ecode = proc.waitFor();
            if (ecode == 0) {
                String[] cmd = {this.bin, this.cmd, "null.tmp", this.key};
                env[0] = this.env;
                proc = rt.exec(cmd, env, dir);
                BufferedReader stdInput = new BufferedReader(new InputStreamReader(proc.getInputStream()));
                BufferedReader stdError = new BufferedReader(new InputStreamReader(proc.getErrorStream()));

                // read the output from the command
                String s = null;
                while ((s = stdInput.readLine()) != null) {
                    MiscUtils.getLogger().info(s);
                }

                // read any errors from the attempted command
                while ((s = stdError.readLine()) != null) {
                    MiscUtils.getLogger().info(s);
                }

                ecode = proc.waitFor();
                if (ecode == 0) {
                    Util.cleanFile("null.tmp.pgp", dirName);
                    rtrn = true;
                }
                Util.cleanFile("null.tmp", dirName);
            }
        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return rtrn;
    }

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
        File dir = new File(workDir);

        try {
            Process proc = rt.exec(cmd, env, dir);
            int ecode = proc.waitFor();
            if (ecode == 0) return true;

        } catch (IOException ex) {
            MiscUtils.getLogger().error("Error", ex);
        }
        return false;
    }
}
