package io.github.carlos_emr.carlos.integration.fhir.resources;
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

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.integration.fhir.interfaces.ResourceAttributeFilterInterface;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.PathValidationUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

public class ResourceAttributeFilter implements ResourceAttributeFilterInterface {

    private static Logger logger = MiscUtils.getLogger();

    private Class<?> targetResource;
    private Properties properties;

    public ResourceAttributeFilter(String filterURL) {
        properties = new Properties();
        try {
            logger.debug("Loading FHIR resource filter from " + filterURL);
            readFromFile(filterURL);
        } catch (IOException e) {
            return;
        }
    }

    // FindSecBugs PATH_TRAVERSAL_IN: path derived from trusted configuration/constant/DB value, not user-controllable input
    @SuppressFBWarnings(value = "PATH_TRAVERSAL_IN", justification = "path derived from trusted configuration/constant/DB value, not user-controllable input")
    private void readFromFile(String filterURL) throws IOException {
        InputStream is = getClass().getResourceAsStream(filterURL);

        if (is == null) {
            try {
                is = new FileInputStream(PathValidationUtils.resolveTrustedPath(new File(filterURL)));
            } catch (SecurityException e) {
                // Honour the declared throws IOException so the constructor's IOException handler
                // catches a canonicalization failure rather than an unchecked SecurityException.
                throw new IOException("Unable to resolve filter resource file", e);
            }
        }

        try {
            properties.load(is);
        } finally {
            is.close();
        }
    }

    /**
     * Checks if the given attribute value should be included (true) or excluded (false)
     */
    @Override
    public final boolean include(OptionalFHIRAttribute attribute) {
        boolean value = validate(attribute.name());
        logger.debug("Filtering optional attribute " + attribute.name() + "=" + value);
        return value;
    }

    @Override
    public final boolean isMandatory(MandatoryFHIRAttribute attribute) {
        boolean value = validate(attribute.name());
        logger.debug("Filtering mandatory attribute " + attribute.name() + "=" + value);
        return value;
    }

    @Override
    public ResourceAttributeFilter getFilter(Class<?> targetResource) {
        this.targetResource = targetResource;
        return this;
    }

    private boolean validate(String attribute) {
        String value = "true";

        if (properties != null) {
            value = properties.getProperty(String.format("%s.%s", targetResource.getSimpleName().toLowerCase(), attribute), "true");
        }

        value = value.trim().toLowerCase();

        return Boolean.parseBoolean(value);
    }

}
