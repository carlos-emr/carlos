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


/*
 * MeasurementMapConfig.java
 *
 * Created on September 28, 2007, 10:15 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */
package io.github.carlos_emr.carlos.encounter.oscarMeasurements.data;

import java.util.*;

import io.github.carlos_emr.carlos.commn.dao.MeasurementMapDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementsExtDao;
import io.github.carlos_emr.carlos.commn.dao.RecycleBinDao;
import io.github.carlos_emr.carlos.commn.model.MeasurementMap;
import io.github.carlos_emr.carlos.commn.model.MeasurementsExt;
import io.github.carlos_emr.carlos.commn.model.RecycleBin;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * @author wrighd
 */
public class MeasurementMapConfig {

    private final MeasurementMapDao measurementMapDao = SpringUtils.getBean(MeasurementMapDao.class);
    private final MeasurementsExtDao measurementsExtDao = SpringUtils.getBean(MeasurementsExtDao.class);

    public MeasurementMapConfig() {
    }

    public List<String> getLabTypes() {
        return measurementMapDao.findDistinctLabTypes();
    }

    public List<HashMap<String, String>> getMappedCodesFromLoincCodes(String loincCode) {
        List<HashMap<String, String>> ret = new LinkedList<>();

        for (MeasurementMap map : measurementMapDao.findByLoincCode(loincCode)) {
            HashMap<String, String> ht = new HashMap<String, String>();
            ht.put("id", map.getId().toString());
            ht.put("loinc_code", map.getLoincCode());
            ht.put("ident_code", map.getIdentCode());
            ht.put("name", map.getName());
            ht.put("lab_type", map.getLabType());
            ret.add(ht);
        }
        return ret;
    }

    public HashMap<String, HashMap<String, String>> getMappedCodesFromLoincCodesHash(String loincCode) {
        HashMap<String, HashMap<String, String>> ret = new HashMap<String, HashMap<String, String>>();

        for (MeasurementMap map : measurementMapDao.findByLoincCode(loincCode)) {
            HashMap<String, String> ht = new HashMap<String, String>();
            ht.put("id", map.getId().toString());
            ht.put("loinc_code", map.getLoincCode());
            ht.put("ident_code", map.getIdentCode());
            ht.put("name", map.getName());
            ht.put("lab_type", map.getLabType());
            ret.put(map.getLabType(), ht);
        }
        return ret;
    }

    public List<String> getDistinctLoincCodes() {
        List<String> results = measurementMapDao.findDistinctLoincCodes();
        Collections.sort(results);
        return results;
    }

    public String getLoincCodeByIdentCode(String identifier) {
        if (identifier != null && identifier.trim().length() > 0) {

            for (MeasurementMap map : measurementMapDao.getMapsByIdent(identifier)) {
                return map.getLoincCode();
            }
        }
        return null;
    }

    public boolean isTypeMappedToLoinc(String measurementType) {
        int size = measurementMapDao.getMapsByIdent(measurementType).size();
        if (size > 0)
            return true;
        return false;
    }

    public LoincMapEntry getLoincMapEntryByIdentCode(String identCode) {
        for (MeasurementMap map : measurementMapDao.getMapsByIdent(identCode)) {
            LoincMapEntry loincMapEntry = new LoincMapEntry();
            loincMapEntry.setId(map.getId().toString());
            loincMapEntry.setLoincCode(map.getLoincCode());
            loincMapEntry.setIdentCode(map.getIdentCode());
            loincMapEntry.setName(map.getName());
            loincMapEntry.setLabType(map.getLabType());
            return loincMapEntry;
        }

        return null;
    }

    public List<MeasurementMap> getLoincCodes(String searchString) {
        searchString = "%" + searchString.replaceAll("\\s", "%") + "%";
        return measurementMapDao.searchMeasurementsByName(searchString);
    }

    public List<MeasurementMap> getMeasurementMap(String searchString) {
        searchString = "%" + searchString.replaceAll("\\s", "%") + "%";
        return measurementMapDao.findMeasurementsByName(searchString);
    }

    /**
     * Return List of maps containing an identifier and association object.
     * ie: List["type": type, "identifier": identifier, "name": name]
     */
    public ArrayList<HashMap<String, String>> getUnmappedMeasurements(String type) {

        ArrayList<HashMap<String, String>> unmappedLabList = new ArrayList<>();
        // get all currently mapped measurements. Only PATHL7 for now
        List<String> measurementMap = measurementMapDao.findDistinctLoincCodesByLabType(MeasurementMap.LAB_TYPE.PATHL7);
        if (measurementMap == null) {
            measurementMap = Collections.emptyList();
        }

        // eliminate the mapped measurements from measurementsExt to get the unmapped result
        List<Integer> measurementIdList = measurementsExtDao.findUnmappedMeasuremntIds(measurementMap);
        if (measurementIdList == null) {
            measurementIdList = Collections.emptyList();
        }

        List<MeasurementsExt> unmappedLabs = measurementsExtDao.getMeasurementsExtListByMeasurementIdList(measurementIdList);
        int currentId = 0;
        HashMap<String, String> labtable = null;
        int count = 0;
        for (MeasurementsExt unmappedLab : unmappedLabs) {
            int selectedId = unmappedLab.getMeasurementId();
            if (currentId != selectedId || count == (unmappedLabs.size() - 1)) {
                if (currentId > 0) {
                    labtable.put("type", MeasurementMap.LAB_TYPE.PATHL7.name());
                    unmappedLabList.add(labtable);
                }
                labtable = new HashMap<>();
            }
            if ("identifier".equalsIgnoreCase(unmappedLab.getKeyVal())) {
                labtable.put("identifier", unmappedLab.getVal());
            }
            if ("name".equalsIgnoreCase(unmappedLab.getKeyVal())) {
                labtable.put("name", unmappedLab.getVal());
            }
            currentId = selectedId;
            count++;
        }
        return unmappedLabList;
    }

    public void mapMeasurement(String identifier, String loinc, String name, String type) {
        MeasurementMap mm = new MeasurementMap();
        mm.setLoincCode(loinc);
        mm.setIdentCode(identifier);
        mm.setName(name);
        mm.setLabType(type);
        measurementMapDao.persist(mm);
    }

    public void removeMapping(String id, String provider_no) {
        String ident_code = "";
        String loinc_code = "";
        String name = "";
        String lab_type = "";

        MeasurementMap map = measurementMapDao.find(Integer.parseInt(id));
        if (map != null) {
            ident_code = map.getIdentCode();
            loinc_code = map.getLoincCode();
            name = map.getName();
            lab_type = map.getLabType();


            measurementMapDao.remove(map.getId());

            RecycleBin rb = new RecycleBin();
            rb.setProviderNo(provider_no);
            rb.setUpdateDateTime(new Date());
            rb.setTableName("measurementMap");
            rb.setKeyword(id);
            rb.setTableContent("<id>" + id + "</id><ident_code>" + ident_code + "</ident_code><loinc_code>" + loinc_code + "</loinc_code><name>" + name + "</name><lab_type>" + lab_type + "</lab_type>");

            RecycleBinDao recycleBinDao = SpringUtils.getBean(RecycleBinDao.class);
            recycleBinDao.persist(rb);
        }
    }

    /**
     * Only one identifier per type is allowed to be mapped to a single loinc code
     * Return true if there is already an identifier mapped to the loinc code.
     */
    public boolean checkLoincMapping(String loinc, String type) {
        List<MeasurementMap> maps = measurementMapDao.findByLoincCodeAndLabType(loinc, type);
        return maps.size() > 0;
    }


    public class mapping {
        public String code;
        public String name;

    }

}
