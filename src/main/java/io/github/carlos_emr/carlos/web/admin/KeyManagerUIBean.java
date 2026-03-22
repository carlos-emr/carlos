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

package io.github.carlos_emr.carlos.web.admin;

import java.util.List;

import org.apache.commons.text.StringEscapeUtils;
import io.github.carlos_emr.carlos.commn.dao.OscarKeyDao;
import io.github.carlos_emr.carlos.commn.dao.ProfessionalSpecialistDao;
import io.github.carlos_emr.carlos.commn.dao.PublicKeyDao;
import io.github.carlos_emr.carlos.commn.model.OscarKey;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.commn.model.PublicKey;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * UI helper bean for the administration key management page.
 *
 * <p>Provides methods for retrieving and updating cryptographic public keys
 * and their associations with professional specialists. Used by the admin
 * key manager JSP to display and manage encryption keys for inter-EMR communication.
 *
 * @since 2012-08-13
 */
public final class KeyManagerUIBean {

    private static final PublicKeyDao publicKeyDao = (PublicKeyDao) SpringUtils.getBean(PublicKeyDao.class);
    private static final OscarKeyDao oscarKeyDao = (OscarKeyDao) SpringUtils.getBean(OscarKeyDao.class);
    private static final ProfessionalSpecialistDao professionalSpecialistDao = (ProfessionalSpecialistDao) SpringUtils.getBean(ProfessionalSpecialistDao.class);

    /**
     * Retrieves all public keys stored in the system.
     *
     * @return List&lt;PublicKey&gt; all registered public keys
     */
    public static List<PublicKey> getPublicKeys() {
        return (publicKeyDao.findAll());
    }

    /**
     * Retrieves a public key by its service name identifier.
     *
     * @param serviceName String the service name that uniquely identifies the public key
     * @return PublicKey the matching public key, or {@code null} if not found
     */
    public static PublicKey getPublicKey(String serviceName) {
        return (publicKeyDao.find(serviceName));
    }

    /**
     * Retrieves all professional specialists registered in the system.
     *
     * @return List&lt;ProfessionalSpecialist&gt; all professional specialist records
     */
    public static List<ProfessionalSpecialist> getProfessionalSpecialists() {
        return (professionalSpecialistDao.findAll());
    }

    /**
     * Returns the HTML-escaped service name of a public key for safe display.
     *
     * @param publicKey PublicKey the public key whose service name to escape
     * @return String the HTML-escaped service name identifier
     */
    public static String getSericeNameEscaped(PublicKey publicKey) {
        return (StringEscapeUtils.escapeHtml4(publicKey.getId()));
    }

    /**
     * Returns an HTML-escaped display string for a public key, formatted as "serviceName (type)".
     *
     * @param publicKey PublicKey the public key to generate a display string for
     * @return String the HTML-escaped display string combining service name and type
     */
    public static String getSericeDisplayString(PublicKey publicKey) {
        return (StringEscapeUtils.escapeHtml4(publicKey.getId() + " (" + publicKey.getType() + ')'));
    }

    /**
     * Returns an HTML-escaped display string for a professional specialist,
     * formatted as "lastName, firstName (id)".
     *
     * @param professionalSpecialist ProfessionalSpecialist the specialist to generate a display string for
     * @return String the HTML-escaped display string combining the specialist's name and ID
     */
    public static String getProfessionalSpecialistDisplayString(ProfessionalSpecialist professionalSpecialist) {
        return (StringEscapeUtils.escapeHtml4(professionalSpecialist.getLastName() + ", " + professionalSpecialist.getFirstName() + " (" + professionalSpecialist.getId() + ')'));
    }

    /**
     * Updates the professional specialist association for a given public key.
     *
     * @param serviceName String the service name of the public key to update
     * @param matchingProfessionalSpecialistId Integer the ID of the professional specialist to associate
     */
    public static void updateMatchingProfessionalSpecialist(String serviceName, Integer matchingProfessionalSpecialistId) {
        PublicKey publicKey = publicKeyDao.find(serviceName);
        publicKey.setMatchingProfessionalSpecialistId(matchingProfessionalSpecialistId);
        publicKeyDao.merge(publicKey);
    }

    /**
     * Retrieves the public key for this CARLOS EMR instance, HTML-escaped for safe display.
     *
     * @return String the HTML-escaped public key string, or an empty string if no key is configured
     */
    public static String getPublicOscarKeyEscaped() {
        OscarKey oscarKey = oscarKeyDao.find("oscar");

        if (oscarKey == null) return ("");

        return (StringEscapeUtils.escapeHtml4(oscarKey.getPublicKey()));
    }
}
