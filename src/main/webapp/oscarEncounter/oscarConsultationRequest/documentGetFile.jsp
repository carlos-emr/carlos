<%--

    Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
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

    This software was written for the
    Department of Family Medicine
    McMaster University
    Hamilton
    Ontario, Canada


    Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos
    CARLOS has no affiliation with OSCAR or McMaster University.

--%>

<%@ taglib uri="/WEB-INF/security.tld" prefix="security" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_con" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_con");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>

<%@page
        import="java.math.*, java.util.*, java.io.*, java.sql.*, io.github.carlos_emr.*, java.net.*,io.github.carlos_emr.MyDateFormat" %>

<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.commn.dao.DocumentDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.Document" %>
<%@ page import="io.github.carlos_emr.OscarProperties" %>

<%
    DocumentDao documentDao = SpringUtils.getBean(DocumentDao.class);
%>


<%
    String filename = "", filetype = "", doc_no = "";
    String docdownload = OscarProperties.getInstance().getProperty("DOCUMENT_DIR");
    String downloadMethod = OscarProperties.getInstance().getProperty("DOCUMENT_DOWNLOAD_METHOD");
    session.setAttribute("docdownload", docdownload);
    if (request.getParameter("document") != null) {
        filename = request.getParameter("document");
        filetype = request.getParameter("type");
        doc_no = request.getParameter("doc_no");
        String filePath = docdownload + filename;
        if (filetype.compareTo("active") == 0) {
            response.setContentType("application/octet-stream");
            response.setHeader("Content-Disposition", "attachment;filename=\"" + filename + "\"");
            //read the file name.
            File f = new File(filePath);
            InputStream is = new FileInputStream(f);

            long length = f.length();
            byte[] bytes = new byte[(int) length];
            int offset = 0;
            int numRead = 0;
            while (offset < bytes.length
                    && (numRead = is.read(bytes, offset, bytes.length - offset)) >= 0) {
                offset += numRead;
            }
            is.close();
            ServletOutputStream outs = response.getOutputStream();
            outs.write(bytes);
            outs.flush();
            outs.close();

        } else {
            for (Document d : documentDao.findActiveByDocumentNo(Integer.parseInt(doc_no))) {
                out.print(d.getDocxml());
            }
        }

    } else {
%>
<fmt:setBundle basename="oscarResources"/><fmt:message key="dms.documentGetFile.msgFileNotfound"/>
<%}%>
