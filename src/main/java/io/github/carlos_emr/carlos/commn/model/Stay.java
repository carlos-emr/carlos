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

package io.github.carlos_emr.carlos.commn.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class Stay {

    private static final Logger logger = MiscUtils.getLogger();

    private Duration duration;

    public Stay(Date admission, Date discharge, Date start, Date end) {
        Instant admissionInstant = (admission != null && admission.after(start)) ? admission.toInstant() : start.toInstant();
        Instant dischargeInstant = (discharge != null) ? discharge.toInstant() : end.toInstant();

        try {
            if (dischargeInstant.isBefore(admissionInstant)) {
                throw new IllegalArgumentException("The end instant must be greater or equal to the start");
            }
            duration = Duration.between(admissionInstant, dischargeInstant);
        } catch (IllegalArgumentException e) {
            logger.error("admission: " + admission + " discharge: " + discharge, e);
            logger.error("admission instant: " + admissionInstant + " discharge instant: " + dischargeInstant);

            throw e;
        }
    }

    public Duration getDuration() {
        return duration;
    }

}
