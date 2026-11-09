<%-- Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later --%>
<%@ page pageEncoding="UTF-8" contentType="text/html; charset=UTF-8" %>
<%@ page import="io.github.carlos_emr.carlos.managers.RxFavoriteGroupService" %>
<%@ page import="io.github.carlos_emr.carlos.utility.SafeEncode" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<%@ taglib uri="jakarta.tags.fmt" prefix="fmt" %>
<fmt:setBundle basename="oscarResources"/>
<fmt:message key="oscarRx.groups.confirmDelete" var="msg_confirmGroupDelete"/>
<%
    RxFavoriteGroupService.View view = (RxFavoriteGroupService.View) request.getAttribute("favoriteGroupView");
    int patient = (Integer) request.getAttribute("favoriteGroupPatient");
    String action = request.getContextPath() + "/rx/favoriteGroups";
%>
<!DOCTYPE html>
<html lang="<%= SafeEncode.forHtmlAttribute(request.getLocale().getLanguage()) %>">
<head><meta charset="UTF-8"><title><fmt:message key="oscarRx.groups.title"/></title>
<link rel="stylesheet" href="<%= request.getContextPath() %>/rx/styles.css">
<style>main {max-width:70rem;margin:1rem auto} form {margin:.5rem 0} fieldset {margin:1rem 0} label {margin-right:1rem} details {margin:1rem 0}</style>
</head><body><main>
<h1><fmt:message key="oscarRx.groups.title"/></h1>
<a href="<%= request.getContextPath() %>/rx/choosePatient?demographicNo=<%= patient %>"><fmt:message key="oscarRx.groups.back"/></a>
<% if (request.getAttribute("favoriteGroupError") != null) { %>
<p role="alert"><fmt:message key="${requestScope.favoriteGroupError}"/></p>
<% } %>
<p><fmt:message key="oscarRx.groups.description"/></p>
<form action="<%= action %>" method="post">
<input type="hidden" name="carlosFormSubmission" value="<carlos:encode value="${requestScope.favoriteGroupSubmission}" context="htmlAttribute"/>">
<input type="hidden" name="demographicNo" value="<%= patient %>"><input type="hidden" name="command" value="create">
<label><fmt:message key="oscarRx.groups.name"/> <input name="name" required maxlength="80"></label>
<button type="submit"><fmt:message key="oscarRx.groups.create"/></button>
</form>
<% for (RxFavoriteGroupService.Group group : view.groups()) { %>
<details open><summary><carlos:encode value='<%= group.name() %>' context="html"/></summary>
<form action="<%= action %>" method="post">
<input type="hidden" name="carlosFormSubmission" value="<carlos:encode value="${requestScope.favoriteGroupSubmission}" context="htmlAttribute"/>">
<input type="hidden" name="demographicNo" value="<%= patient %>"><input type="hidden" name="groupId" value="<%= group.id() %>">
<label><fmt:message key="oscarRx.groups.name"/> <input name="name" required maxlength="80" value="<carlos:encode value='<%= group.name() %>' context="htmlAttribute"/>"></label>
<button name="command" value="rename"><fmt:message key="oscarRx.groups.rename"/></button>
<button name="command" value="up" formnovalidate><fmt:message key="oscarRx.groups.up"/></button>
<button name="command" value="down" formnovalidate><fmt:message key="oscarRx.groups.down"/></button>
<button name="command" value="delete" formnovalidate onclick="return confirm('${carlos:forJavaScript(msg_confirmGroupDelete)}')"><fmt:message key="oscarRx.groups.delete"/></button>
</form>
<ul>
<% for (RxFavoriteGroupService.Favorite favorite : view.favorites()) { if (favorite.groupIds().contains(group.id())) { %>
<li><a href="<%= request.getContextPath() %>/rx/choosePatient?demographicNo=<%= patient %>&amp;usefav=true&amp;favid=<%= favorite.id() %>"><carlos:encode value='<%= favorite.name() %>' context="html"/></a></li>
<% } } %>
</ul></details>
<% } %>
<details open><summary><fmt:message key="oscarRx.groups.ungrouped"/></summary><ul>
<% for (RxFavoriteGroupService.Favorite favorite : view.favorites()) { if (favorite.groupIds().isEmpty()) { %>
<li><a href="<%= request.getContextPath() %>/rx/choosePatient?demographicNo=<%= patient %>&amp;usefav=true&amp;favid=<%= favorite.id() %>"><carlos:encode value='<%= favorite.name() %>' context="html"/></a></li>
<% } } %>
</ul></details>
<h2><fmt:message key="oscarRx.groups.assign"/></h2>
<% for (RxFavoriteGroupService.Favorite favorite : view.favorites()) { %>
<form action="<%= action %>" method="post"><fieldset>
<legend><carlos:encode value='<%= favorite.name() %>' context="html"/></legend>
<input type="hidden" name="carlosFormSubmission" value="<carlos:encode value="${requestScope.favoriteGroupSubmission}" context="htmlAttribute"/>">
<input type="hidden" name="demographicNo" value="<%= patient %>"><input type="hidden" name="command" value="assign">
<input type="hidden" name="favoriteId" value="<%= favorite.id() %>">
<% for (RxFavoriteGroupService.Group group : view.groups()) { %>
<input type="checkbox" id="favorite-<%= favorite.id() %>-group-<%= group.id() %>" name="groupIds" value="<%= group.id() %>" <%= favorite.groupIds().contains(group.id()) ? "checked" : "" %>><label for="favorite-<%= favorite.id() %>-group-<%= group.id() %>"><%= io.github.carlos_emr.carlos.utility.SafeEncode.forHtmlContent(group.name()) %></label>
<% } %>
<button type="submit"><fmt:message key="oscarRx.groups.save"/></button>
</fieldset></form>
<% } %>
</main></body></html>
