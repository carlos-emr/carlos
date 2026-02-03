package io.github.carlos_emr.carlos.integration.fhir.manager;
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

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.integration.fhir.builder.DestinationFactory;
import io.github.carlos_emr.carlos.integration.fhir.builder.ResourceAttributeFilterFactory;
import io.github.carlos_emr.carlos.integration.fhir.builder.SenderFactory;
import io.github.carlos_emr.carlos.integration.fhir.model.Destination;
import io.github.carlos_emr.carlos.integration.fhir.model.Sender;
import io.github.carlos_emr.carlos.integration.fhir.resources.ResourceAttributeFilter;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public final class OscarFhirConfigurationManager {

    private static Logger logger = MiscUtils.getLogger();

    private Destination destination;
    private Sender sender;
    private LoggedInInfo loggedInInfo;
    private ResourceAttributeFilter resourceAttributeFilter;
    private Settings settings;

    /**
     * Inject a Settings Object and all the configuration Objects will be instantiated automatically.
     * Including the Sender and Destination Objects.
     */
    public OscarFhirConfigurationManager(LoggedInInfo loggedInInfo, Settings settings) {

        logger.debug("Setting Oscar FHIR Configuration Manager with settings file: " + settings);

        this.loggedInInfo = loggedInInfo;

        this.destination = DestinationFactory.getDestination(settings);

        logger.debug("Destination settings: " + this.destination);

        this.sender = SenderFactory.getSender(settings);

        logger.debug("Sender settings: " + this.sender);

        this.resourceAttributeFilter = ResourceAttributeFilterFactory.getFilter(settings.getFhirDestination());

        logger.debug("FHIR Resource Attribute Filter: " + this.resourceAttributeFilter);
    }

    public Destination getDestination() {
        return destination;
    }

    public Sender getSender() {
        return sender;
    }

    public LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    public ResourceAttributeFilter getResourceAttributeFilter(Class<?> targetResource) {
        if (resourceAttributeFilter == null) {
            return null;
        }
        return resourceAttributeFilter.getFilter(targetResource);
    }

    public Settings getSettings() {
        return settings;
    }

    public void setSettings(Settings settings) {
        this.settings = settings;
    }

}
