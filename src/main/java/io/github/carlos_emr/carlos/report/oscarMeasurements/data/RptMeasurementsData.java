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

package io.github.carlos_emr.carlos.report.oscarMeasurements.data;

import java.util.ArrayList;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.ConversionUtils;


/**
 * Data provider for measurement-based reports. Queries the measurement table to count
 * and retrieve distinct patients who have had measurements recorded within a given
 * date range. Used by CDM (Chronic Disease Management) reporting.
 *
 * @since 2001-01-01
 */
public class RptMeasurementsData {

    /**
     * Gets the number of Patient seen during aspecific time period
     *
     * @return number or Patients seen in Integer
     */
    public int getNbPatientSeen(String startDateA, String endDateA) {
        int nbPatient = 0;
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        for (Object o : dao.findByCreateDate(ConversionUtils.fromDateString(startDateA), ConversionUtils.fromDateString(endDateA))) {
            nbPatient = (Integer) o;
        }
        return nbPatient;
    }

    /**
     * get the number of patients during a specific time period
     *
     * @return ArrayList which contain the result in String format
     */
    public ArrayList getPatientsSeen(String startDate, String endDate) {
        ArrayList patients = new ArrayList();
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        for (Object[] o : dao.findByCreateDate(ConversionUtils.fromDateString(startDate), ConversionUtils.fromDateString(endDate))) {
            Integer i = (Integer) o[0];
            patients.add("" + i);
        }
        return patients;
    }
}
