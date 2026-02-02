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
package io.github.carlos_emr.carlos.admin.web;

import io.github.carlos_emr.carlos.commn.dao.DemographicContactDao;
import io.github.carlos_emr.carlos.commn.dao.RelationshipsDao;
import io.github.carlos_emr.carlos.commn.model.DemographicContact;
import io.github.carlos_emr.carlos.commn.model.Relationships;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import io.github.carlos_emr.carlos.log.LogAction;

public class MigrateRelationshipsToContactsHelper {

    private static SecurityInfoManager securityInfoManager = SpringUtils.getBean(SecurityInfoManager.class);

    public static boolean doMigration(LoggedInInfo loggedInInfo) {

        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin", "r", null)) {
            throw new SecurityException("missing required sec object (_admin)");
        }

        RelationshipsDao relationshipsDao = SpringUtils.getBean(RelationshipsDao.class);
        DemographicContactDao demographicContactDao = SpringUtils.getBean(DemographicContactDao.class);

        for (Relationships relationship : relationshipsDao.findAll()) {
            DemographicContact dc = new DemographicContact();

            dc.setCreated(relationship.getCreationDate());
            dc.setCreator(relationship.getCreator());
            dc.setDeleted(relationship.getDeleted());
            dc.setDemographicNo(relationship.getDemographicNo());
            dc.setRole(relationship.getRelation());
            dc.setContactId(String.valueOf(relationship.getRelationDemographicNo()));
            dc.setFacilityId(relationship.getFacilityId());
            dc.setNote(relationship.getNotes());
            dc.setSdm(relationship.getSubDecisionMaker() != null && "1".equals(relationship.getSubDecisionMaker()) ? "true" : "");
            dc.setEc(relationship.getEmergencyContact() != null && "1".equals(relationship.getEmergencyContact()) ? "true" : "");
            dc.setActive(!dc.isDeleted());
            dc.setType(DemographicContact.TYPE_DEMOGRAPHIC);
            dc.setCategory(DemographicContact.CATEGORY_PERSONAL);
            dc.setConsentToContact(true);

            demographicContactDao.persist(dc);

            LogAction.addLog(loggedInInfo, "MIGRATION", "NewContacts", relationship.getId() + "->" + dc.getId(), String.valueOf(dc.getDemographicNo()), "");

            relationshipsDao.remove(relationship.getId());

        }

        return true;
    }
}
