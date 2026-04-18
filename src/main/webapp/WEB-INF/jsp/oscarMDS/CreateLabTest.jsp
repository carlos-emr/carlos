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
<%@ taglib uri="owasp.encoder.jakarta.advanced" prefix="e" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_lab" rights="w" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError?type=_lab");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@ include file="/taglibs.jsp" %>
<%@page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@page import="io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao" %>
<%@page import="io.github.carlos_emr.carlos.commn.model.DemographicContact" %>
<%
    String id = request.getParameter("id");
%>

<div id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>">
					<input type="hidden" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.id" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.id" value="<carlos:encode value='<%= id %>' context="htmlAttribute"/>"/>

					<fieldset>
                <legend>Test Information</legend>

                <table border="0" class="lab-test-table">
					<tr>
						<td  class="input-group"><label>Date:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate" class="form-control" required><img src="<%=request.getContextPath()%>/images/cal.gif" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.valDate_cal" class="input-group-text" required></td>
						<td><label>Flag:</label>
                 		<select name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.flag" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.flag">
                        	<option value="">None</option>
                            <option value="A">Abnormal</option>
                            <option value="N">Normal</option>
                        </select>
                     </td>
                     <td><label>Status:</label>
 						<select name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.stat" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.stat">
                    		<option value="F">Final</option>
                   			<option value="P">Partial</option>
                        </select>
                    </td>
                 		</tr>

                	<tr>

                		<td><label>Code Type:</label>
                			<select name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeType" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeType">
                            	<option value="ST">ST-short text</option>
                                <option value="FT">FT-formatted text</option>
                            </select>
                        </td>
                  	<td><label>Code:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.code" size="10" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.code"/></td>
                  	<td><label>Name:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.lab_test_name" size="15" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.lab_test_name" required></td>
                 	<td colspan="2"><label>Description:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.test_descr" size="40" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.test_descr"/></td>

                  </tr>


                 <tr>
                 	<td><label>Value:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeVal" size="10" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeVal"/></td>
                 	<td><label>Unit:</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeUnit" size="10" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.codeUnit"/></td>
                 </tr>

                 <tr>
                 	<td><label>refRange (low):</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeLow" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeLow" size="5"/></td>
                 	<td><label>refRange (high):</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeHigh" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeHigh" size="5"/></td>
                 	<td><label>refRange (text):</label><input type="text" name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeText" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.refRangeText" size="15"/></td>
                 </tr>

 			     <tr>
 			     	<td valign="top"><label>Lab Notes:</label><textarea name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.labnotes" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.labnotes" rows="5" cols="30"></textarea></td>
 			     	<td valign="top">
 			     		<label>Blocked Test Result:</label>
 			     		<select name="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.blocked" id="test_<carlos:encode value='<%= id %>' context="htmlAttribute"/>.blocked">
 			     			<option value="">No</option>
 			     			<option value="BLOCKED">Yes</option>
 			     		</select>
 			     	</td>
 			     </tr>

                </table>

                <a href="#" onclick="deleteTest(<carlos:encode value='<%= id %>' context="javaScriptAttribute"/>); return false;" class="btn btn-danger" style="width: 80px; margin-top: 10px;">Delete</a>

		       </fieldset>
		       <script>
			       Calendar.setup({ inputField : "test_<carlos:encode value='<%= id %>' context="javaScript"/>.valDate", ifFormat : "%Y-%m-%d %H:%m", showsTime :true, button : "test_<carlos:encode value='<%= id %>' context="javaScript"/>.valDate_cal" });

    </script>
</div>