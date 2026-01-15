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

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Date;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.integration.mcedt.DelegateFactory;
import ca.openosp.openo.integration.mcedt.McedtMessageCreator;

import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.ResourceStatus;
import ca.ontario.health.edt.TypeListResult;


import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Resource2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("loadDownloadList".equals(method)) {
            return loadDownloadList();
        } else if ("loadSentList".equals(method)) {
            return loadSentList();
        } else if ("changeDisplay".equals(method)) {
            return changeDisplay();
        } 
        //functions needed for the upload page
        ActionUtils.removeSuccessfulUploads(request);
        ActionUtils.removeUploadResponseResults(request);
        ActionUtils.removeSubmitResponseResults(request);
        Date startDate = ActionUtils.getOutboxTimestamp();
        Date endDate = new Date();
        if (startDate != null && endDate != null) {
            ActionUtils.moveOhipToOutBox(startDate, endDate);
            
            /*
             * The method ActionUtils.moveObecToOutBox is slow with many files in 'OscarDocument/oscar/document/'.
             * To optimize, we will move OBEC files during generation rather than during MCEDT mailbox opening.
             * See ObecData.writeFile() for details on the updated process.
             */
            // ActionUtils.moveObecToOutBox(startDate,endDate);

            ActionUtils.setOutboxTimestamp(endDate);
        }
        ActionUtils.setUploadResourceId(request, new BigInteger("-1"));


        if (request.getSession().getAttribute("resourceList") != null) {
            request.getSession().removeAttribute("resourceList");
        }
        if (request.getSession().getAttribute("resourceID") != null) {
            request.getSession().removeAttribute("resourceID");
        }
        if (request.getSession().getAttribute("info") != null) {
            request.getSession().removeAttribute("info");
        }
        return SUCCESS;
    }

    //----------------------------------
    public String loadDownloadList() throws Exception {

        List<DetailDataCustom> resourceList;
        try {
            resourceList = loadList(ResourceStatus.DOWNLOADABLE);
            request.getSession().setAttribute("resourceListDL", resourceList);
        } catch (Exception e) {
            logger.error("Unable to load resource list ", e);

            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
            return SUCCESS;
        }

        return "successUserDownload";
    }

    public String loadSentList() throws Exception {
        List<DetailDataCustom> resourceList;
        List<DetailDataCustom> resourceListFiltered = new ArrayList<DetailDataCustom>();

        try {
            if (request.getSession().getAttribute("resourceTypeList") == null) {
                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
                this.setTypeListResult(getTypeList(request, delegate));
            }
            this.setStatus("UPLOADED");
            resourceList = loadList(ResourceStatus.UPLOADED);
            request.getSession().setAttribute("resourceListSent", resourceList);
            request.getSession().setAttribute("resourceStatus", "UPLOADED");
        } catch (Exception e) {
            return "successUserSent";
        }
        return "successUserSent";
    }

    public List<DetailDataCustom> loadList(ResourceStatus resourceStatus) throws Exception {
        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();

            if (request.getSession().getAttribute("resourceTypeList") == null) {
                this.setTypeListResult(getTypeList(request, delegate));
                request.getSession().setAttribute("resourceTypeList", this.getTypeListResult());
            } else {
                this.setTypeListResult((TypeListResult) request.getSession().getAttribute("resourceTypeList"));
            }

            return getResourceList(delegate, resourceStatus);
        } catch (Exception e) {
            logger.error("Unable to load resource list ", e);
            //saveErrors(request, ActionUtils.addMessage("resourceAction.getResourceList.fault", McedtMessageCreator.exceptionToString(e)));
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
            return null;
        }

    }

    private List<DetailDataCustom> getResourceList(EDTDelegate delegate, ResourceStatus resourceStatus) {
        Detail result = ActionUtils.getDetails(request);
        List<DetailDataCustom> resourceList = new ArrayList<DetailDataCustom>();


        if (result == null) {
            try {
                if (resourceType != null && resourceType.trim().isEmpty()) {
                    resourceType = null;
                }

                result = delegate.list(resourceType, resourceStatus, this.getPageNoAsBigInt());

                BigInteger resultSize = null;
                if (result != null)
                    resultSize = result.getResultSize();
                request.getSession().setAttribute("resultSize", resultSize);

                if (result != null && result.getData() != null) {

                    DetailDataCustom detailDataK;
                    for (DetailData detailData : result.getData()) {

                        detailDataK = new DetailDataCustom();
                        detailDataK = Action2Utils.mapDetailData(detailDataK, detailData, this.getTypeListResult().getData(), this.getServiceIdSent());
                        resourceList.add(detailDataK);
                    }

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
        return reset();
    }

    private String reset() throws Exception {
        ActionUtils.removeDetails(request);
        return execute();
    }


    private TypeListResult getTypeList(HttpServletRequest request, EDTDelegate delegate) {
        TypeListResult result = ActionUtils.getTypeList(request);
        if (result == null) {
            try {
                result = delegate.getTypeList();
                ActionUtils.setTypeList(request, result);
            } catch (Exception e) {
                logger.error("Unable to load type list", e);
                String errorMessage = McedtMessageCreator.exceptionToString(e);
                addActionError(getText("resourceAction.getTypeList.fault", new String[]{errorMessage}));
            }
        }
        return result;
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