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
package io.github.carlos_emr.carlos.event;

import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;

public class EventService implements ApplicationEventPublisherAware {
    Logger logger = MiscUtils.getLogger();
    protected ApplicationEventPublisher applicationEventPublisher;

    @Override
    public void setApplicationEventPublisher(ApplicationEventPublisher arg0) {
        this.applicationEventPublisher = arg0;

    }


    /*
     * Event is fired from:
     *   /appointment/UpdateRecord — edit appt screen (UpdateRecord2Action)
     *   /provider/AddStatus — appt status icon (AddStatus2Action)
     */
    public void appointmentStatusChanged(Object source, String appointment_no, String provider_no, String status) {
        if (logger.isDebugEnabled()) {
            logger.debug("appointmentStatusChanged thrown by {} appt# {} status {}", // NOSONAR javasecurity:S5145 - source class is non-request metadata; appointment_no and status sanitized with LogSafe
                    source.getClass().getName(),
                    LogSafe.sanitize(appointment_no),
                    LogSafe.sanitize(status));
        }

        applicationEventPublisher.publishEvent(new AppointmentStatusChangeEvent(source, appointment_no, provider_no, status));
    }

    /*
     * Event is fired from:
     *   /appointment/AddRecord — AddRecord2Action
     *   /appointment/appointmentaddrecordcard — forwards to
     *     /WEB-INF/jsp/appointment/appointmentaddrecordcard.jsp which
     *     persists via appointmentDao.persist(...) and fires the event
     *   /appointment/appointmentaddrecordprint — forwards to
     *     /WEB-INF/jsp/appointment/appointmentaddrecordprint.jsp which
     *     persists via appointmentDao.persist(...) and fires the event
     * The two *record{card,print} endpoints are ViewAppointmentSelfPost2Action
     * gates whose target JSPs still contain scriptlet mutations (flagged for
     * scriptlet extraction follow-up).
     */
    public void appointmentCreated(Object source, String appointment_no, String provider_no) {
        applicationEventPublisher.publishEvent(new AppointmentCreatedEvent(source, appointment_no, provider_no));
    }
}
