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

import static ca.openosp.openo.integration.mcedt.McedtConstants.REQUEST_ATTR_KEY_RESOURCE_ID;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.openosp.openo.utility.MiscUtils;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;
import ca.openosp.openo.integration.mcedt.DelegateFactory;
import ca.openosp.openo.integration.mcedt.McedtMessageCreator;
import ca.openosp.openo.integration.mcedt.ResourceForm;

import ca.openosp.OscarProperties;
import ca.openosp.openo.util.ConversionUtils;
import ca.ontario.health.edt.Detail;
import ca.ontario.health.edt.DetailData;
import ca.ontario.health.edt.DownloadData;
import ca.ontario.health.edt.DownloadResult;
import ca.ontario.health.edt.EDTDelegate;
import ca.ontario.health.edt.ResourceStatus;
import ca.ontario.health.edt.TypeListData;
import ca.ontario.health.edt.TypeListResult;

import com.opensymphony.xwork2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Download2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();
    private boolean isFileToDownload = false;

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("download".equals(method)) {
            return download();
        } else if ("getLastDownloadedID".equals(method)) {
            return getLastDownloadedID();
        } else if ("cancel".equals(method)) {
            return cancel();
        } else if ("userDownload".equals(method)) {
            return userDownload();
        } else if ("changeDisplay".equals(method)) {
            return changeDisplay();
        }

        try {
            if (request.getSession().getAttribute("resourceTypeList") == null) {
                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
                this.setTypeListResult(getTypeList(request, delegate));
                request.getSession().setAttribute("resourceTypeList", this.getTypeListResult());
            } else {
                this.setTypeListResult((TypeListResult) request.getSession().getAttribute("resourceTypeList"));
            }
            Detail result = ActionUtils.getDetails(request);
            if (result == null) {
                for (String serviceId : ActionUtils.getServiceIds()) {
                    EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(serviceId);
                    result = getResourceList(delegate, serviceId, result);
                }
                List<DetailDataCustom> resourceList = this.getData();
                if (resourceList.size() > 0) {
                    //ActionUtils.setDetails(request, result);
                    //Collections.sort(resourceList, DetailDataCustom.ResourceIdComparator);
                    //setting the first element to downloading to view on the jsp
                    resourceList.get(0).setDownloadStatus("Downloading");
                    this.setData(resourceList);

                    request.getSession().setAttribute("resourceList", resourceList);
                    request.getSession().setAttribute("resourceID", resourceList.get(0).getResourceID());
                } else {
                    request.getSession().setAttribute("resourceID", BigInteger.ZERO);
                }
                this.setDetail(result);
            }

        } catch (Exception e) {
            logger.error("Unable to load resource list ", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
            return SUCCESS;
        }

        return SUCCESS;
    }

    private Detail getResourceList(EDTDelegate delegate, String serviceId, Detail result) {
        try {
            String resourceType = this.getResourceType();
            if (resourceType != null && resourceType.trim().isEmpty()) {
                resourceType = null;
            }
            //original code
            //result = delegate.list(resourceType, this.getStatusAsResourceStatus(), this.getPageNoAsBigInt());

            //filtering out the downloadable files
            this.getStatusAsResourceStatus();
            result = delegate.list(resourceType, ResourceStatus.DOWNLOADABLE, this.getPageNoAsBigInt());

            List<DetailDataCustom> resourceList = this.getData();
            if (resourceList == null || resourceList.size() < 1) resourceList = new ArrayList<DetailDataCustom>();

            if (result != null && result.getData() != null && result.getResultSize() != null) {
                /*filtering the list to contain only the files that have not been downloaded*/
                //get last downloaded resourceid
                BigInteger lastDownLoadedID = new BigInteger(getLastDownloadedID());

                //creating list with only new downloadable files
                DetailDataCustom detailDataK;
                for (DetailData detailData : result.getData()) {
                    if (detailData.getResourceID().compareTo(lastDownLoadedID) > 0) {
                        detailDataK = new DetailDataCustom();
                        detailDataK.setCreateTimestamp(detailData.getCreateTimestamp());
                        detailDataK.setDescription(detailData.getDescription());
                        detailDataK.setModifyTimestamp(detailData.getModifyTimestamp());
                        detailDataK.setResourceID(detailData.getResourceID());

                        //detailDataK.setResourceType(detailData.getResourceType());
                        detailDataK.setResourceType(getTypeDescription(detailData.getResourceType()));

                        detailDataK.setResult(detailData.getResult());
                        detailDataK.setStatus(detailData.getStatus());
                        detailDataK.setDownloadStatus("Waiting");
                        detailDataK.setServiceId(serviceId);

                        resourceList.add(detailDataK);
                    }
                }
                if (resourceList.size() > 0) {
                    //ActionUtils.setDetails(request, result);
                    Collections.sort(resourceList, DetailDataCustom.ResourceIdComparator);
                    this.setData(resourceList);
                    request.getSession().setAttribute("resourceList", resourceList);
                }
            }

        } catch (Exception e) {
            logger.error("Unable to load resource list ", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
        }

        return result;
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

    private String getTypeDescription(String typeCode) {
        String typeDesc = "";
        for (TypeListData typeListData : this.getTypeListResult().getData()) {
            if (typeListData.getResourceType().trim().equalsIgnoreCase(typeCode.trim())) {
                typeDesc = typeListData.getDescriptionEn();
                break;
            }
        }
        return typeDesc;
    }

    public String download() throws Exception {
        List<BigInteger> ids = getResourceIds(request);
        Collections.sort(ids);

        List<DetailDataCustom> resourceList = ActionUtils.getResourceList(request);
        String serviceId = new String();
        for (DetailDataCustom resource : resourceList) {
            if (resource.getResourceID().equals(ids.get(0))) {
                serviceId = resource.getServiceId();
                break;
            }
        }
        //-
        DownloadResult downloadResult = null;

        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(serviceId);

            downloadResult = delegate.download(ids);

            //----------start to save file
            for (DownloadData d : downloadResult.getData()) {
                String inboxFolder = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
                File document = new File(inboxFolder + File.separator + d.getDescription());
                byte[] inputBytes = d.getContent();


                FileUtils.writeByteArrayToFile(document, inputBytes);
                updateLastDownloadedID(d.getResourceID().toString());

            }
            //----------end of saving file

            //updating the downloading file status to Downloaded
            for (DetailDataCustom detailDatak : resourceList) {
                if (detailDatak.getResourceID().equals(ids.get(0))) {
                    detailDatak.setDownloadStatus("Download Completed");
                }
            }

            //updating the next waiting file status to Downloading to display to user
            boolean isFileToWating = false;
            for (DetailDataCustom detailDatak : resourceList) {
                if (detailDatak.getDownloadStatus().equals("Waiting")) {
                    detailDatak.setDownloadStatus("Downloading");
                    request.getSession().setAttribute("resourceID", detailDatak.getResourceID());
                    isFileToWating = true;
                    break;
                }
            }

            if (!isFileToWating) {
                request.getSession().setAttribute("resourceID", BigInteger.ZERO);
                ActionUtils.removeResourceList(request);
            }


            //}

        } catch (Exception e) {
            if (ActionUtils.getResourceList(request) != null) {
                ActionUtils.removeResourceList(request);
            }
            if (request.getSession().getAttribute("resourceID") != null) {
                request.getSession().removeAttribute("resourceID");
            }
            logger.error("Unable to load resource list ", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
            return SUCCESS;
        }

        //return null;
        return SUCCESS;
    }

    private String getLastDownloadedID() {
        String resourceID = "0";
        String inboxFolder = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
        String lastDownloadedFile = OscarProperties.getInstance().getProperty("mcedt.last.downloadedID.file");
        Path path = Paths.get(inboxFolder, lastDownloadedFile);
        try {
            File document;

            if (Files.exists(path, LinkOption.NOFOLLOW_LINKS)) {
                document = path.toFile();
            } else {
                document = Files.createFile(path).toFile();
            }

            List<String> lastId = FileUtils.readLines(document);

            if (lastId.size() > 0 && StringUtils.isNumeric(lastId.get(0))) {
                resourceID = lastId.get(0);
            }
        } catch (Exception e) {
            logger.error("Unable to get Last Download ID ", e);
        }

        return resourceID;
    }

    private void updateLastDownloadedID(String lastID) {
        boolean writeResult = false;
        String inboxFolder = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
        String lastDownloadedFile = OscarProperties.getInstance().getProperty("mcedt.last.downloadedID.file");


        try {
            File document = new File(inboxFolder + File.separator + lastDownloadedFile);
            FileUtils.write(document, lastID, false);

        } catch (Exception e) {
            logger.error("Unable to update Last Download ID ", e);
        }

    }

    static List<BigInteger> getResourceIds(HttpServletRequest request) {
        String[] resourceIds = request.getParameterValues(REQUEST_ATTR_KEY_RESOURCE_ID);

        List<BigInteger> ids = new ArrayList<BigInteger>();
        if (resourceIds == null) {
            return ids;
        }

        for (String i : resourceIds) {
            ids.add(BigInteger.valueOf(ConversionUtils.fromIntString(i)));
        }
        return ids;
    }

    public String cancel() throws Exception {
        if (request.getSession().getAttribute("resourceList") != null) {
            request.getSession().removeAttribute("resourceList");
        }
        if (request.getSession().getAttribute("resourceID") != null) {
            request.getSession().removeAttribute("resourceID");
        }
        return "cancel";
    }

    public String userDownload() throws Exception {
        List<BigInteger> ids = getResourceIds(request);
        Collections.sort(ids);
        DownloadResult downloadResult = null;

        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(this.getServiceId() == null ? ActionUtils.getDefaultServiceId() : this.getServiceId());

            downloadResult = delegate.download(ids);

            //----------start to save file
            for (DownloadData d : downloadResult.getData()) {
                String inboxFolder = OscarProperties.getInstance().getProperty("ONEDT_INBOX");
                File document = new File(inboxFolder + File.separator + d.getDescription());
                byte[] inputBytes = d.getContent();

                FileUtils.writeByteArrayToFile(document, inputBytes);

            }
            //----------end of saving file
            addActionMessage(getText("resourceAction.submit.success", new String[]{McedtMessageCreator.downloadResultToString(downloadResult)}));

        } catch (Exception e) {
            logger.error("Unable to load resource list ", e);
            String errorMessage = McedtMessageCreator.exceptionToString(e);
            addActionError(getText("resourceAction.getResourceList.fault", new String[]{errorMessage}));
            return "error";
        }

        return SUCCESS;
    }

    public String changeDisplay() throws Exception {
        List<DetailDataCustom> resourceList = getResourceList();

        request.getSession().setAttribute("resourceListSent", resourceList);

        return SUCCESS;
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

                EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance(this.getServiceId());
                result = delegate.list(resourceType, ResourceStatus.DOWNLOADABLE, this.getPageNoAsBigInt());

                if (request.getSession().getAttribute("resourceTypeList") == null) {
                    this.setTypeListResult(ActionUtils.getTypeList(request, delegate));
                    request.getSession().setAttribute("resourceTypeList", this.getTypeListResult());
                } else {
                    this.setTypeListResult((TypeListResult) request.getSession().getAttribute("resourceTypeList"));
                }

                if (result != null && result.getData() != null && result.getResultSize() != null) {

                    DetailDataCustom detailDataK;
                    for (DetailData detailData : result.getData()) {
                        detailDataK = new DetailDataCustom();
                        ResourceForm resourceForm2 = new ResourceForm();
                        resourceForm2.setTypeListResult(this.getTypeListResult());
                        resourceForm2.setServiceIdSent(this.getServiceId());
                        detailDataK = ActionUtils.mapDetailData(resourceForm2, detailDataK, detailData);
                        resourceList.add(detailDataK);

                    }

                    if (resourceList.size() > 0) {
                        //Collections.sort(resourceList, DetailDataCustom.ResourceIdComparator);
                        request.getSession().setAttribute("resourceListDL", resourceList);
                    }
                } else if (result.getResultSize() == null) {
                    request.getSession().removeAttribute("resourceListDL");
                    // if a result is returned with no size, meaning you are accessing a list that is not permitted, one response will be returned holding the error message
                    addActionError(getText("resourceAction.getResourceList.fault", result.getData().get(0).getResult().getMsg()));

                }
            } catch (Exception e) {
                logger.error("Unable to load resource list ", e);
                addActionError(getText("resourceAction.getResourceList.fault", result.getData().get(0).getResult().getMsg()));
            }
        }
        return resourceList;
    }

    private String resourceType;
    private String status;
    private Integer pageNo;
    private String serviceId;

    private TypeListResult typeListResult;
    private Detail detail;

    private List<DetailDataCustom> data = new ArrayList<DetailDataCustom>();

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


    public List<DetailDataCustom> getData() {
        return data;
    }

    public void setData(List<DetailDataCustom> data) {
        this.data = data;
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

    public String getServiceId() {
        return serviceId;
    }

    public void setServiceId(String serviceId) {
        this.serviceId = serviceId;
    }

}