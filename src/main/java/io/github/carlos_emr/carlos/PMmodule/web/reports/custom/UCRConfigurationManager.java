/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.PMmodule.web.reports.custom;

import java.io.File;
import java.io.FileInputStream;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.Unmarshaller;
import javax.xml.transform.sax.SAXSource;
import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.XmlUtils;

public class UCRConfigurationManager {

    private static Logger logger = MiscUtils.getLogger();
    static UCRConfiguration config;

    private String filename;

    public UCRConfigurationManager() {

    }

    public void setFilename(String filename) {
        this.filename = filename;
    }

    public UCRConfiguration getConfig(String basePath) throws Exception {
        logger.debug("loading up custom reports config");
        if (config == null) {
            File f = new File(basePath, filename);
            if (f.exists()) {
                logger.debug("found config file");
            }
            JAXBContext ctx = JAXBContext.newInstance(UCRConfiguration.class);
            Unmarshaller unmarshaller = ctx.createUnmarshaller();
            try (FileInputStream fis = new FileInputStream(f)) {
                SAXSource source = XmlUtils.createSecureJaxbSource(fis);
                config = (UCRConfiguration) unmarshaller.unmarshal(source);
            }
            logger.debug("parsed config file");
            return config;
        } else {
            return config;
        }
    }

    public UCRConfiguration getConfig() throws Exception {
        return getConfig("");
    }

}
