//CHECKSTYLE:OFF
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
 */

package ca.openosp.openo.web.admin;

import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import ca.openosp.openo.commn.dao.OscarKeyDao;
import ca.openosp.openo.commn.dao.ProfessionalSpecialistDao;
import ca.openosp.openo.commn.dao.PublicKeyDao;
import ca.openosp.openo.commn.model.OscarKey;
import ca.openosp.openo.commn.model.ProfessionalSpecialist;
import ca.openosp.openo.commn.model.PublicKey;
import ca.openosp.openo.utility.SpringUtils;

public final class KeyManagerUIBean {

    private static final PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
    private static final OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
    private static final ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);

    public static List<PublicKey> getPublicKeys() {
        return (publicKeyDao.findAll());
    }

    public static PublicKey getPublicKey(String serviceName) {
        return (publicKeyDao.find(serviceName));
    }

    public static List<ProfessionalSpecialist> getProfessionalSpecialists() {
        return (professionalSpecialistDao.findAll());
    }

    public static String getSericeNameEscaped(PublicKey publicKey) {
        return (StringEscapeUtils.escapeHtml4(publicKey.getId()));
    }

    public static String getSericeDisplayString(PublicKey publicKey) {
        return (StringEscapeUtils.escapeHtml4(publicKey.getId() + " (" + publicKey.getType() + ')'));
    }

    public static String getProfessionalSpecialistDisplayString(ProfessionalSpecialist professionalSpecialist) {
        return (StringEscapeUtils.escapeHtml4(professionalSpecialist.getLastName() + ", " + professionalSpecialist.getFirstName() + " (" + professionalSpecialist.getId() + ')'));
    }

    public static void updateMatchingProfessionalSpecialist(String serviceName, Integer matchingProfessionalSpecialistId) {
        PublicKey publicKey = publicKeyDao.find(serviceName);
        publicKey.setMatchingProfessionalSpecialistId(matchingProfessionalSpecialistId);
        publicKeyDao.merge(publicKey);
    }

    public static String getPublicOscarKeyEscaped() {
        OscarKey oscarKey = oscarKeyDao.find("oscar");

        if (oscarKey == null) return ("");

        return (StringEscapeUtils.escapeHtml4(oscarKey.getPublicKey()));
    }
}
