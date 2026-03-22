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

/**
 * Manages FHIR configuration for outbound messages including Sender, Destination,
 * and resource attribute filter settings.
 *
 * <p>Instantiates the Sender via {@link SenderFactory}, the Destination via
 * {@link DestinationFactory}, and the ResourceAttributeFilter via
 * {@link ResourceAttributeFilterFactory} based on the provided {@link Settings}.</p>
 *
 * @since 2026-03-17
 */
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

    /**
     * Returns the configured FHIR message destination.
     *
     * @return Destination the message destination
     */
    public Destination getDestination() {
        return destination;
    }

    /**
     * Returns the configured FHIR message sender.
     *
     * @return Sender the message sender
     */
    public Sender getSender() {
        return sender;
    }

    /**
     * Returns the logged-in user session context.
     *
     * @return LoggedInInfo the current user's session information
     */
    public LoggedInInfo getLoggedInInfo() {
        return loggedInInfo;
    }

    /**
     * Returns the resource attribute filter scoped to the given FHIR resource class.
     *
     * @param targetResource the FHIR resource class to filter attributes for
     * @return ResourceAttributeFilter the filter for the target resource, or {@code null} if none loaded
     */
    public ResourceAttributeFilter getResourceAttributeFilter(Class<?> targetResource) {
        if (resourceAttributeFilter == null) {
            return null;
        }
        return resourceAttributeFilter.getFilter(targetResource);
    }

    /**
     * Returns the FHIR settings for this configuration.
     *
     * @return Settings the FHIR settings, or {@code null} if not explicitly set after construction
     */
    public Settings getSettings() {
        return settings;
    }

    /**
     * Sets the FHIR settings for this configuration.
     *
     * @param settings the FHIR settings to apply
     */
    public void setSettings(Settings settings) {
        this.settings = settings;
    }

}
