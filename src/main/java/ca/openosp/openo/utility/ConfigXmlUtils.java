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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.lang3.StringUtils;

import org.apache.logging.log4j.Logger;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

public final class ConfigXmlUtils {
    private static Logger logger = MiscUtils.getLogger();
    private static final String DEFAULT_CONFIG_FILE = "/config.xml";
    private static HashMap<String, HashMap<String, Object>> config = getConfigMap();

    public ConfigXmlUtils() {
    }

    private static HashMap<String, HashMap<String, Object>> getConfigMap() {
        try {
            HashMap<String, HashMap<String, Object>> results = new HashMap();
            readFileIntoMap("/config.xml", results);
            String overrideFilenameSystemPropertiesKey = ((Node) getProperty(results, "misc", "override_config_sytem_property_key")).getTextContent();
            if (overrideFilenameSystemPropertiesKey != null) {
                String overrideFilename = System.getProperty(overrideFilenameSystemPropertiesKey);
                if (overrideFilename != null) {
                    readFileIntoMap(overrideFilename, results);
                }
            }

            return results;
        } catch (Exception var3) {
            logger.error("Error initialising ConfigXmlUtils", var3);
            throw new RuntimeException(var3);
        }
    }

    private static void readFileIntoMap(String fileName, HashMap<String, HashMap<String, Object>> map) throws ParserConfigurationException, SAXException, IOException {
        logger.info("Reading config file into map : " + fileName);
        Document doc = XmlUtils.toDocumentFromFile(fileName);
        Node rootNode = doc.getFirstChild();
        NodeList categories = rootNode.getChildNodes();

        for (int i = 0; i < categories.getLength(); ++i) {
            putCatetoryIntoMap(categories.item(i), map);
        }

    }

    private static void putCatetoryIntoMap(Node category, HashMap<String, HashMap<String, Object>> map) {
        String categoryName = StringUtils.trimToNull(category.getNodeName());
        if (categoryName != null) {
            NodeList properties = category.getChildNodes();

            for (int i = 0; i < properties.getLength(); ++i) {
                putPropertyIntoMap(categoryName, properties.item(i), map);
            }

        }
    }

    private static void putPropertyIntoMap(String categoryName, Node property, HashMap<String, HashMap<String, Object>> map) {
        if (property.getNodeType() == 1) {
            String propertyName = StringUtils.trimToNull(property.getNodeName());
            HashMap<String, Object> categoryMap = (HashMap) map.get(categoryName);
            if (categoryMap == null) {
                categoryMap = new HashMap();
                map.put(categoryName, categoryMap);
            }

            String tempString = XmlUtils.getAttributeValue(property, "list_entry");
            boolean isList = Boolean.parseBoolean(tempString);
            tempString = XmlUtils.getAttributeValue(property, "clear_list");
            boolean clearList = Boolean.parseBoolean(tempString);
            if (clearList) {
                categoryMap.remove(propertyName);
            }

            if (isList) {
                ArrayList<Node> list = (ArrayList) categoryMap.get(propertyName);
                if (list == null) {
                    list = new ArrayList();
                    categoryMap.put(propertyName, list);
                }

                list.add(property);
            } else {
                categoryMap.put(propertyName, property);
            }

        }
    }

    private static Object getProperty(HashMap<String, HashMap<String, Object>> map, String category, String property) {
        HashMap<String, Object> categoryMap = (HashMap) map.get(category);
        return categoryMap == null ? null : categoryMap.get(property);
    }

    public static void reloadConfig() {
        config = getConfigMap();
    }

    public static String getPropertyString(String category, String property) {
        Node node = (Node) getProperty(config, category, property);
        return node != null ? StringUtils.trimToNull(node.getTextContent()) : null;
    }

    public static boolean getPropertyBoolean(String category, String property) {
        return Boolean.parseBoolean(getPropertyString(category, property));
    }

    public static int getPropertyInt(String category, String property) {
        return Integer.parseInt(getPropertyString(category, property));
    }

    public static ArrayList<String> getPropertyStringList(String category, String property) {
        ArrayList<Node> nodeList = (ArrayList) getProperty(config, category, property);
        if (nodeList == null) {
            return null;
        } else {
            ArrayList<String> stringList = new ArrayList();
            Iterator i$ = nodeList.iterator();

            while (i$.hasNext()) {
                Node n = (Node) i$.next();
                stringList.add(n.getTextContent());
            }

            return stringList;
        }
    }

    public static Node getPropertyNode(String category, String property) {
        Node node = (Node) getProperty(config, category, property);
        return node;
    }

    public static ArrayList<Node> getPropertyNodeList(String category, String property) {
        ArrayList<Node> nodeList = (ArrayList) getProperty(config, category, property);
        return nodeList;
    }

    public static HashMap<String, HashMap<String, Object>> getConfig() {
        return config;
    }
}

