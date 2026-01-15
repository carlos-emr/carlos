package ca.openosp.openo.caisi_integrator.util;

import java.util.Iterator;
import java.util.ArrayList;
import org.apache.commons.lang3.StringUtils;
import java.io.IOException;
import org.xml.sax.SAXException;
import javax.xml.parsers.ParserConfigurationException;
import org.w3c.dom.NodeList;
import org.w3c.dom.Document;
import java.io.InputStream;
import java.io.FileInputStream;
import org.w3c.dom.Node;
import java.util.HashMap;
import org.apache.log4j.Logger;

public final class ConfigXmlUtils
{
    private static final Logger logger;
    private static final String DEFAULT_CONFIG_FILE = "/config.xml";
    private static HashMap<String, HashMap<String, Object>> config;
    
    private static HashMap<String, HashMap<String, Object>> getConfigMap() {
        try {
            final HashMap<String, HashMap<String, Object>> results = new HashMap<String, HashMap<String, Object>>();
            readFileIntoMap("/config.xml", results);
            final String overrideFilenameSystemPropertiesKey = ((Node)getProperty(results, "misc", "override_config_sytem_property_key")).getTextContent();
            if (overrideFilenameSystemPropertiesKey != null && !overrideFilenameSystemPropertiesKey.isEmpty()) {
                final String overrideFilename = System.getProperty(overrideFilenameSystemPropertiesKey);
                if (overrideFilename != null) {
                    readFileIntoMap(overrideFilename, results);
                }
            }
            return results;
        }
        catch (final Exception e) {
            ConfigXmlUtils.logger.error((Object)"Error initialising ConfigXmlUtils", (Throwable)e);
            throw new RuntimeException(e);
        }
    }
    
    private static void readFileIntoMap(final String fileName, final HashMap<String, HashMap<String, Object>> map) throws ParserConfigurationException, SAXException, IOException {
        ConfigXmlUtils.logger.info((Object)("Reading config file into map : " + fileName));
        InputStream is = ConfigXmlUtils.class.getResourceAsStream(fileName);
        if (is == null) {
            is = new FileInputStream(fileName);
        }
        try {
            final Document doc = XmlUtils.toDocument(is);
            final Node rootNode = doc.getFirstChild();
            final NodeList categories = rootNode.getChildNodes();
            for (int i = 0; i < categories.getLength(); ++i) {
                putCatetoryIntoMap(categories.item(i), map);
            }
        }
        finally {
            is.close();
        }
    }
    
    private static void putCatetoryIntoMap(final Node category, final HashMap<String, HashMap<String, Object>> map) {
        final String categoryName = StringUtils.trimToNull(category.getNodeName());
        if (categoryName == null) {
            return;
        }
        final NodeList properties = category.getChildNodes();
        for (int i = 0; i < properties.getLength(); ++i) {
            putPropertyIntoMap(categoryName, properties.item(i), map);
        }
    }
    
    private static void putPropertyIntoMap(final String categoryName, final Node property, final HashMap<String, HashMap<String, Object>> map) {
        if (property.getNodeType() != 1) {
            return;
        }
        final String propertyName = StringUtils.trimToNull(property.getNodeName());
        HashMap<String, Object> categoryMap = map.get(categoryName);
        if (categoryMap == null) {
            categoryMap = new HashMap<String, Object>();
            map.put(categoryName, categoryMap);
        }
        String tempString = XmlUtils.getAttributeValue(property, "list_entry");
        final boolean isList = Boolean.parseBoolean(tempString);
        tempString = XmlUtils.getAttributeValue(property, "clear_list");
        final boolean clearList = Boolean.parseBoolean(tempString);
        if (clearList) {
            categoryMap.remove(propertyName);
        }
        if (isList) {
            ArrayList<Node> list = (ArrayList<Node>)categoryMap.get(propertyName);
            if (list == null) {
                list = new ArrayList<Node>();
                categoryMap.put(propertyName, list);
            }
            list.add(property);
        }
        else {
            categoryMap.put(propertyName, property);
        }
    }
    
    private static Object getProperty(final HashMap<String, HashMap<String, Object>> map, final String category, final String property) {
        final HashMap<String, Object> categoryMap = map.get(category);
        if (categoryMap == null) {
            return null;
        }
        return categoryMap.get(property);
    }
    
    public static void reloadConfig() {
        ConfigXmlUtils.config = getConfigMap();
    }
    
    public static String getPropertyString(final String category, final String property) {
        final Node node = (Node)getProperty(ConfigXmlUtils.config, category, property);
        if (node != null) {
            return StringUtils.trimToNull(node.getTextContent());
        }
        return null;
    }
    
    public static ArrayList<String> getPropertyStringList(final String category, final String property) {
        final ArrayList<Node> nodeList = (ArrayList<Node>)getProperty(ConfigXmlUtils.config, category, property);
        if (nodeList != null) {
            final ArrayList<String> stringList = new ArrayList<String>();
            for (final Node n : nodeList) {
                stringList.add(n.getTextContent());
            }
            return stringList;
        }
        return null;
    }
    
    public static Node getPropertyNode(final String category, final String property) {
        return (Node)getProperty(ConfigXmlUtils.config, category, property);
    }
    
    public static ArrayList<Node> getPropertyNodeList(final String category, final String property) {
        return (ArrayList)getProperty(ConfigXmlUtils.config, category, property);
    }
    
    public static HashMap<String, HashMap<String, Object>> getConfig() {
        return ConfigXmlUtils.config;
    }
    
    static {
        logger = MiscUtils.getLogger();
        ConfigXmlUtils.config = getConfigMap();
    }
}
