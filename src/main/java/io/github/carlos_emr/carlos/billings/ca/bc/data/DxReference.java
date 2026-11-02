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


package io.github.carlos_emr.carlos.billings.ca.bc.data;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.billings.ca.bc.service.BillingDiagnosisService;
import org.springframework.dao.DataAccessException;
import jakarta.persistence.PersistenceException;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.util.UtilDateUtilities;

/**
 * @author jay
 */
public class DxReference {
    private static final Logger _log = MiscUtils.getLogger();
    private final BillingDiagnosisService diagnosisService = SpringUtils.getBean(BillingDiagnosisService.class);

    /**
     * Creates a new instance of DxReference
     */
    public DxReference() {
    }
    //select dx_code1, dx_code2, dx_code3,service_date from billingmaster order by service_date desc;
    /*
+----------+----------+----------+--------------+
| dx_code1 | dx_code2 | dx_code3 | service_date |
+----------+----------+----------+--------------+
| 250      |          |          | 20061114     |
+----------+----------+----------+--------------+
     */

    /*
     * method looks in a paitnest
     */
    @SuppressWarnings("unchecked")
    public List<DxCode> getLatestDxCodes(String demo) {
        ArrayList<DxCode> list = new ArrayList<DxCode>();
        try {
            for (BillingDiagnosisService.DiagnosisDto diagnosis : diagnosisService.findRecentDiagnoses(demo)) {
                DxCode code = new DxCode(UtilDateUtilities.StringToDate(diagnosis.serviceDate(), "yyyyMMdd"), diagnosis.code());
                code.setDesc(diagnosis.description());
                list.add(code);
            }
        } catch (DataAccessException | PersistenceException e) {
            _log.error("Unable to read recent billing diagnoses ({})", e.getClass().getSimpleName());
        }
        Collections.sort(list, Collections.reverseOrder());

        return list;
    }

    public class DxCode implements Comparable {

        public DxCode(Date d, String dx) {
            this.setDx(dx);
            this.setDate(d);
        }

        private String dx = null;
        private Date date = null;
        private String desc = null;

        public String getDx() {
            return dx;
        }

        public void setDx(String dx) {
            this.dx = dx;
        }

        public Date getDate() {
            return date;
        }

        public void setDate(Date date) {
            this.date = date;
        }

        public void setDesc(String desc) {
            this.desc = desc;
        }

        public String getDesc() {
            return this.desc;
        }


        public int getNumMonthSinceDate() {
            return getNumMonths(date, Calendar.getInstance().getTime());
        }

        public int getNumMonthsSinceDate(Date d) {
            return getNumMonths(date, d);
        }

        private int getNumMonths(Date dStart, Date dEnd) {
            int i = 0;
            try {
                _log.debug("Getting the number of months between " + dStart.toString() + " and " + dEnd.toString());
                Calendar calendar = Calendar.getInstance();
                calendar.setTime(dStart);
                while (calendar.getTime().before(dEnd) || calendar.getTime().equals(dEnd)) {
                    calendar.add(Calendar.MONTH, 1);
                    i++;
                }
                i--;
                if (i < 0) {
                    i = 0;
                }
            } catch (Exception e) {
                _log.warn("Date was NULL in DxReference");
            }
            return i;
        }

        public int compareTo(Object o) {
            Date d = ((DxReference.DxCode) o).getDate();
            if (d == null && date == null) return 0;
            if (d == null && date != null) return -1;
            if (d != null && date == null) return 1;

            if (date.after(d)) {
                return 1;
            } else if (date.before(d)) {
                return -1;
            }
            return 0;
        }
    }


}
