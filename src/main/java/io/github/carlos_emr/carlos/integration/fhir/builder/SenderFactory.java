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

import io.github.carlos_emr.carlos.commn.model.Clinic;
import io.github.carlos_emr.carlos.commn.dao.ClinicDAO;
import io.github.carlos_emr.carlos.integration.fhir.model.Sender;
import io.github.carlos_emr.carlos.integration.fhir.resources.Settings;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.CarlosProperties;


/**
 * Factory for creating {@link Sender} objects pre-configured with the EMR's
 * vendor information and clinic data.
 *
 * <p>Reads the build tag and web service endpoint from application properties,
 * and retrieves the clinic entity from the database to populate the Sender's
 * Organization resource.</p>
 *
 * @since 2026-03-17
 */
public final class SenderFactory {

    private static String buildName = CarlosProperties.getInstance().getProperty("buildtag", "UNKNOWN");
    private static String senderEndpoint = CarlosProperties.getInstance().getProperty("ws_endpoint_url_base", "UNKNOWN");
    private static ClinicDAO clinicDao = SpringUtils.getBean(ClinicDAO.class);
    private static String vendorName = "Oscar EMR";
    private static String softwareName = "Oscar";


    public static final Sender getSender() {
        return init(null);
    }

    public static final Sender getSender(Settings settings) {
        return init(settings);
    }

    private static Sender init(Settings settings) {
        Sender sender = null;

        if (settings != null && !settings.isIncludeSenderEndpoint()) {
            sender = new Sender(vendorName, softwareName, buildName);
        } else {
            sender = new Sender(vendorName, softwareName, buildName, senderEndpoint);
        }

        if (clinicDao != null) {
            Clinic clinic = clinicDao.getClinic();
            sender.setClinic(clinic);
        }

        return sender;
    }
}
