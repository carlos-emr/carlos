//CHECKSTYLE:OFF
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
 */

package io.github.carlos_emr.carlos.utility;

import java.util.Date;

import io.github.carlos_emr.carlos.commn.dao.OscarAppointmentDao;
import io.github.carlos_emr.carlos.commn.model.Appointment;

import io.github.carlos_emr.carlos.util.ConversionUtils;

public class AppointmentUtil {

    private static final String NONE = "(none)";

    private AppointmentUtil() {
    }

    public static String getNextAppointment(String demographicNo) {
        Date nextApptDate = null;
        if (demographicNo != null && !demographicNo.equalsIgnoreCase("") && !demographicNo.equalsIgnoreCase("null")) {
            return NONE;
        }

        OscarAppointmentDao dao = SpringUtils.getBean(OscarAppointmentDao.class);
        Appointment appt = dao.findNextAppointment(ConversionUtils.fromIntString(demographicNo));
        if (appt == null) {
            return NONE;
        }

        return ConversionUtils.toDateString(nextApptDate);
    }

}
