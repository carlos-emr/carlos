package io.github.carlos_emr.carlos.integration.fhir.builder;
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

import java.util.HashSet;
import java.util.UUID;

import org.hl7.fhir.dstu3.model.Attachment;
import org.hl7.fhir.dstu3.model.BaseResource;
import org.hl7.fhir.dstu3.model.Bundle;
import org.hl7.fhir.dstu3.model.MessageHeader;
import org.hl7.fhir.dstu3.model.Resource;
import org.hl7.fhir.dstu3.model.Bundle.BundleType;
import io.github.carlos_emr.carlos.integration.fhir.manager.OscarFhirConfigurationManager;
import io.github.carlos_emr.carlos.integration.fhir.model.Destination;
import io.github.carlos_emr.carlos.integration.fhir.model.AbstractOscarFhirResource;
import io.github.carlos_emr.carlos.integration.fhir.model.Sender;


public class FhirBundleBuilder extends AbstractFhirMessageBuilder<Bundle> {

    /**
     * Build a bundle without the header.
     * The header can be added in later with the resources.
     */
    public FhirBundleBuilder(MessageHeader messageHeader) {
        super(messageHeader);
        setBundle(new Bundle());
    }

    /**
     * To build a bundle with a header from the sender and destination objects.
     */
    public FhirBundleBuilder(Sender sender, Destination destination) {
        super(sender, destination);
        setBundle(new Bundle());
    }

    public FhirBundleBuilder(OscarFhirConfigurationManager configurationManager) {
        super(configurationManager);
        setBundle(new Bundle());
    }

    public Bundle getBundle() {
        return getWrapper();
    }

    private void setBundle(Bundle bundle) {
        bundle.setId(UUID.randomUUID().toString());
        bundle.setType(BundleType.MESSAGE);
        setWrapper(bundle);
        initResources();
    }

    private void initResources() {
        MessageHeader messageHeader = getMessageHeader();
        addResource(messageHeader);
    }

    public void addResources(HashSet<AbstractOscarFhirResource<?, ?>> oscarFhirResources) {
        for (AbstractOscarFhirResource<?, ?> oscarFhirResource : oscarFhirResources) {
            addResource(oscarFhirResource);
        }
    }

    @Override
    public void addResource(BaseResource resource) {
        getBundle().addEntry().setResource((Resource) resource);
    }

    @Override
    protected void addAttachment(Attachment attachment) {
        // unused
    }

}
