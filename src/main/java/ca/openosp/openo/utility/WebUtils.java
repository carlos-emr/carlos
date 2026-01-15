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

package ca.openosp.openo.utility;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;

import org.apache.commons.text.StringEscapeUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.Logger;


public final class WebUtils {
    private static Logger logger = MiscUtils.getLogger();
    public static final String ERROR_MESSAGE_SESSION_KEY = WebUtils.class.getName() + ".ERROR_MESSAGE_SESSION_KEY";
    public static final String INFO_MESSAGE_SESSION_KEY = WebUtils.class.getName() + ".INFO_MESSAGE_SESSION_KEY";

    public WebUtils() {
    }

    public static void dumpParameters(HttpServletRequest request) {
        logger.error("--- Dump Request Parameters Start for " + request.getRequestURI() + " Start ---");
        Enumeration e = request.getParameterNames();

        while (e.hasMoreElements()) {
            String key = (String) e.nextElement();
            logger.error(key + '=' + request.getParameter(key));
        }

        logger.error("--- Dump Request Parameters End ---");
    }

    public static void dumpRequest(HttpServletRequest request) throws IOException {
        logger.error("--- Dump Request Start ---");
        InputStream is = request.getInputStream();
        StringBuilder sb = new StringBuilder();
        boolean var3 = false;

        int x;
        while ((x = is.read()) != -1) {
            sb.append((char) x);
        }

        logger.error(sb.toString());
        logger.error("--- Dump Request End ---");
    }

    public static boolean isChecked(HttpServletRequest request, String parameter) {
        String temp = request.getParameter(parameter);
        return temp != null && (temp.equalsIgnoreCase("on") || temp.equalsIgnoreCase("true") || temp.equalsIgnoreCase("checked"));
    }

    public static String renderMessagesAsHtml(HttpSession session, String messageKey, String styleClass, String style, String id, String name) {
        ArrayList<String> messages = popMessages(session, messageKey);
        StringBuilder sb = new StringBuilder();
        if (messages != null && messages.size() > 0) {
            sb.append(getTagString("ul", style, styleClass, id, name, false));
            Iterator i$ = messages.iterator();

            while (i$.hasNext()) {
                String s = (String) i$.next();
                sb.append("<li>");
                sb.append(s);
                sb.append("</il>");
            }

            sb.append("</ul>");
        }

        return sb.toString();
    }

    public static String popErrorAndInfoMessagesAsHtml(HttpSession session) {
        return (popErrorMessagesAsHtml(session) + popInfoMessagesAsHtml(session));
    }

    public static String popErrorMessagesAsHtml(HttpSession session, String styleClass, String style, String id, String name) {
        return renderMessagesAsHtml(session, ERROR_MESSAGE_SESSION_KEY, styleClass, style, id, name);
    }

    public static String popInfoMessagesAsHtml(HttpSession session, String styleClass, String style, String id, String name) {
        return renderMessagesAsHtml(session, INFO_MESSAGE_SESSION_KEY, styleClass, style, id, name);
    }

    public static void addErrorMessage(HttpSession session, String message) {
        addMessage(session, ERROR_MESSAGE_SESSION_KEY, message);
    }

    public static void addLocalisedErrorMessage(HttpServletRequest request, String messageKey) {
        addLocalisedMessage(request, ERROR_MESSAGE_SESSION_KEY, messageKey);
    }

    public static void addLocalisedErrorMessage(HttpServletRequest request, String messageKey, String additionalText) {
        addLocalisedMessage(request, ERROR_MESSAGE_SESSION_KEY, messageKey, additionalText);
    }

    public static void addInfoMessage(HttpSession session, String message) {
        addMessage(session, INFO_MESSAGE_SESSION_KEY, message);
    }

    public static void addLocalisedInfoMessage(HttpServletRequest request, String messageKey) {
        addLocalisedMessage(request, INFO_MESSAGE_SESSION_KEY, messageKey);
    }

    public static void addLocalisedInfoMessage(HttpServletRequest request, String messageKey, String additionalText) {
        addLocalisedMessage(request, INFO_MESSAGE_SESSION_KEY, messageKey, additionalText);
    }

    public static ArrayList<String> popMessages(HttpSession session, String type) {
        synchronized (session) {
            ArrayList<String> errors = (ArrayList) ((ArrayList) session.getAttribute(type));
            session.removeAttribute(type);
            return errors;
        }
    }

    public static void addLocalisedMessage(HttpServletRequest request, String type, String messageKey) {
        addMessage(request.getSession(), type, LocaleUtils.getMessage(request.getLocale(), messageKey));
    }

    public static void addLocalisedMessage(HttpServletRequest request, String type, String messageKey, String additionalText) {
        addMessage(request.getSession(), type, LocaleUtils.getMessage(request.getLocale(), messageKey) + ' ' + additionalText);
    }

    public static void addMessage(HttpSession session, String type, String message) {
        synchronized (session) {
            ArrayList<String> messages = (ArrayList) ((ArrayList) session.getAttribute(type));
            if (messages == null) {
                messages = new ArrayList();
                session.setAttribute(type, messages);
            }

            messages.add(message);
        }
    }

    public static String buildQueryString(Map<String, Object> map) {
        StringBuilder sb = new StringBuilder();
        Iterator i$ = map.entrySet().iterator();

        while (i$.hasNext()) {
            Entry<String, Object> entry = (Entry) i$.next();
            if (entry.getValue() != null) {
                if (sb.length() == 0) {
                    sb.append('?');
                } else {
                    sb.append('&');
                }

                sb.append((String) entry.getKey());
                sb.append('=');
                sb.append(entry.getValue());
            }
        }

        return sb.toString();
    }

    public static String getCheckedString(boolean b) {
        return b ? "checked=\"checked\"" : "";
    }

    public static String getSelectedString(boolean b) {
        return b ? "selected=\"selected\"" : "";
    }

    public static String getTagString(String tagName, String cssStyle, String styleClass, String id, String name, boolean closeTag) {
        StringBuilder sb = new StringBuilder();
        sb.append('<');
        sb.append(tagName);
        if (id != null) {
            sb.append(" id=\"");
            sb.append(id);
            sb.append('"');
        }

        if (name != null) {
            sb.append(" name=\"");
            sb.append(name);
            sb.append('"');
        }

        if (styleClass != null) {
            sb.append(" class=\"");
            sb.append(styleClass);
            sb.append('"');
        }

        if (cssStyle != null) {
            sb.append(" style=\"");
            sb.append(cssStyle);
            sb.append('"');
        }

        if (closeTag) {
            sb.append('/');
        }

        sb.append('>');
        return sb.toString();
    }

    public static String trimToEmptyEscapeHtml(String s) {
        s = StringUtils.trimToEmpty(s);
        s = StringEscapeUtils.escapeHtml4(s);
        return s;
    }

    public static String getDisabledString(boolean enabled) {
        return !enabled ? "disabled=\"disabled\"" : "";
    }

    public static String limitStringLength(String s, int length) {
        if (s == null) {
            return null;
        } else {
            return s.length() <= length ? s : s.substring(0, length - 3) + "...";
        }
    }

    public static String popErrorMessagesAsHtml(HttpSession session) {
        ArrayList<String> al = popErrorMessages(session);

        StringBuilder sb = new StringBuilder();

        if (al != null && al.size() > 0) {
            sb.append("<ul style=\"color:red\">");

            for (String s : al) {
                sb.append("<li>");
                sb.append(s);
                sb.append("</il>");
            }

            sb.append("</ul>");
        }

        return (sb.toString());
    }

    public static String popInfoMessagesAsHtml(HttpSession session) {
        ArrayList<String> al = popInfoMessages(session);

        StringBuilder sb = new StringBuilder();

        if (al != null && al.size() > 0) {
            sb.append("<ul style=\"color:#009900\">");

            for (String s : al) {
                sb.append("<li>");
                sb.append(s);
                sb.append("</il>");
            }

            sb.append("</ul>");
        }

        return (sb.toString());
    }

    public static String popErrorMessagesAsAlert(HttpSession session) {
        ArrayList<String> al = popErrorMessages(session);

        StringBuilder sb = new StringBuilder();

        if (al != null && al.size() > 0) {
            sb.append("<script type=\"text/javascript\">");
            sb.append("alert('");

            for (String s : al) sb.append(StringEscapeUtils.escapeEcmaScript(s));

            sb.append("');");
            sb.append("</script>");
        }

        return (sb.toString());
    }

    /**
     * @return an arrayList of error message or null if no messages, the messages are then removed from the session upon return from this call.
     */
    public static ArrayList<String> popErrorMessages(HttpSession session) {
        return (WebUtils.popMessages(session, WebUtils.ERROR_MESSAGE_SESSION_KEY));
    }

    /**
     * @return an arrayList of error message or null if no messages, the messages are then removed from the session upon return from this call.
     */
    public static ArrayList<String> popInfoMessages(HttpSession session) {
        return (WebUtils.popMessages(session, WebUtils.INFO_MESSAGE_SESSION_KEY));
    }

}
