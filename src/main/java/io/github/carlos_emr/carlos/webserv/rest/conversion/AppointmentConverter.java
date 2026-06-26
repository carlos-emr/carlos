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
package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.dao.DemographicDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppointmentTo1;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.beans.BeanUtils;

public class AppointmentConverter extends AbstractConverter<Appointment, AppointmentTo1> {

    /**
     * Identity and server-managed audit fields that must never be taken from a client-supplied
     * transfer object. Copying these from the DTO would allow over-posting (e.g. spoofing
     * {@code creator}/{@code lastUpdateUser}) and would clobber the original {@code createDateTime}
     * with the DTO's {@code new Date()} default on every update.
     */
    private static final String[] SERVER_MANAGED_PROPERTIES = {
            "id", "createDateTime", "updateDateTime", "creator", "creatorSecurityId",
            "lastUpdateUser", "bookingSource"
    };

    private boolean includeDemographic;
    private boolean includeProvider;

    private DemographicDao demographicDao = SpringUtils.getBean(DemographicDao.class);
    private ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);

    public AppointmentConverter() {

    }

    public AppointmentConverter(boolean includeDemographic, boolean includeProvider) {
        this.includeDemographic = includeDemographic;
        this.includeProvider = includeProvider;
    }

    @Override
    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public Appointment getAsDomainObject(LoggedInInfo loggedInInfo, AppointmentTo1 t) throws ConversionException {
        if (t == null) {
            return null;
        }
        // Inverse of getAsTransferObject(): copy the client-editable JavaBean properties onto a
        // domain Appointment. Identity and audit fields are excluded (see applyEditableProperties)
        // so a caller cannot over-post them. The DTO-only fields (demographic, provider,
        // billingDetail) have no counterpart on Appointment and are ignored by copyProperties.
        // Without this, callers such as updateAppointment received a null Appointment and failed.
        Appointment d = new Appointment();
        applyEditableProperties(t, d);
        return d;
    }

    /**
     * Copies only the client-editable properties from {@code source} onto {@code target}, leaving
     * the target's identity and server-managed audit fields ({@link #SERVER_MANAGED_PROPERTIES})
     * untouched. Used by the update flow to apply changes onto a loaded appointment without
     * over-posting or corrupting audit data.
     */
    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public void applyEditableProperties(AppointmentTo1 source, Appointment target) {
        BeanUtils.copyProperties(source, target, SERVER_MANAGED_PROPERTIES);
    }

    @Override
    // FindSecBugs BEAN_PROPERTY_INJECTION: Spring BeanUtils.copyProperties copies fixed JavaBean
    // descriptors between known CARLOS types; no user-controlled property name reaches the sink.
    @SuppressFBWarnings(value = "BEAN_PROPERTY_INJECTION",
            justification = "Spring BeanUtils.copyProperties copies fixed JavaBean descriptors between " +
                    "known CARLOS types; no user-controlled property name reaches the sink")
    public AppointmentTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Appointment d) throws ConversionException {
        AppointmentTo1 t = new AppointmentTo1();

        BeanUtils.copyProperties(d, t);

        if (includeDemographic && t.getDemographicNo() > 0) {
            t.setDemographic(demographicDao.getDemographicById(t.getDemographicNo()));
        }

        if (includeProvider && t.getProviderNo() != null) {
            t.setProvider(providerDao.getProvider(t.getProviderNo()));
        }

        return t;
    }


}
