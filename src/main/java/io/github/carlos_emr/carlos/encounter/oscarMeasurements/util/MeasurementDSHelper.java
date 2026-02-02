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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.util;

import org.apache.logging.log4j.Logger;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.demographic.data.DemographicData;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBean;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean.EctMeasurementsDataBeanHandler;

import java.util.*;

/**
 * @author jay
 */
public class MeasurementDSHelper {
    private static Logger log = MiscUtils.getLogger();

    EctMeasurementsDataBean mdb = null;
    java.util.Date dob = null;
    String sex = null;
    String demographic_no = null;
    boolean problem = false;
    private boolean inRange = false;

    /**
     * Creates a new instance of MeasurementDSHelper
     */
    public MeasurementDSHelper() {
    }

    public void log(String logMessage) {
        log.debug(logMessage);
    }

    public MeasurementDSHelper(LoggedInInfo loggedInInfo, String demo) {
        log.debug("sdfsdf ==" + demo);
        demographic_no = demo;
        DemographicData dd = new DemographicData();
        dob = dd.getDemographicDOB(loggedInInfo, demo);
        sex = dd.getDemographicSex(loggedInInfo, demo);

        log.debug("goin out");
    }

    public MeasurementDSHelper(LoggedInInfo loggedInInfo, EctMeasurementsDataBean mdb) {
        this.mdb = mdb;
        DemographicData dd = new DemographicData();
        dob = dd.getDemographicDOB(loggedInInfo, mdb.getDemo());
        sex = dd.getDemographicSex(loggedInInfo, mdb.getDemo());

    }

    public boolean setMeasurement(String measurementType) {
        boolean setM = false;
        log.debug("demo " + this.demographic_no + " type " + measurementType);
        EctMeasurementsDataBeanHandler mdbh = new EctMeasurementsDataBeanHandler(Integer.valueOf(this.demographic_no), measurementType);
        Collection col = mdbh.getMeasurementsDataVector();
        if (col.size() > 0) {
            this.mdb = (EctMeasurementsDataBean) col.iterator().next();
            setM = true;
        }


        return setM;
    }

    public boolean setMeasurement(String measurementType, Date startDate, Date endDate) {
        boolean setM = false;
        log.debug("demo " + this.demographic_no + " type " + measurementType);
        EctMeasurementsDataBeanHandler mdbh = new EctMeasurementsDataBeanHandler(Integer.valueOf(this.demographic_no), measurementType);

        List<EctMeasurementsDataBean> col2 = new ArrayList<EctMeasurementsDataBean>();
        for (Object o : mdbh.getMeasurementsDataVector()) {
            EctMeasurementsDataBean edb = (EctMeasurementsDataBean) o;
            Date d = edb.getDateObservedAsDate();
            if (d != null) {

                if (startDate == null && endDate == null) {
                    col2.add(edb);
                }

                if (startDate != null && endDate != null) {
                    if (d.after(startDate) && d.before(endDate)) {
                        col2.add(edb);
                    }
                } else if (startDate != null && endDate == null) {
                    if (d.after(startDate)) {
                        col2.add(edb);
                    }
                } else if (startDate == null && endDate != null) {
                    if (d.before(endDate)) {
                        col2.add(edb);
                    }
                }

            }
        }
        if (col2.size() > 0) {
            this.mdb = col2.iterator().next();
            setM = true;
        }


        return setM;
    }


    public boolean hasProblem() {
        return problem;
    }

    public boolean isMale() {
        boolean ismale = false;
        if (sex != null && sex.trim().equalsIgnoreCase("M")) {
            ismale = true;
        }
        return ismale;
    }

    public boolean isFemale() {
        boolean isfemale = false;
        if (sex != null && sex.trim().equalsIgnoreCase("F")) {
            isfemale = true;
        }
        return isfemale;
    }

    public boolean isDataEqualTo(String str) {
        boolean equal = false;
        try {
            equal = mdb != null && mdb.getDataField() != null && mdb.getDataField().equalsIgnoreCase(str);
        } catch (Exception e) {
            MiscUtils.getLogger().error("Error processing " + str, e);
            problem = true;
        }
        return equal;
    }

    public double getDataAsDouble() {
        log.debug("dataAsDouble");

        double ret = -1;
        String dataField = null;

        if (mdb != null) {
            dataField = mdb.getDataField();
        }

        if (dataField != null) {
            /*
             * filter out comparators that were inserted
             * accidentally in older measurement datasets.
             */
            if (dataField.contains("<")) {
                dataField = dataField.replaceAll("<", "");
            }

            if (dataField.contains(">")) {
                dataField = dataField.replaceAll(">", "");
            }

            if (!dataField.isEmpty()) {
                try {
                    ret = Double.parseDouble(dataField);
                } catch (Exception e) {
                    MiscUtils.getLogger().error("Fatal error while parsing double value {} for Flowsheet type {} in demographic number {}", ret, mdb.getType(), mdb.getDemo(), e);
                    problem = true;
                }
            }
        }
        log.debug("DOUBLE val : {}", ret);
        return ret;
    }

    public double getNumberFromSplit(String delimiter, int number) {
        double ret = -1;
        try {
            String data = mdb.getDataField();
            log.debug("Trying to parse " + data);
            ret = Double.parseDouble(data.split(delimiter)[number]);
        } catch (Exception e) {

            MiscUtils.getLogger().error("Error", e);
            problem = true;
        }
        return ret;
    }

    public void setIndicationColor(String c) {
        log.debug("SETTING COLOUR TO " + c);
        mdb.setIndicationColour(c);
    }

    public boolean isInRange() {
        return inRange;
    }

    public void setInRange(boolean inRange) {
        this.inRange = inRange;
    }

    public String getDateObserved() {
        return mdb.getDateObserved();
    }

    public int getLastDateRecordedInMths() {
        int mths = 0;

        Date date = mdb.getDateObservedAsDate();
        Date now = new Date();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(date);
        while (calendar.getTime().before(now)) {
            calendar.add(Calendar.MONTH, 1);
            ++mths;
        }

        return mths;
    }

}
