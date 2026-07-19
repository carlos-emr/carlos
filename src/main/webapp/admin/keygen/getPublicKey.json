<%@page import="io.github.carlos_emr.carlos.web.admin.KeyManagerUIBean"%>
<%@page import="io.github.carlos_emr.carlos.commn.model.PublicKey"%>
<%@page import="com.fasterxml.jackson.databind.ObjectMapper"%>

<%
	PublicKey publicKey=KeyManagerUIBean.getPublicKey(request.getParameter("id"));

	response.setContentType("application/json");
	ObjectMapper mapper = new ObjectMapper();
    out.print(mapper.writeValueAsString(publicKey));
%>