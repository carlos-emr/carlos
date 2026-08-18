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
/*
 * MspErrorCodes.java
 *
 * Created on June 20, 2004, 6:41 PM
 */

package io.github.carlos_emr.carlos.billings.ca.bc.MSP;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.Properties;

import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;

import io.github.carlos_emr.CarlosProperties;

public class MspErrorCodes extends Properties {

    /**
     * Creates a new instance of MspErrorCodes
     */
    public MspErrorCodes() {
        super();
        try (InputStream is = openErrorCodesStream()) {
            if (is != null) {
                load(is);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
            MiscUtils.getLogger().debug("Error loading MSP Error codes file :" + CarlosProperties.getInstance().getProperty("msp_error_codes"));
        }
    }

    private InputStream openErrorCodesStream() throws Exception {
        String configuredPath = CarlosProperties.getInstance().getProperty("msp_error_codes");
        if (configuredPath != null) {
            try {
                File configuredFile = PathValidationUtils.validateConfiguredFile(configuredPath, "msp_error_codes");
                return new FileInputStream(configuredFile);
            } catch (Exception e) {
                MiscUtils.getLogger().warn("Could not load configured MSP error codes; using fallback", e);
            }
        }

        try {
            File documentDir = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
            File file = PathValidationUtils.validateGeneratedChildPath("msp_error_codes.properties", documentDir);
            if (file.exists()) {
                return new FileInputStream(file);
            }
        } catch (Exception e) {
            MiscUtils.getLogger().warn("Could not load MSP error codes from DOCUMENT_DIR; using bundled defaults", e);
        }

        return this.getClass().getClassLoader().getResourceAsStream("oscar/oscarBilling/ca/bc/MSP/mspEditCodes.properties");
    }


    public void save() {
        try {
            File file;
            if (CarlosProperties.getInstance().getProperty("msp_error_codes") != null) {
                String filename = CarlosProperties.getInstance().getProperty("msp_error_codes");
                file = PathValidationUtils.resolveConfiguredFile(filename, "msp_error_codes");
            } else {
                File documentDir = PathValidationUtils.resolveConfiguredDirectory(CarlosProperties.getInstance().getProperty("DOCUMENT_DIR"), "DOCUMENT_DIR");
                file = PathValidationUtils.validateGeneratedChildPath("msp_error_codes.properties", documentDir);
            }

            try (FileOutputStream fos = new FileOutputStream(file)) {
                store(fos, "Written on " + new Date());
            }
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        }

    }

}
