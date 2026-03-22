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
package io.github.carlos_emr.carlos.webserv.rest;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

import io.github.carlos_emr.carlos.commn.dao.PharmacyInfoDao;
import io.github.carlos_emr.carlos.commn.model.PharmacyInfo;
import io.github.carlos_emr.carlos.webserv.rest.conversion.PharmacyInfoConverter;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PharmacyInfoTo1;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Defines service contract for create/read/update/delete operations on pharmacies.
 * <p>
 * TODO: switch DAOs to PharmacyManager
 */
@Path("/pharmacies/")
@Component("pharmacyService")
@Consumes(MediaType.APPLICATION_JSON)
/**
 * REST service for pharmacy information lookup and management operations.
 *
 * @since 2012-08-13
 */
public class PharmacyService extends AbstractServiceImpl {

    @Autowired
    private PharmacyInfoDao pharmacyInfoDao;

    private PharmacyInfoConverter converter = new PharmacyInfoConverter();

    /**
     * Loads all active pharmacies available on the system
     * <p/>
     * In case limit or offset parameters are set to null or zero, the entire result set is returned.
     *
     * @param offset First record in the entire result set to be returned
     * @param limit  Maximum total number of records that should be returned
     * @return Returns all pharmacy records available on the system
     */
    @GET
    @Path("/")
    public OscarSearchResponse<PharmacyInfoTo1> getPharmacies(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
        OscarSearchResponse<PharmacyInfoTo1> result = new OscarSearchResponse<PharmacyInfoTo1>();
        result.getContent().addAll(converter.getAllAsTransferObjects(getLoggedInInfo(), pharmacyInfoDao.findAll(offset, limit)));
        return result;
    }

    /**
     * Finds pharmacy with the specified id.
     *
     * @param id Id of the pharmacy to be found
     * @return Returns the pharmacy or null if it can not be found
     */
    @GET
    @Path("/{pharmacyId}")
    public PharmacyInfoTo1 getPharmacy(@PathParam("pharmacyId") Integer id) {
        return converter.getAsTransferObject(getLoggedInInfo(), pharmacyInfoDao.find(id));
    }

    /**
     * Adds pharmacy to the system
     *
     * @param pharmacyInfo Pharmacy info to be added
     * @return Returns the added pharmacy info
     */
    @POST
    @Path("/")
    public PharmacyInfoTo1 addPharmacy(PharmacyInfoTo1 pharmacyInfo) {
        return converter.getAsTransferObject(getLoggedInInfo(), pharmacyInfoDao.saveEntity(converter.getAsDomainObject(getLoggedInInfo(), pharmacyInfo)));
    }

    /**
     * Updates pharmacy on the system
     *
     * @param pharmacyInfo Pharmacy info to be updated
     * @return Returns the updated pharmacy info
     */
    @PUT
    @Path("/")
    public PharmacyInfoTo1 updatePharmacy(PharmacyInfoTo1 pharmacyInfo) {
        return converter.getAsTransferObject(getLoggedInInfo(), pharmacyInfoDao.saveEntity(converter.getAsDomainObject(getLoggedInInfo(), pharmacyInfo)));
    }

    /**
     * Deletes pharmacy from the system
     *
     * @param id ID of the pharmacy to be deleted
     * @return Returns the deleted pharmacy info
     */
    @DELETE
    @Path("/{pharmacyId}")
    public PharmacyInfoTo1 removePharmacy(@PathParam("pharmacyId") Integer id) {
        PharmacyInfo pharmacyInfo = pharmacyInfoDao.find(id);
        pharmacyInfo.setStatus(PharmacyInfo.DELETED);
        return converter.getAsTransferObject(getLoggedInInfo(), pharmacyInfoDao.saveEntity(pharmacyInfo));

    }

}
