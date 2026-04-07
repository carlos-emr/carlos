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
<%
    String roleName$ = (String) session.getAttribute("userrole") + "," + (String) session.getAttribute("user");
    boolean authed = true;
%>
<security:oscarSec roleName="<%=roleName$%>" objectName="_eChart" rights="r" reverse="<%=true%>">
    <%authed = false; %>
    <%response.sendRedirect(request.getContextPath() + "/securityError.jsp?type=_eChart");%>
</security:oscarSec>
<%
    if (!authed) {
        return;
    }
%>


<%@page import="org.apache.commons.lang3.StringUtils" %>
<%@page
        import="io.github.carlos_emr.carlos.encounter.pageUtil.NavBarDisplayDAO, io.github.carlos_emr.carlos.util.*, java.util.ArrayList, java.util.Date, java.util.Calendar, java.io.IOException" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SpringUtils" %>
<%@ page import="io.github.carlos_emr.carlos.daos.security.SecobjprivilegeDao" %>
<%@ page import="io.github.carlos_emr.carlos.model.security.Secobjprivilege" %>
<%@ page import="java.util.List, java.util.regex.Pattern, java.util.regex.Matcher" %>
<%@ page import="org.owasp.encoder.Encode" %>
<%@ page import="io.github.carlos_emr.carlos.services.security.SecurityManager" %>
<%@ page import="io.github.carlos_emr.carlos.util.DateUtils" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<c:set var="ctx" value="${pageContext.request.contextPath}"
       scope="request"/>

<%
    long startTime = System.currentTimeMillis();
    NavBarDisplayDAO dao = (NavBarDisplayDAO) request.getAttribute("DAO");
    int maxColumnHeight = 40;  //break into columns after maxColumnHeight items reached
    int menuWidth = 125;

    // Render auto-complete items with OWASP encoding (defense-in-depth, issue #1386)
    java.util.List<NavBarDisplayDAO.AutoCompleteItem> acItems = dao.getAutoCompleteItems();
    if (!acItems.isEmpty()) {
%>
<script type="text/javascript">
<% for (NavBarDisplayDAO.AutoCompleteItem acItem : acItems) { %>
itemColours['<%= Encode.forJavaScript(acItem.key()) %>'] = '<%= Encode.forJavaScript(acItem.bgColour()) %>';
autoCompList.push('<%= Encode.forJavaScript(acItem.key()) %>');
autoCompleted['<%= Encode.forJavaScript(acItem.key()) %>'] = "<%= Encode.forJavaScript(acItem.jsExpression()) %>";
<% } %>
</script>
<%
    }
%>
<input type=hidden name="reloadUrl" value="<%=Encode.forHtmlAttribute(dao.getReloadUrl())%>"/>
<%
    //Do we have a '+' command to display on the right of the module header?
    String rh = dao.getRightHeadingID();
    String rhid = dao.getRightHeadingID();
    SecurityManager securityMgr = new SecurityManager();

    if (!rh.equals("") && securityMgr.hasWriteAccess("_" + ((String) request.getAttribute("cmd")).toLowerCase(), roleName$)) {
%>
<div class="nav-menu-heading" style="<%=getBackgroundColor(dao)%>">
    <div class="nav-menu-add-button" id='menuTitle<%=rh%>'>
<%      NavBarDisplayDAO.PopupConfig rightCfg = dao.getRightPopup();
        String rightEvent = dao.numPopUpMenuItems() > 0 ? "onmouseover" : "onclick";
        if (rightCfg != null) { %>
        <h3><a href="javascript:void(0);" <%=rightEvent%>="popupPage(<%=rightCfg.width()%>,<%=rightCfg.height()%>,'<%=Encode.forJavaScript(rightCfg.windowName())%>','<%=Encode.forJavaScript(rightCfg.url())%>'); return false;">&#43;</a></h3>
<%      } else { %>
        <h3><a href="javascript:void(0);" <%=rightEvent%>="<%=Encode.forHtmlAttribute(dao.getRightURL())%>">&#43;</a></h3>
<%      } %>
    </div>
    <%
        int num;
        //if there is a pop up menu then grab all of the items and format according to number
        if ((num = dao.numPopUpMenuItems()) > 0) {
            boolean columns = false;
            String style;
            String width;
            if (num > maxColumnHeight) {
                columns = true;
                menuWidth *= 2;
            }
    %>
    <div id='menu<%=rh%>' class='menu' style='width: <%=menuWidth%>px;'
         onclick='event.cancelBubble = true;'>
        <h3 style='text-align: center'><%=Encode.forHtml(dao.getMenuHeader())%>
        </h3>
        <%
            for (int idx = 0; idx < num; ++idx) {
                if (columns) {
                    style = idx % 2 == 0 ? "menuItemleft" : "menuItemright";
                } else {
                    style = "menuItemleft";
                }%>
        <a href="javascript:void(0)" class="<%=style%>"
           onmouseover='this.style.color="black"'
           onmouseout='this.style.color="white"'
<%         NavBarDisplayDAO.PopupConfig popCfg = dao.getPopUpConfig(idx);
           if (popCfg != null) { %>
           onclick="popupPage(<%=popCfg.width()%>,<%=popCfg.height()%>,'<%=Encode.forJavaScript(popCfg.windowName())%>','<%=Encode.forJavaScript(popCfg.url())%>'); return false;"><%=Encode.forHtml(dao.getPopUpText(idx))%>
<%         } else { %>
           onclick="<%=Encode.forHtmlAttribute(dao.getPopUpUrl(idx) + "; return false;")%>"><%=Encode.forHtml(dao.getPopUpText(idx))%>
<%         } %>
        </a>
        <%
            if (columns && idx % 2 == 1) {
        %> <br>
        <%
        } else if (!columns) {
        %> <br>
        <%
                }
            } //end for
        %>
    </div>
    <%
        } //end if menu items

    } //end if there is a right hand header
    else {
        if (!rh.equals("")) {
    %>
    <div id='menuTitle<%=rh%>' style="width:10%;">
        <h3 style="padding:0; <%=getBackgroundColor(dao)%>">&nbsp;</h3>
    </div>
    <%
            }
        }

        //left hand module header comes last as it's displayed as a block
    %>
    <div class="nav-menu-title">
<%      NavBarDisplayDAO.PopupConfig leftCfg = dao.getLeftPopup();
        if (leftCfg != null) { %>
        <h3 onclick="popupPage(<%=leftCfg.width()%>,<%=leftCfg.height()%>,'<%=Encode.forJavaScript(leftCfg.windowName())%>','<%=Encode.forJavaScript(leftCfg.url())%>'); return false;"><a href="javascript:void(0)"><%=Encode.forHtml(dao.getLeftHeading())%>
        </a></h3>
<%      } else { %>
        <h3 onclick="<%=Encode.forHtmlAttribute(dao.getLeftURL() + "; return false;")%>"><a href="javascript:void(0)"><%=Encode.forHtml(dao.getLeftHeading())%>
        </a></h3>
<%      } %>
    </div>
</div>
<ul id="<%=request.getAttribute("navbarName")%>list">
    <%
        //now we display the actual items of the module
        String manageItems = "";
        String div = (String) request.getAttribute("navbarName");
        div = div.trim();
        int numItems = dao.numItems();
        String rawReloadURL = request.getParameter("reloadURL");
        if (rawReloadURL == null) rawReloadURL = "";
        StringBuilder reloadURL = new StringBuilder(rawReloadURL + "&reloadURL=" + rawReloadURL);
        String strToDisplay = request.getParameter("numToDisplay");
        int numToDisplay;
        boolean xpanded = false;
        int displayThreshold = 6;

        if (strToDisplay != null) {
            numToDisplay = Integer.parseInt(strToDisplay);
            reloadURL.append("&numToDisplay=" + strToDisplay);
            if (numItems > numToDisplay) {
                String xpandUrl = rawReloadURL + "&reloadURL=" + rawReloadURL + "&cmd=" + div;
                manageItems = xpandUrl;
            }
        } else {
            numToDisplay = numItems;
            if (numToDisplay > displayThreshold) {
                xpanded = true;
            }
        }
        reloadURL.append("&cmd=" + div);
        int numDisplayed = 0;

        ArrayList<NavBarDisplayDAO.Item> current = new ArrayList<NavBarDisplayDAO.Item>();
        ArrayList<NavBarDisplayDAO.Item> pastDates = new ArrayList<NavBarDisplayDAO.Item>();
        ArrayList<NavBarDisplayDAO.Item> noDates = new ArrayList<NavBarDisplayDAO.Item>();
        Calendar threshold = Calendar.getInstance();
        threshold.add(Calendar.MONTH, -3);
        Date threeMths = threshold.getTime();
        int j;

        for (j = 0; j < numItems; j++) {
            NavBarDisplayDAO.Item item = dao.getItem(j);
            Date d = item.getDate();
            if (d == null)
                noDates.add(item);
            else if (d.compareTo(threeMths) < 0)
                pastDates.add(item);
            else
                current.add(item);
        }

        StringBuilder jscode = new StringBuilder();

        numDisplayed = display(noDates, numToDisplay, numDisplayed, manageItems, xpanded, numItems, jscode, displayThreshold, reloadURL.toString(), dao.getDivId(), request, out);

        if (numDisplayed < numToDisplay) {
            numDisplayed += display(current, numToDisplay, numDisplayed, manageItems, xpanded, numItems, jscode, displayThreshold, reloadURL.toString(), dao.getDivId(), request, out);
        }

        if (numDisplayed < numToDisplay) {
            numDisplayed += display(pastDates, numToDisplay, numDisplayed, manageItems, xpanded, numItems, jscode, displayThreshold, reloadURL.toString(), dao.getDivId(), request, out);
        }

        if (numDisplayed == 0) {
            out.println("<li>&nbsp;</li>");
        }
    %>
</ul>
<input type="hidden" id="<%=request.getAttribute("navbarName")%>num"
       value="<%=numDisplayed%>"/>
<%
    out.println("<script type=\"text/javascript\">" + jscode.toString() + "</script>");
%>

<%!
    public String getBackgroundColor(NavBarDisplayDAO dao) {
        if (dao.hasHeadingColour()) {
            return "background-color: #" + dao.getHeadingColour() + ";";
        }
        return "";
    }

    public int display(ArrayList<NavBarDisplayDAO.Item> items, int numToDisplay, int numDisplayed, String reloadUrl, boolean xpanded, int numItems, StringBuilder js, int displayThreshold, String divReloadUrl, String cmd, jakarta.servlet.http.HttpServletRequest request, jakarta.servlet.jsp.JspWriter out) throws IOException {
        String stripe, colour, bgColour;
        String imgName;
        String dateFormat = "dd-MMM-yyyy";
        Pattern pattern = Pattern.compile("'([^']*)'");


        String divReloadInfo;
        numToDisplay -= numDisplayed;

        int total = items.size() < numToDisplay ? items.size() : numToDisplay;
        int j;
        int curNum = numDisplayed;
        for (j = 0; j < total; ++j) {
            NavBarDisplayDAO.Item item = items.get(j);
            colour = item.getColour().equals("") ? "" : "color: " + item.getColour() + ";";
            bgColour = item.getBgColour().equals("") ? "background-color: #f3f3f3;" : "background-color: " + item.getBgColour() + ";";
            String dateColour = "background-color: white;";
            if ((j % 2) == 0) {
                stripe = "style=\"overflow: hidden; clear:both; position:relative; display:block; white-space:nowrap; " + bgColour + "\"";
                dateColour = bgColour;
            } else {
                stripe = "style=\"overflow: hidden; clear:both; position:relative; display:block; white-space:nowrap; \"";
            }
            out.println("<li " + stripe + ">");

            if (curNum == 0 && xpanded) {
                imgName = "img" + request.getAttribute("navbarName") + curNum;
                out.println("<a href='#' onclick=\"return false;\" style='text-decoration:none; width:7px; z-index: 100; " + dateColour + " position:relative; margin: 0px; padding-bottom: 0px;  vertical-align: bottom; display: inline; float: right; clear:both;'><img id='" + imgName + "' src='" + request.getContextPath() + "/messenger/img/collapse.gif'/>&nbsp;&nbsp;</a>");
                js.append("imgfunc['" + imgName + "'] = clickListDisplay.bindAsEventListener(obj,'" + request.getAttribute("navbarName") + "', '" + displayThreshold + "');");
                js.append("Element.observe($('" + imgName + "'), 'click', imgfunc['" + imgName + "']);");
            } else if (j == (numToDisplay - 1) && xpanded) {
                imgName = "img" + request.getAttribute("navbarName") + curNum;
                out.println("<a href='#' onclick=\"return false;\" style='text-decoration:none; width:7px; z-index: 100; " + dateColour + " position:relative; margin: 0px; padding-bottom: 0px;  vertical-align: bottom; display: inline; float: right; clear:both;'><img id='" + imgName + "' src='" + request.getContextPath() + "/messenger/img/collapse.gif'/>&nbsp;&nbsp;</a>");
                js.append("imgfunc['" + imgName + "'] = clickListDisplay.bindAsEventListener(obj,'" + request.getAttribute("navbarName") + "', '" + displayThreshold + "');");
                js.append("Element.observe($('" + imgName + "'), 'click', imgfunc['" + imgName + "']);");
            } else if (j == (numToDisplay - 1) && numItems > (curNum + 1)) {
                imgName = "img" + request.getAttribute("navbarName") + curNum;
                out.println("<a href='#' onclick=\"return false;\" title='" + String.valueOf(numItems - j - 1) + " more items' style=' text-decoration:none; width:7px; z-index: 100; " + dateColour + " position:relative; margin: 0px; padding-bottom: 0px;  vertical-align: bottom; display: inline; float: right; clear:both;'><img id='" + imgName + "' src='" + request.getContextPath() + "/encounter/graphics/expand.gif'/>&nbsp;&nbsp;</a>");
                js.append("imgfunc['" + imgName + "'] = clickLoadDiv.bindAsEventListener(obj,'" + Encode.forJavaScript(String.valueOf(request.getAttribute("navbarName"))) + "','" + Encode.forJavaScript(reloadUrl) + "');");
                js.append("Element.observe($('" + imgName + "'), 'click', imgfunc['" + imgName + "']);");
            } else {
                out.println("<a border=0 style='text-decoration:none; width:7px; z-index: 100; " + dateColour + " position:relative; margin: 0px; padding-bottom: 0px;  vertical-align: bottom; display: inline; float: right; clear:both;'><img  id='img" + request.getAttribute("navbarName") + curNum + "' src='" + request.getContextPath() + "/images/clear.gif'/>&nbsp;&nbsp;</a>");
            }
            ++curNum;

            out.println("<span style=\" z-index: 1; position:absolute; margin-right:10px; width:90%; overflow:hidden;  height:1.2em; white-space:nowrap; float:left; text-align:left; \">");
            String url = item.getURL();
            //This should be done in the display classes but I'll keep it here for future reference
            //url = StringUtils.replaceEach(url, new String[] {"'","\\\""}, new String[] {"\'","\\\""});
            if (item.isURLJavaScript()) {
                divReloadInfo = trackWindowString(url, divReloadUrl, cmd, pattern);
                out.println("<a class='links' style='" + colour + "' onmouseover=\"this.className='linkhover'\" onmouseout=\"this.className='links'\" href='#' onclick=\"" + divReloadInfo + url + "\" title='" + Encode.forHtmlAttribute(item.getLinkTitle()) + "'>");
            } else {
                out.println("<a class='links' style='" + colour + "' onmouseover=\"this.className='linkhover'\" onmouseout=\"this.className='links'\" href=\"" + url + "\" title='" + Encode.forHtmlAttribute(item.getLinkTitle()) + "' target=\"_blank\">");
            }
            out.println(item.getTitle());
            out.println("</a>");
            out.println("</span>");

            if (item.getDate() != null) {
                out.println("<span style=\"z-index: 100; " + dateColour + " overflow:hidden;   position:relative; height:1.2em; white-space:nowrap; float:right; text-align:right;\">");

                if (item.isURLJavaScript()) {
                    divReloadInfo = trackWindowString(url, divReloadUrl, cmd, pattern);
                    out.println("...<a class='links' style='" + colour + "' onmouseover=\"this.className='linkhover'\" onmouseout=\"this.className='links'\" href='#' onclick=\"" + divReloadInfo + url + "\" title='" + Encode.forHtmlAttribute(item.getLinkTitle()) + "'>");
                } else {
                    out.println("...<a class='links' style='" + colour + "' onmouseover=\"this.className='linkhover'\" onmouseout=\"this.className='links'\" href=\"" + url + "\" title='" + Encode.forHtmlAttribute(item.getLinkTitle()) + "' target=\"_blank\">");
                }

                if (item.getValue() != null && !item.getValue().trim().equals("")) {
                    out.println(item.getValue());
                }
                out.println(DateUtils.getDate(item.getDate(), dateFormat, request.getLocale()));
                out.println("</a>");
                out.println("</span>");
            }
            out.println("</li>");
        }

        return j;
    }

    public String trackWindowString(String url, String reloadUrl, String cmd, Pattern pattern) {
        String windowName, divReloadInfo = "";
        if (url.startsWith("popupPage")) {
            Matcher matcher = pattern.matcher(url);
            if (matcher.find()) {
                windowName = matcher.group(1);
                reloadUrl += "&numToDisplay=6&cmd=" + cmd;
                divReloadInfo = "reloadWindows['" + windowName + "'] = '" + Encode.forJavaScript(reloadUrl) + "';reloadWindows['" + windowName + "div'] = '" + Encode.forJavaScript(cmd) + "';";
            }

        }

        return divReloadInfo;
    }

%>
