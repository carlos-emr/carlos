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

import java.util.List;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MultivaluedMap;

import org.apache.commons.lang3.time.DateUtils;
import io.github.carlos_emr.carlos.commn.model.DrugProduct;
import io.github.carlos_emr.carlos.commn.model.DrugProductTemplate;
import io.github.carlos_emr.carlos.commn.model.ProductLocation;
import io.github.carlos_emr.carlos.managers.DrugDispensingManager;
import io.github.carlos_emr.carlos.managers.DrugProductManager;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugProductResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugProductTemplateResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRESTResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.ProductLocationResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugProductTemplateTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugProductTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ProductLocationTo1;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Path("/productDispensing")
@Component("productDispensingService")
@Consumes(MediaType.APPLICATION_JSON)
public class ProductDispensingService extends AbstractServiceImpl {

    @Autowired
    private DrugDispensingManager drugDispensingManager;

    @Autowired
    private DrugProductManager drugProductManager;

    @GET
    public String helloWorld() {
        return "Hello World " + drugDispensingManager;
    }

    @POST
    @Path("/saveDrugProduct")
    @Produces("application/json")
    @Consumes("application/x-www-form-urlencoded")
    public DrugProductResponse saveDrugProduct(MultivaluedMap<String, String> params) throws Exception {

        DrugProduct drugProduct = new DrugProduct();
        drugProduct.setId(Integer.parseInt(params.getFirst("product.id")));
        drugProduct.setName(params.getFirst("product.name"));
        drugProduct.setCode(params.getFirst("product.code"));

        // We cannot parse the location value if it is not set, going to default to 1 if not an int
        String locationStr = params.getFirst("product.location");
        String defaultLocation = "1";
        if (locationStr == null) {
            locationStr = defaultLocation;
        }
        
        drugProduct.setLocation(Integer.parseInt(locationStr));
        drugProduct.setLotNumber(params.getFirst("product.lotNumber"));
        drugProduct.setExpiryDate(DateUtils.parseDate(params.getFirst("product.expiryDate"), new String[]{"yyyy-MM-dd"}));
        drugProduct.setAmount(Integer.parseInt(params.getFirst("product.amount")));


        int totalToCreate = 1;
        if (params.getFirst("productBulkTotal") != null) {
            try {
                totalToCreate = Integer.parseInt(params.getFirst("productBulkTotal"));
            } catch (NumberFormatException e) {
                MiscUtils.getLogger().error("invalid number to create, defaulting to 1");
            }
        }

        DrugProduct result = null;

        if (drugProduct.getId() > 0) {
            //edit
            DrugProduct savedDrugProduct = drugProductManager.getDrugProduct(getLoggedInInfo(), drugProduct.getId());
            savedDrugProduct.setName(drugProduct.getName());
            savedDrugProduct.setCode(drugProduct.getCode());
            savedDrugProduct.setLocation(drugProduct.getLocation());
            savedDrugProduct.setLotNumber(drugProduct.getLotNumber());
            savedDrugProduct.setExpiryDate(drugProduct.getExpiryDate());

            drugProductManager.updateDrugProduct(getLoggedInInfo(), savedDrugProduct);
            result = savedDrugProduct;
        } else {
            for (int x = 0; x < totalToCreate; x++) {
                drugProduct.setId(null);
                drugProductManager.saveDrugProduct(getLoggedInInfo(), drugProduct);
                result = drugProduct;
            }
        }


        return getDrugProduct(result.getId());
    }

    @GET
    @Path("/drugProduct/{drugProductId}")
    @Produces("application/json")
    public DrugProductResponse getDrugProduct(@PathParam("drugProductId") Integer drugProductId) {
        DrugProduct result = drugProductManager.getDrugProduct(getLoggedInInfo(), drugProductId);
        DrugProductResponse response = new DrugProductResponse();
        DrugProductTo1 to = new DrugProductTo1();
        BeanUtils.copyProperties(result, to);
        response.getContent().add(to);
        return response;
    }

    @GET
    @Path("/drugProducts")
    @Produces("application/json")
    public DrugProductResponse getAllDrugProducts(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit,
                                                  @QueryParam("limitByName") String limitByName, @QueryParam("limitByLot") String limitByLot, @QueryParam("limitByLocation") String limitByLocation,
                                                  @QueryParam("availableOnly") boolean availableOnly) {
        List<DrugProduct> results = null;

        int count = drugProductManager.getAllDrugProductsByNameAndLotCount(getLoggedInInfo(), limitByName, limitByLot, (limitByLocation != null && limitByLocation.length() > 0) ? Integer.valueOf(limitByLocation) : null, availableOnly);
        results = drugProductManager.getAllDrugProductsByNameAndLot(getLoggedInInfo(), offset, limit, limitByName, limitByLot, (limitByLocation != null && limitByLocation.length() > 0) ? Integer.valueOf(limitByLocation) : null, availableOnly);

        DrugProductResponse response = new DrugProductResponse();
        for (DrugProduct result : results) {
            DrugProductTo1 to = new DrugProductTo1();
            BeanUtils.copyProperties(result, to);
            response.getContent().add(to);
        }
        response.setTotal(count);
        return response;
    }

    @GET
    @Path("/drugProducts/byCode")
    @Produces("application/json")
    public DrugProductResponse getAllDrugProductsGroupedByCode(@QueryParam("offset") Integer offset, @QueryParam("limit") Integer limit) {
        List<DrugProduct> results = drugProductManager.getAllDrugProductsGroupedByCode(getLoggedInInfo(), offset, limit);
        DrugProductResponse response = new DrugProductResponse();
        for (DrugProduct result : results) {
            DrugProductTo1 to = new DrugProductTo1();
            BeanUtils.copyProperties(result, to);
            response.getContent().add(to);
        }
        return response;
    }

    @GET
    @Path("/drugProducts/uniqueNames")
    @Produces("application/json")
    public AbstractSearchResponse<String> getUniqueDrugProductNames() {
        List<String> results = drugProductManager.findUniqueDrugProductNames(getLoggedInInfo());
        AbstractSearchResponse<String> response = new AbstractSearchResponse<String>();
        for (String result : results) {
            response.getContent().add(result);
        }
        return response;
    }

    @GET
    @Path("/drugProducts/uniqueLots")
    @Produces("application/json")
    public AbstractSearchResponse<String> getUniqueDrugProducLotsByName(@QueryParam("name") String name) {

        List<String> results = drugProductManager.findUniqueDrugProductLotsByName(getLoggedInInfo(), name);
        AbstractSearchResponse<String> response = new AbstractSearchResponse<String>();
        for (String result : results) {
            response.getContent().add(result);
        }
        return response;
    }

    @POST
    @Path("/deleteDrugProduct/{drugProductId}")
    @Produces("application/json")
    public GenericRESTResponse deleteDrugProduct(@PathParam("drugProductId") Integer drugProductId) {
        drugProductManager.deleteDrugProduct(getLoggedInInfo(), drugProductId);

        GenericRESTResponse response = new GenericRESTResponse();
        response.setMessage("Product deleted");


        return response;
    }

    @GET
    @Path("/productLocations")
    @Produces("application/json")
    public ProductLocationResponse listProductLocations() {
        List<ProductLocation> productLocations = drugProductManager.getProductLocations();


        ProductLocationResponse response = new ProductLocationResponse();

        for (ProductLocation result : productLocations) {
            ProductLocationTo1 to = new ProductLocationTo1();
            BeanUtils.copyProperties(result, to);
            response.getProductLocations().add(to);
        }

        return response;
    }

    @GET
    @Path("/status/{drugId}")
    @Produces("application/json")
    public GenericRESTResponse getDispensingStatus(@PathParam("drugId") Integer drugId) {
        GenericRESTResponse response = new GenericRESTResponse();
        response.setMessage(drugDispensingManager.getStatus(drugId));

        return response;
    }

    @GET
    @Path("/drugProductTemplates")
    @Produces("application/json")
    public DrugProductTemplateResponse listProductTemplates() {
        List<DrugProductTemplate> templates = drugProductManager.getDrugProductTemplates();


        DrugProductTemplateResponse response = new DrugProductTemplateResponse();

        for (DrugProductTemplate result : templates) {
            DrugProductTemplateTo1 to = new DrugProductTemplateTo1();
            BeanUtils.copyProperties(result, to);
            response.getTemplates().add(to);
        }

        return response;
    }
}
