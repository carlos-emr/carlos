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

package io.github.carlos_emr.carlos.demographic.data;

import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.dao.RelationshipsDao;
import io.github.carlos_emr.carlos.commn.model.Relationships;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import io.github.carlos_emr.carlos.util.ConversionUtils;

import java.util.*;

/**
 * Manages inter-patient relationships such as family members, emergency contacts,
 * and substitute decision makers (SDMs) within the demographic system.
 *
 * <p>This class provides CRUD operations for demographic relationships, including
 * creating bidirectional relationships, soft-deleting relationships, and querying
 * relationships by demographic number or relationship ID. Relationships can optionally
 * be scoped to a specific facility.</p>
 *
 * <p>Relationships are stored via the {@link RelationshipsDao} and support flags for
 * substitute decision maker and emergency contact designations.</p>
 *
 * @see io.github.carlos_emr.carlos.commn.dao.RelationshipsDao
 * @see io.github.carlos_emr.carlos.commn.model.Relationships
 * @since 2026-03-17
 */
public class DemographicRelationship {

    /**
     * Constructs a new DemographicRelationship instance.
     */
    public DemographicRelationship() {
    }

	/**
	 * @param facilityId can be null
	 */
	public void addDemographicRelationship(String demographic, String linkingDemographic, String relationship, boolean sdm, boolean emergency, String notes, String providerNo, Integer facilityId) {
		Relationships relationships = new Relationships();
		relationships.setFacilityId(facilityId);
		relationships.setDemographicNo(ConversionUtils.fromIntString(demographic));
		relationships.setRelationDemographicNo(ConversionUtils.fromIntString(linkingDemographic));
		relationships.setRelation(relationship);
		relationships.setSubDecisionMaker(ConversionUtils.toBoolString(sdm));
		relationships.setEmergencyContact(ConversionUtils.toBoolString(emergency));
		relationships.setNotes(notes);
		relationships.setCreator(providerNo);
		relationships.setCreationDate(new Date());
        relationships.setDeleted(Boolean.FALSE);

        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        dao.persist(relationships);
    }

    /**
     * Soft-deletes a demographic relationship by setting its deleted flag to true.
     *
     * @param id String the relationship record ID to delete
     */
    public void deleteDemographicRelationship(String id) {
        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        Relationships relationships = dao.find(ConversionUtils.fromIntString(id));
        if (relationships == null) {
            MiscUtils.getLogger().error("Unable to find demographic relationship to delete");
        } else {
			relationships.setDeleted(Boolean.TRUE);
			dao.merge(relationships);
		}
	}

    /**
     * Retrieves all active relationships for a given demographic number.
     *
     * <p>Each relationship is returned as a Map with keys: "id", "demographic_no",
     * "relation", "sub_decision_maker", "emergency_contact", and "notes".</p>
     *
     * @param demographic String the demographic number to query relationships for
     * @return ArrayList&lt;Map&lt;String, String&gt;&gt; list of relationship maps, empty if none found
     */
    public ArrayList<Map<String, String>> getDemographicRelationships(String demographic) {
        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();

        List<Relationships> relationships = dao.findByDemographicNumber(ConversionUtils.fromIntString(demographic));

		if (relationships.isEmpty()) {
			MiscUtils.getLogger().warn("Unable to find demographic relationship for demographic {}", demographic);
			return list;
		}

        for (Relationships r : relationships) {
            HashMap<String, String> h = new HashMap<String, String>();
            h.put("id", r.getId().toString());
            h.put("demographic_no", String.valueOf(r.getRelationDemographicNo()));
            h.put("relation", r.getRelation());
            h.put("sub_decision_maker", r.getSubDecisionMaker());
            h.put("emergency_contact", r.getEmergencyContact());
            h.put("notes", r.getNotes());
            list.add(h);
        }

        return list;
    }

	/**
	 * Retrieves a specific relationship by its record ID.
	 *
	 * <p>Returns a list containing a single map with keys: "demographic_no",
	 * "relation_demographic_no", "relation", "sub_decision_maker", "emergency_contact",
	 * and "notes".</p>
	 *
	 * @param id String the relationship record ID
	 * @return ArrayList&lt;Map&lt;String, String&gt;&gt; list with the relationship map, empty if not found
	 */
	public ArrayList<Map<String, String>> getDemographicRelationshipsByID(String id) {
		RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
		Relationships r = dao.findActive(ConversionUtils.fromIntString(id));
		ArrayList<Map<String, String>> list = new ArrayList<Map<String, String>>();
		if (r == null) {
			MiscUtils.getLogger().warn("Unable to find demographic relationship for ID {}", id);
			return list;
		}

        Map<String, String> h = new HashMap<String, String>();
        h.put("demographic_no", ConversionUtils.toIntString(r.getDemographicNo()));
        h.put("relation_demographic_no", ConversionUtils.toIntString(r.getRelationDemographicNo()));
        h.put("relation", r.getRelation());
        h.put("sub_decision_maker", r.getSubDecisionMaker());
        h.put("emergency_contact", r.getEmergencyContact());
        h.put("notes", r.getNotes());
        list.add(h);

        return list;
    }

    /**
     * Returns the demographic number of the Substitute Decision Maker for a patient.
     *
     * <p>If multiple SDMs exist, returns the last one found.</p>
     *
     * @param demographic String the patient's demographic number
     * @return String the SDM's demographic number, or null if no SDM is assigned
     */
    public String getSDM(String demographic) {
        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        List<Relationships> rs = dao.findActiveSubDecisionMaker(ConversionUtils.fromIntString(demographic));
        String result = null;
        for (Relationships r : rs)
            result = ConversionUtils.toIntString(r.getRelationDemographicNo());
        return result;
    }

    /**
     * Retrieves SDM relationships with full contact details (name, phone, age).
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographic_no String the patient's demographic number
     * @return List&lt;Map&lt;String, Object&gt;&gt; list of relationship maps with contact details
     */
    public List<Map<String, Object>> getDemographicRelationshipsWithNamePhone(LoggedInInfo loggedInInfo, String demographic_no) {
        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        List<Relationships> rs = dao.findActiveSubDecisionMaker(ConversionUtils.fromIntString(demographic_no));

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();

        for (Relationships r : rs) {
            HashMap<String, Object> h = new HashMap<String, Object>();
            String demo = ConversionUtils.toIntString(r.getRelationDemographicNo());

            DemographicData dd = new DemographicData();
            Demographic demographic = dd.getDemographic(loggedInInfo, demo);
            h.put("lastName", demographic.getLastName());
            h.put("firstName", demographic.getFirstName());
            h.put("phone", demographic.getPhone());
            h.put("demographicNo", demo);
            h.put("relation", r.getRelation());

            h.put("subDecisionMaker", ConversionUtils.fromBoolString(r.getSubDecisionMaker()));
            h.put("emergencyContact", ConversionUtils.fromBoolString(r.getEmergencyContact()));
            h.put("notes", r.getNotes());
            h.put("age", demographic.getAge());
            list.add(h);
        }
        return list;
    }

    /**
     * Retrieves active relationships with contact details, filtered by facility.
     *
     * @param loggedInInfo LoggedInInfo the current user's session context
     * @param demographic_no String the patient's demographic number
     * @param facilityId Integer the facility ID to filter by
     * @return List&lt;Map&lt;String, Object&gt;&gt; list of relationship maps with contact details
     */
    public List<Map<String, Object>> getDemographicRelationshipsWithNamePhone(LoggedInInfo loggedInInfo, String demographic_no, Integer facilityId) {
        RelationshipsDao dao = SpringUtils.getBean(RelationshipsDao.class);
        List<Relationships> rs = dao.findActiveByDemographicNumberAndFacility(ConversionUtils.fromIntString(demographic_no), facilityId);

        List<Map<String, Object>> list = new ArrayList<Map<String, Object>>();
        for (Relationships r : rs) {
            HashMap<String, Object> h = new HashMap<String, Object>();
            String demo = ConversionUtils.toIntString(r.getRelationDemographicNo());

            DemographicData dd = new DemographicData();
            Demographic demographic = dd.getDemographic(loggedInInfo, demo);
            h.put("lastName", demographic.getLastName());
            h.put("firstName", demographic.getFirstName());
            h.put("phone", demographic.getPhone());
            h.put("demographicNo", demo);
            h.put("relation", r.getRelation());

            h.put("subDecisionMaker", ConversionUtils.fromBoolString(r.getSubDecisionMaker()));
            h.put("emergencyContact", ConversionUtils.fromBoolString(r.getEmergencyContact()));
            h.put("notes", r.getNotes());
            h.put("age", demographic.getAge());
            list.add(h);
        }

        return list;
    }

}
