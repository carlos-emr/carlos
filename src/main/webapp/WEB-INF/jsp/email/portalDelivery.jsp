<%@ page contentType="text/html;charset=UTF-8" %>
<%@ taglib uri="jakarta.tags.core" prefix="c" %>
<%@ taglib uri="carlos" prefix="carlos" %>
<!DOCTYPE html>
<html lang="${carlos:forHtmlAttribute(pageContext.request.locale.language)}"><head><title>Email password delivery</title></head><body>
<h1>Email password delivery</h1>
<c:choose>
    <c:when test="${portalRecoveryError}">
        <p>Recovery could not be completed. Reload this page to check the current state before trying again. No email was resent.</p>
    </c:when>
    <c:otherwise>
        <p>Password reference: Email <carlos:encode value="${emailLog.id}"/></p>
        <c:choose>
            <c:when test="${emailLog.portalDeliveryState eq 'PUBLISHED'}"><p>The email was accepted by the mail provider and its password is available in the patient's Portal.</p></c:when>
            <c:when test="${emailLog.portalDeliveryState eq 'REVOKED'}"><p>The unsent email's password has been revoked. A new email can now be composed if needed.</p></c:when>
            <c:when test="${emailLog.portalDeliveryState eq 'SENDING'}">
                <p>Delivery is uncertain. Recovery is available after 15 minutes. Check the mail provider's record for this email before continuing. Do not send it again while its outcome is unknown.</p>
                <form method="post" action="${pageContext.request.contextPath}/email/portalDelivery">
                    <input type="hidden" name="emailLogId" value="${carlos:forHtmlAttribute(emailLog.id)}"/>
                    <label><input type="checkbox" name="confirmed" value="true" required/> I checked the mail provider's record and confirmed the outcome.</label>
                    <button name="operation" value="confirmSent">Provider accepted email: publish password</button>
                    <button name="operation" value="confirmNotSent">Provider did not accept email: revoke password</button>
                </form>
            </c:when>
            <c:otherwise>
                <p><carlos:encode value="${emailLog.errorMessage}"/></p>
                <p>This operation retries password publication for a sent email, or revokes the password for an unsent email. It never sends email.</p>
                <form method="post" action="${pageContext.request.contextPath}/email/portalDelivery">
                    <input type="hidden" name="emailLogId" value="${carlos:forHtmlAttribute(emailLog.id)}"/>
                    <button name="operation" value="retry">Retry password update</button>
                </form>
            </c:otherwise>
        </c:choose>
        <c:if test="${emailLog.status eq 'PENDING' and (emailLog.portalDeliveryState eq 'PUBLISHED' or emailLog.portalDeliveryState eq 'REVOKED')}">
            <p>The delivery record still needs to be updated. This will not send another email.</p>
            <form method="post" action="${pageContext.request.contextPath}/email/portalDelivery">
                <input type="hidden" name="emailLogId" value="${carlos:forHtmlAttribute(emailLog.id)}"/>
                <button name="operation" value="retry">Update delivery record</button>
            </form>
        </c:if>
    </c:otherwise>
</c:choose>
</body></html>
