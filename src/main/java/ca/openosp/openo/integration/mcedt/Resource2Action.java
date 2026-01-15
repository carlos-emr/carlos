//CHECKSTYLE:OFF
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
package ca.openosp.openo.integration.mcedt;

import static ca.openosp.openo.integration.mcedt.ActionUtils.getResourceIds;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import ca.ontario.health.edt.*;
import ca.openosp.openo.utility.MiscUtils;
import org.apache.logging.log4j.Logger;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

public class Resource2Action extends ActionSupport {
    HttpServletRequest request = ServletActionContext.getRequest();
    HttpServletResponse response = ServletActionContext.getResponse();

    private static Logger logger = MiscUtils.getLogger();

    @Override
    public String execute() throws Exception {
        String method = request.getParameter("method");
        if ("changeDisplay".equals(method)) {
            return changeDisplay();
        } else if ("reset".equals(method)) {
            return reset();
        } else if ("delete".equals(method)) {
            return delete();
        } else if ("submit".equals(method)) {
            return submit();
        } else if ("download".equals(method)) {
            return download();
        }
        EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
        this.setTypeListResult(getTypeList(request, delegate));
        this.setDetail(getResourceList(delegate));

        return SUCCESS;
    }

    public String changeDisplay() throws Exception {
        return reset();
    }

    private String reset() throws Exception {
        ActionUtils.removeDetails(request);
        return execute();
    }

    private Detail getResourceList(EDTDelegate delegate) {
        Detail result = ActionUtils.getDetails(request);
        if (result == null) {
            try {
                String resourceType = this.getResourceType();
                if (resourceType != null && resourceType.trim().isEmpty()) {
                    resourceType = null;
                }
                result = delegate.list(resourceType, this.getStatusAsResourceStatus(), this.getPageNoAsBigInt());
                ActionUtils.setDetails(request, result);
            } catch (Exception e) {
                logger.error("Unable to load resource list ", e);
                addActionError(getText("resourceAction.getResourceList.fault", new String[]{McedtMessageCreator.exceptionToString(e)}));
            }
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
                addActionError(getText("resourceAction.getTypeList.fault", new String[]{McedtMessageCreator.exceptionToString(e)}));
            }
        }
        return result;
    }

    public String delete() throws Exception {
        List<BigInteger> ids = getResourceIds(request);

        ResourceResult result = null;
        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            result = delegate.delete(ids);
        } catch (Exception e) {
            logger.error("Unable to delete", e);
            addActionError(getText("resourceAction.delete.fault", new String[]{McedtMessageCreator.exceptionToString(e)}));

        }
        reset();

        if (result != null) {
            for (ResponseResult r : result.getResponse()) {
                addActionMessage(getText("resourceAction.delete.success", new String[]{McedtMessageCreator.responseResultToString(r)}));
            }
        }
        return SUCCESS;
    }

    public String submit() {
        List<BigInteger> ids = getResourceIds(request);

        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            ResourceResult result = delegate.submit(ids);

            reset();
            addActionMessage(getText("resourceAction.submit.success", new String[]{McedtMessageCreator.resourceResultToString(result)}));
        } catch (Exception e) {
            logger.error("Unable to submit", e);
            addActionError(getText("resourceAction.submit.failure", new String[]{McedtMessageCreator.exceptionToString(e)}));
        }

        return SUCCESS;
    }

    public String download() throws Exception {
        List<BigInteger> ids = getResourceIds(request);
        DownloadResult downloadResult = null;
        try {
            EDTDelegate delegate = DelegateFactory.getEDTDelegateInstance();
            downloadResult = delegate.download(ids);
        } catch (Exception e) {
            addActionError(getText("resourceAction.download.fault", new String[]{McedtMessageCreator.exceptionToString(e)}));
            return SUCCESS;
        }

        response.setContentType("application/zip");
        response.setHeader("Content-Transfer-Encoding", "binary");
        response.setHeader("Content-Disposition", "attachment; filename=\"mcedt_download_" + System.currentTimeMillis() + ".zip\"");

        ZipOutputStream zos = new ZipOutputStream(response.getOutputStream());
        for (DownloadData d : downloadResult.getData()) {
            byte[] inputBytes = d.getContent();

            String name = d.getResourceID().toString();
            ZipEntry ze = new ZipEntry(name);
            ze.setComment(d.getDescription());
            ze.setSize(inputBytes.length);

            zos.putNextEntry(ze);
            zos.write(inputBytes);
            zos.closeEntry();
            zos.flush();
        }
        zos.close();

        return null;
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
