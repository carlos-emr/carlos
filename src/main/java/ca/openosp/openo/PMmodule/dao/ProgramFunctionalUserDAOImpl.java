//CHECKSTYLE:OFF
/**
 * Copyright (c) 2024. Magenta Health. All Rights Reserved.
 * <p>
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
 * Modifications made by Magenta Health in 2024.
 */

package ca.openosp.openo.PMmodule.dao;

import java.util.List;

import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import ca.openosp.openo.PMmodule.model.FunctionalUserType;
import ca.openosp.openo.PMmodule.model.ProgramFunctionalUser;
import ca.openosp.openo.utility.MiscUtils;

/**
 * Data Access Object implementation for managing Program Functional Users and Functional User Types.
 * <p>
 * This DAO provides CRUD operations for functional user types and program functional users,
 * including retrieval, saving, and deletion of entities. It uses Hibernate SessionFactory
 * for database interactions.
 * </p>
 *
 * @see ProgramFunctionalUserDAO
 * @see FunctionalUserType
 * @see ProgramFunctionalUser
 */
@Repository
@Transactional
public class ProgramFunctionalUserDAOImpl implements ProgramFunctionalUserDAO {

    private static Logger log = MiscUtils.getLogger();
    
    @Autowired
    private SessionFactory sessionFactory;

    /**
     * Gets the current Hibernate session.
     *
     * @return the current Hibernate session
     */
    private Session getSession() {
        return sessionFactory.getCurrentSession();
    }

    /**
     * Retrieves all functional user types.
     *
     * @return a list of all functional user types
     */
    @Override
    public List<FunctionalUserType> getFunctionalUserTypes() {
        String sSQL = "from FunctionalUserType";
        @SuppressWarnings("unchecked")
        List<FunctionalUserType> results = getSession().createQuery(sSQL).list();

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserTypes: # of results=" + results.size());
        }
        return results;
    }

    /**
     * Retrieves a functional user type by its ID.
     *
     * @param id the ID of the functional user type
     * @return the functional user type, or null if not found
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    @Override
    public FunctionalUserType getFunctionalUserType(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        FunctionalUserType result = getSession().get(FunctionalUserType.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserType: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Saves or updates a functional user type.
     *
     * @param fut the functional user type to save or update
     * @throws IllegalArgumentException if fut is null
     */
    @Override
    public void saveFunctionalUserType(FunctionalUserType fut) {
        if (fut == null) {
            throw new IllegalArgumentException();
        }

        getSession().saveOrUpdate(fut);

        if (log.isDebugEnabled()) {
            log.debug("saveFunctionalUserType:" + fut.getId());
        }
    }

    /**
     * Deletes a functional user type by its ID.
     *
     * @param id the ID of the functional user type to delete
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    @Override
    public void deleteFunctionalUserType(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        getSession().delete(getFunctionalUserType(id));

        if (log.isDebugEnabled()) {
            log.debug("deleteFunctionalUserType:" + id);
        }
    }

    /**
     * Retrieves functional users by program ID.
     *
     * @param programId the ID of the program
     * @return a list of functional user types associated with the program
     * @throws IllegalArgumentException if programId is null or less than or equal to 0
     */
    @Override
    public List<FunctionalUserType> getFunctionalUsers(Long programId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        String sSQL = "from ProgramFunctionalUser pfu where pfu.ProgramId = :programId";
        @SuppressWarnings("unchecked")
        List<FunctionalUserType> results = getSession().createQuery(sSQL)
                .setParameter("programId", programId)
                .list();

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUsers: programId=" + programId + ",# of results=" + results.size());
        }
        return results;
    }

    /**
     * Retrieves a program functional user by its ID.
     *
     * @param id the ID of the program functional user
     * @return the program functional user, or null if not found
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    @Override
    public ProgramFunctionalUser getFunctionalUser(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        ProgramFunctionalUser result = getSession().get(ProgramFunctionalUser.class, id);

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUser: id=" + id + ",found=" + (result != null));
        }

        return result;
    }

    /**
     * Saves or updates a program functional user.
     *
     * @param pfu the program functional user to save or update
     * @throws IllegalArgumentException if pfu is null
     */
    @Override
    public void saveFunctionalUser(ProgramFunctionalUser pfu) {
        if (pfu == null) {
            throw new IllegalArgumentException();
        }

        getSession().saveOrUpdate(pfu);

        if (log.isDebugEnabled()) {
            log.debug("saveFunctionalUser:" + pfu.getId());
        }
    }

    /**
     * Deletes a program functional user by its ID.
     *
     * @param id the ID of the program functional user to delete
     * @throws IllegalArgumentException if id is null or less than or equal to 0
     */
    @Override
    public void deleteFunctionalUser(Long id) {
        if (id == null || id.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        getSession().delete(getFunctionalUser(id));

        if (log.isDebugEnabled()) {
            log.debug("deleteFunctionalUser:" + id);
        }
    }

    /**
     * Retrieves the program ID for a functional user by program ID and user type ID.
     *
     * @param programId the ID of the program
     * @param userTypeId the ID of the user type
     * @return the program ID if found, null otherwise
     * @throws IllegalArgumentException if programId or userTypeId is null or less than or equal to 0
     */
    @Override
    public Long getFunctionalUserByUserType(Long programId, Long userTypeId) {
        if (programId == null || programId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }
        if (userTypeId == null || userTypeId.intValue() <= 0) {
            throw new IllegalArgumentException();
        }

        Long result = null;

        String query = "select pfu.ProgramId from ProgramFunctionalUser pfu where pfu.ProgramId = :programId and pfu.UserTypeId = :userTypeId";
        @SuppressWarnings("unchecked")
        List<Long> results = getSession().createQuery(query)
                .setParameter("programId", programId)
                .setParameter("userTypeId", userTypeId)
                .list();
        
        if (!results.isEmpty()) {
            result = results.get(0);
        }

        if (log.isDebugEnabled()) {
            log.debug("getFunctionalUserByUserType: programId=" + programId + ",userTypeId=" + userTypeId + ",result="
                    + result);
        }

        return result;
    }
}
