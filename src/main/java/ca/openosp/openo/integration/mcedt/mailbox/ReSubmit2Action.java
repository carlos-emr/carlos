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

public class ReSubmit2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        List<BigInteger> resourceIds = getResourceIds(request);
        String serviceId = getServiceId(request);
        if (serviceId == null || serviceId.trim().equals("")) serviceId = getDefaultServiceId();

        try {

            ResourceResult result = null;
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(serviceId);
            if (resourceIds.size() > 0) result = delegate.submit(resourceIds);
            for (ResponseResult edtResponse : result.getResponse()) {
                if (edtResponse.getResult().getCode().equals("IEDTS0001")) {
                    addActionMessage(getText("uploadAction.submit.success", new String[]{McedtMessageCreator.resourceResultToString(result)}));
                } else {
                    addActionError(getText("uploadAction.submit.failure", new String[]{edtResponse.getResult().getMsg()}));
                }
            }

            List<DetailDataCustom> resourceList = getResourceList();

            request.getSession().setAttribute("resourceListSent", resourceList);

            return SUCCESS;
        } catch (Exception e) {
            logger.error("Unable to submit resource ", e);
            addActionError(getText("uploadAction.submit.failure", new String[]{McedtMessageCreator.exceptionToString(e)}));
            return SUCCESS;
        }
    }

    private List<DetailDataCustom> getResourceList() {
        Detail result = ActionUtils.getDetails(request);
        List<DetailDataCustom> resourceList = new ArrayList<DetailDataCustom>();
        if (result == null) {
            try {
                String resourceType = this.getResourceType();
                if (resourceType != null && resourceType.trim().isEmpty()) {
                    resourceType = null;
                }

                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
                result = delegate.list(resourceType, this.getStatusAsResourceStatus(), this.getPageNoAsBigInt());

                BigInteger resultSize = null;
                if (result != null)
                    resultSize = result.getResultSize();
                request.getSession().setAttribute("resultSize", resultSize);

                if (request.getSession().getAttribute("resourceTypeList") == null) {
                    this.setTypeListResult(ActionUtils.getTypeList(request, delegate));
                    request.getSession().setAttribute("resourceTypeList", this.getTypeListResult());
                } else {
                    this.setTypeListResult((TypeListResult) request.getSession().getAttribute("resourceTypeList"));
                }

                if (result != null && result.getData() != null) {

                    DetailDataCustom detailDataK;
                    for (DetailData detailData : result.getData()) {

                        //add to list if only of certain status
                        //if(ActionUtils.filterResourceStatus(detailData)){
                        detailDataK = new DetailDataCustom();
                        detailDataK = Action2Utils.mapDetailData(detailDataK, detailData, this.getTypeListResult().getData(), this.getServiceIdSent());
                        resourceList.add(detailDataK);
                        //}
                    }
                }


            } catch (Exception e) {
                logger.error("Unable to load resource list ", e);
                addActionError(getText("resourceAction.getResourceList.fault", new String[]{McedtMessageCreator.exceptionToString(e)}));
            }
        }
        return resourceList;
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