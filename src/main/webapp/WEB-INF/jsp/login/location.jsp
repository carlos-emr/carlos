<%-- Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved. This software is
    published under the GPL GNU General Public License. This program is free software; you can redistribute it and/or
    modify it under the terms of the GNU General Public License as published by the Free Software Foundation; either
    version 2 of the License, or (at your option) any later version. This program is distributed in the hope that it
    will be useful, but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
    PARTICULAR PURPOSE. See the GNU General Public License for more details. You should have received a copy of the GNU
    General Public License along with this program; if not, write to the Free Software Foundation, Inc., 59 Temple Place
    - Suite 330, Boston, MA 02111-1307, USA. This software was written for the Department of Family Medicine McMaster
    University Hamilton Ontario, Canada Now maintained by the CARLOS EMR Project (2026+).
    https://github.com/carlos-emr/carlos CARLOS has no affiliation with OSCAR or McMaster University. --%>
                            <%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
                                <fmt:setBundle basename="oscarResources" />
                                            <%@ include file="/WEB-INF/jsp/common/webAppContextAndSuperMgr.jsp" %>
                                                <!DOCTYPE html
                                                    PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
                                                <html>
                                                <p>&nbsp;</p>
                                                <h3 align="center">
                                                    <fmt:message key="provider.selectClinicSite" />
                                                </h3>

                                                <head>
                                                    <link rel="icon"
                                                        href="${pageContext.request.contextPath}/images/favicon.ico" />
                                                    <script type="text/javascript"
                                                        src="<%=request.getContextPath()%>/library/jquery/jquery-3.7.1.min.js"></script>
                                                    <script
                                                        src="<%=request.getContextPath()%>/library/jquery/jquery-compat.js"></script>
                                                </head>

                                                <body>
                                                            <p>&nbsp;</p>
                                                </body>

                                                </html>