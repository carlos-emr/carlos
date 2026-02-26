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


package io.github.carlos_emr.carlos.encounter.oscarMeasurements.bean;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Comparator;
import java.util.Hashtable;
import java.util.List;
import java.util.Vector;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.ValidationsDao;
import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.commn.model.MeasurementType;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Validations;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.data.MeasurementTypes;
import io.github.carlos_emr.carlos.util.ConversionUtils;

public class EctMeasurementsDataBeanHandler {

    Vector<EctMeasurementsDataBean> measurementsDataVector = new Vector<EctMeasurementsDataBean>();

    public EctMeasurementsDataBeanHandler(Integer demo) {
        init(demo);
    }

    public EctMeasurementsDataBeanHandler(Integer demo, String type) {
        init(demo, type);
    }

    public boolean init(Integer demo) {
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        for (MeasurementType mt : dao.findMeasurementsTypes(demo)) {
            EctMeasurementsDataBean data = new EctMeasurementsDataBean();
            data.setType(mt.getType());
            data.setTypeDisplayName(mt.getTypeDisplayName());
            data.setTypeDescription(mt.getTypeDescription());
            data.setMeasuringInstrc(mt.getMeasuringInstruction());

            measurementsDataVector.add(data);

        }
        return true;
    }

    public boolean init(Integer demo, String type) {
        MeasurementTypes mt = MeasurementTypes.getInstance();
        EctMeasurementTypesBean mBean = mt.getByType(type);
        if (mBean != null) {
            ValidationsDao dao = SpringUtils.getBean(ValidationsDao.class);
            ProviderDao providerDao = SpringUtils.getBean(ProviderDao.class);
            Provider provider = null;

            for (Object[] o : dao.findValidationsBy(demo, type, ConversionUtils.fromIntString(mBean.getValidation()))) {
                Validations v = (Validations) o[0];
                Measurement m = (Measurement) o[1];
                provider = providerDao.getProvider(m.getProviderNo());

                String canPlot = null;
                String firstName = null;
                String lastName = null;

                boolean isNumeric = v.isNumeric() != null && v.isNumeric();
                if (isNumeric || v.getName().equalsIgnoreCase("Blood Pressure"))
                    canPlot = "true";
                else
                    canPlot = null;

                if (provider != null) {
                    firstName = provider.getFirstName();
                    lastName = provider.getLastName();
                }

                if (firstName == null && lastName == null) {
                    firstName = "Automatic";
                    lastName = "";
                }
                EctMeasurementsDataBean data = new EctMeasurementsDataBean(
                        m.getId().intValue(),
                        m.getType(),
                        mBean.getTypeDisplayName(),
                        mBean.getTypeDesc(),
                        "" + m.getDemographicId(),
                        firstName, lastName,
                        m.getDataField(),
                        m.getMeasuringInstruction(),
                        m.getComments(),
                        ConversionUtils.toDateString(m.getDateObserved()),
                        ConversionUtils.toDateString(m.getCreateDate()),
                        canPlot,
                        m.getDateObserved(),
                        m.getCreateDate());

                measurementsDataVector.add(data);

            }
        }
        return true;
    }

    public Collection<EctMeasurementsDataBean> getMeasurementsDataVector() {
        return measurementsDataVector;
    }

    public Collection<EctMeasurementsDataBean> getMeasurementsDataCollection() {
        return measurementsDataVector;
    }

    public static Hashtable<String, Object> getMeasurementDataById(String id) {
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        for (Object[] i : dao.findMeasurementsAndProviders(ConversionUtils.fromIntString(id))) {
            Measurement m = (Measurement) i[0];
            MeasurementType mt = (MeasurementType) i[1];
            Provider p = (Provider) i[2];
            return toHashTable(m, mt, p);
        }
        return new Hashtable<String, Object>();
    }

    public static List<EctMeasurementsDataBean> getMeasurementObjectByType(String type, Integer demographicNo) {
        List<EctMeasurementsDataBean> measurements = new ArrayList<EctMeasurementsDataBean>();

        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        for (Object[] i : dao.findMeasurementsAndProvidersByType(type, demographicNo)) {
            Measurement m = (Measurement) i[0];
            Provider p = (Provider) i[2];

            EctMeasurementsDataBean measurement = new EctMeasurementsDataBean();
            measurement.setId(m.getId());
            measurement.setMeasuringInstrc(m.getMeasuringInstruction());
            measurement.setType(m.getType());
            measurement.setProviderFirstName(p.getFirstName());
            measurement.setProviderLastName(p.getLastName());
            measurement.setDataField(m.getDataField());
            measurement.setComments(m.getComments());
            measurement.setDateObservedAsDate(m.getDateObserved());
            measurement.setDateEnteredAsDate(m.getCreateDate());
            measurements.add(measurement);
        }

        return measurements;
    }

    public static Hashtable<String, Object> getLast(String demo, String type) {
        MeasurementDao dao = SpringUtils.getBean(MeasurementDao.class);
        Object[] i = dao.findMeasurementsAndProvidersByDemoAndType(ConversionUtils.fromIntString(demo), type);
        if (i == null) {
            return new Hashtable<String, Object>();
        }

        Measurement m = (Measurement) i[0];
        Provider p = (Provider) i[1];
        MeasurementType mt = (MeasurementType) i[2];
        return toHashTable(m, mt, p);
    }

    private static Hashtable<String, Object> toHashTable(Measurement m, MeasurementType mt, Provider p) {
        Hashtable<String, Object> data = new Hashtable<String, Object>();
        data.put("type", mt.getTypeDisplayName());
        data.put("typeDisplayName", mt.getTypeDisplayName());
        data.put("typeDescription", mt.getTypeDescription());
        data.put("value", m.getDataField());
        data.put("measuringInstruction", m.getMeasuringInstruction());
        data.put("comments", m.getComments());
        data.put("dateObserved", ConversionUtils.toTimestampString(m.getDateObserved()));
        data.put("dateObserved_date", m.getDateObserved());
        data.put("dateEntered", ConversionUtils.toTimestampString(m.getCreateDate()));
        data.put("dateEntered_date", m.getCreateDate());
        data.put("provider_first", p.getFirstName());
        data.put("provider_last", p.getLastName());
        return data;
    }

    public static class EctMeasurementsDataBeanComparator implements Comparator<EctMeasurementsDataBean> {
        public int compare(EctMeasurementsDataBean o1, EctMeasurementsDataBean o2) {
            Comparable date1 = o1.getDateObservedAsDate();
            Comparable date2 = o2.getDateObservedAsDate();

            if (date1 != null && date2 != null) {
                if (date1 instanceof Calendar) {
                    date1 = ((Calendar) date1).getTime();
                }

                if (date2 instanceof Calendar) {
                    date2 = ((Calendar) date2).getTime();
                }

                return (date2.compareTo(date1));
            } else {
                return (0);
            }
        }
    }


}
