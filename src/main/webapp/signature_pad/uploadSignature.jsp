<%--


    Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
    This software is published under the GPL GNU General Public License.
    This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License
    as published by the Free Software Foundation; either version 2
    of the License, or (at your option) any later version.

    This program is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with this program; if not, write to the Free Software
    Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.

    This software was written for
    Centre for Research on Inner City Health, St. Michael's Hospital,
    Toronto, Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="http://java.sun.com/jsp/jstl/core" prefix="c" %>

<c:set var="roleName" value='${sessionScope.userrole}, ${sessionScope.user}'/>
<c:set var="authenticated" value="false"/>

<security:oscarSec roleName="${pageScope.roleName}" objectName="_con" rights="w" reverse="false">
    <c:set var="authenticated" value="true"/>
</security:oscarSec>

<c:if test="${authenticated eq true}">

    <%@page import="io.github.carlos_emr.carlos.utility.DigitalSignatureUtils" %>
    <%@page import="io.github.carlos_emr.carlos.utility.MiscUtils" %>
    <%@page import="java.io.FileOutputStream" %>
    <%@page import="java.io.InputStream" %>
    <%@page import="io.github.carlos_emr.carlos.utility.LoggedInInfo" %>
    <%@page import="org.apache.commons.codec.binary.Base64" %>
    <%@ page import="io.github.carlos_emr.carlos.commn.model.DigitalSignature" %>
    <%@ page import="io.github.carlos_emr.carlos.managers.DigitalSignatureManager" %>
    <%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
    <%@ page import="io.github.carlos_emr.carlos.commn.model.enumerator.ModuleType" %>
    <%

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        String signatureId = "";
        String uploadSource = request.getParameter("source");
        String imageString = request.getParameter("signatureImage");
        String demographic = request.getParameter("demographicNo");
        boolean saveToDB = "true".equals(request.getParameter("saveToDB"));
        String signatureKey = request.getParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY);
		ModuleType moduleType = ModuleType.getByName(request.getParameter(ModuleType.class.getSimpleName()));

        if (signatureKey != null) {
            String filename = DigitalSignatureUtils.getTempFilePath(signatureKey);

            if ("IPAD".equalsIgnoreCase(uploadSource) && imageString != null && !imageString.isEmpty()) {

                try (FileOutputStream fos = new FileOutputStream(filename)) {

                    imageString = imageString.substring(imageString.indexOf(",") + 1);

                    Base64 b64 = new Base64();
                    byte[] imageByteData = imageString.getBytes();
                    byte[] imageData = b64.decode(imageByteData);

                    if (imageData != null) {
                        fos.write(imageData);
                        MiscUtils.getLogger().debug("Signature uploaded: {}, size={}", filename, imageData.length);
                    }

                } catch (Exception e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    MiscUtils.getLogger().error("Error uploading signature from IPAD: {}", filename, e);
                }

            } else if (uploadSource == null) {

                try (FileOutputStream fos = new FileOutputStream(filename);
                     InputStream is = request.getInputStream()) {

                    int i = 0;
                    int counter = 0;

                    while ((i = is.read()) != -1) {
                        fos.write(i);
                        counter++;
                    }
                    MiscUtils.getLogger().debug("Signature uploaded : " + filename + ", size=" + counter);
                } catch (Exception e) {
                    response.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, e.getMessage());
                    MiscUtils.getLogger().error("Error uploading signature: {}", filename, e);
                }
            }

            if (saveToDB) {

                int demographicNo = -1;

                if (demographic != null && !demographic.isEmpty()) {
                    demographicNo = Integer.parseInt(demographic);
                }

                DigitalSignatureManager digitalSignatureManager = SpringUtils.getBean(DigitalSignatureManager.class);
                DigitalSignature signature = digitalSignatureManager.processAndSaveDigitalSignature(loggedInInfo,
                        request.getParameter(DigitalSignatureUtils.SIGNATURE_REQUEST_ID_KEY),
						demographicNo, moduleType);
                if (signature != null) {
                    signatureId = "" + signature.getId();
                }
            }

            response.setStatus(HttpServletResponse.SC_OK);
        }
    %>
    <input type="hidden" name="signatureId" value="<%=signatureId%>"/>
</c:if>
