//CHECKSTYLE:OFF
/**
 * Copyright (c) 2014-2015. KAI Innovations Inc. All Rights Reserved.
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
package ca.openosp.openo.integration.mcedt.mailbox;

import static ca.openosp.openo.integration.mcedt.mailbox.ActionUtils.getDefaultServiceId;
import static ca.openosp.openo.integration.mcedt.mailbox.ActionUtils.getResourceIds;
import static ca.openosp.openo.integration.mcedt.mailbox.ActionUtils.getServiceId;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.ontario.health.edt.*;
import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.integration.mcedt.DelegateFactory;
import ca.openosp.openo.integration.mcedt.McedtMessageCreator;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Info2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("deleteFiles".equals(method)) {
            return deleteFiles();
        } else if ("changeDisplay".equals(method)) {
            return changeDisplay();
        }

        List<BigInteger> resourceIds = getResourceIds(request);
        String serviceId = getServiceId(request);
        if (serviceId == null || serviceId.trim().equals("")) serviceId = getDefaultServiceId();

        try {

            //temorary code as mcedt is not working
			/*DetailData detailData = new DetailData();
			detailData.setResourceID(resourceIds.get(0));
			detailData.setDescription("description");
			detailData.setResourceType("resourceType");						
			request.setAttribute("detaildata", detailData);
			
			Detail detail = new Detail();
			detail.getData().add(detailData);
			request.setAttribute("detail", detail);
			
			request.getSession().setAttribute("info", "true");*/
            //----------

            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(serviceId);
            Detail detail = delegate.info(resourceIds);
            request.setAttribute("detail", detail);
            request.getSession().setAttribute("info", "true");

            return SUCCESS;
        } catch (Exception e) {
            logger.error("Unable to load resource info ", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("updateAction.unspecified.errorLoading", new String[]{errorMessage}));
            request.getSession().setAttribute("info", "false");
            return SUCCESS;
        }
    }

    public String deleteFiles() throws Exception {
        List<BigInteger> ids = getResourceIds(request);
        String serviceId = getServiceId(request);
        if (serviceId == null || serviceId.trim().equals("")) serviceId = getDefaultServiceId();

        ResourceResult result = null;
        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(serviceId);
            result = delegate.delete(ids);
        } catch (Exception e) {
            logger.error("Unable to delete", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.delete.fault", new String[]{errorMessage}));
        }


        if (result != null) {
            for (ResponseResult r : result.getResponse()) {
                addActionMessage(getText("resourceAction.delete.success", new String[]{McedtMessageCreator.responseResultToString(r)}));
            }
        }
        //get the updated list from mcedt and save to session
        List<DetailDataCustom> resourceList = getResourceList(request);
        request.getSession().setAttribute("resourceListSent", resourceList);
        return SUCCESS;
    }

    private List<DetailDataCustom> getResourceList(HttpServletRequest request) {
        Detail result = ActionUtils.getDetails(request);
        List<DetailDataCustom> resourceList = new ArrayList<DetailDataCustom>();
        //ResourceForm resourceForm = (ResourceForm) form;

        if (result == null) {
            try {
                String resourceType = this.getResourceType();
                if (resourceType != null && resourceType.trim().isEmpty()) {
                    resourceType = null;
                }

                BigInteger resultSize = null;

                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(this.getServiceIdSent());
                result = delegate.list(resourceType, this.getStatusAsResourceStatus(), this.getPageNoAsBigInt());

                if (result != null) {
                    resultSize = result.getResultSize();
                }
                request.getSession().setAttribute("resultSize", resultSize);

                if (request.getSession().getAttribute("resourceTypeList") == null) {
                    this.setTypeListResult(ActionUtils.getTypeList(request, delegate));
                    request.getSession().setAttribute("resourceTypeList", this.getTypeListResult());
                } else {
                    this.setTypeListResult((TypeListResult) request.getSession().getAttribute("resourceTypeList"));
                }

                if (result != null && result.getData() != null && result.getResultSize() != null) {

                    DetailDataCustom detailDataK;
                    for (DetailData detailData : result.getData()) {

                        //add to list if only of certain status
                        //if(ActionUtils.filterResourceStatus(detailData)){
                        detailDataK = new DetailDataCustom();
                        detailDataK = Action2Utils.mapDetailData(detailDataK, detailData, this.getTypeListResult().getData(), this.getServiceIdSent());
                        resourceList.add(detailDataK);
                        //}
                    }

                    if (resourceList.size() > 0) {
                        //Collections.sort(resourceList, DetailDataCustom.ResourceIdComparator);
                        request.getSession().setAttribute("resourceListDL", resourceList);
                    }
                } else if (result == null) {
                    // No documents found
                } else if (result.getResultSize() == null) {
                    // if a result is returned with no size, meaning you are accessing a list that is not permitted, one response will be returned holding the error message
                    String errorMessage = result.getData().get(0).getResult().getMsg();
                    addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
                }

            } catch (Exception e) {
                logger.error("Unable to load resource list ", e);
                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));

            }
        }
        return resourceList;
    }

    public String changeDisplay() throws Exception {

        //ResourceForm resourceForm = (ResourceForm) form;
        String prvStatus = (String) request.getSession().getAttribute("resourceStatus");
        String currStatus = this.getStatus();
        if (prvStatus.equalsIgnoreCase(currStatus)) {
            this.setPageNo(1);
            request.getSession().setAttribute("resourceStatus", currStatus);
        }

        List<DetailDataCustom> resourceList = getResourceList(request);

        request.getSession().setAttribute("resourceListSent", resourceList);

        return SUCCESS;
    }

    private String resourceType;
    private String status;
    private Integer pageNo;

    private TypeListResult typeListResult;
    private Detail detail;
    private String serviceIdSent;

    public TypeListResult getTypeListResult() {
        return typeListResult;
    }

    public void setTypeListResult(TypeListResult typeListResult) {
        this.typeListResult = typeListResult;
    }

    public Detail getDetail() {
        return detail;
    }

    public void setDetail(Detail detail) {
        this.detail = detail;
    }

    public void removeResource(BigInteger resourceId) {
        if (resourceId == null) {
            return;
        }

        Iterator<DetailData> it = getDetail().getData().iterator();
        while (it.hasNext()) {
            DetailData d = it.next();

            if (resourceId.equals(d.getResourceID())) {
                it.remove();
            }
        }
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPageNo() {
        return pageNo;
    }

    public void setPageNo(Integer pageNo) {
        this.pageNo = pageNo;
    }

    public List<String> getResourceStatusValues() {
        List<String> result = new ArrayList<String>();
        for (ResourceStatus r : ResourceStatus.values()) {
            char[] name = r.name().toCharArray();
            name[0] = Character.toUpperCase(name[0]);
            result.add(new String(name));
        }
        return result;
    }

    public ResourceStatus getStatusAsResourceStatus() {
        if (getStatus() == null) {
            return null;
        }

        for (ResourceStatus r : ResourceStatus.values()) {
            if (r.name().equalsIgnoreCase(getStatus())) {
                return r;
            }
        }

        return null;
    }

    public BigInteger getPageNoAsBigInt() {
        if (getPageNo() == null) {
            return null;
        }
        return BigInteger.valueOf(getPageNo().longValue());
    }

    public String getServiceIdSent() {
        return serviceIdSent;
    }

    public void setServiceIdSent(String serviceId) {
        this.serviceIdSent = serviceId;
    }
}