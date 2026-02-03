/**
 * Copyright (c) 2005-2012. Centre for Research on Inner City Health, St. Michael's Hospital, Toronto. All Rights Reserved.
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
 * This software was written for
 * Centre for Research on Inner City Health, St. Michael's Hospital,
 * Toronto, Ontario, Canada
 */

package io.github.carlos_emr.carlos.utility;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Properties;

import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.config.PropertyPlaceholderConfigurer;
import org.w3c.dom.Node;

public class SpringPropertyConfigurer extends PropertyPlaceholderConfigurer {
    private static Logger logger = MiscUtils.getLogger();

    public SpringPropertyConfigurer() {
        HashMap<String, HashMap<String, Object>> configMap = ConfigXmlUtils.getConfig();
        Properties p = new Properties();
        Iterator<String> i$ = configMap.keySet().iterator();
        Iterator<String> inner$;
        HashMap<String, Object> categoryMap;
        String category;
        String key;
        Object value;

        while (i$.hasNext()) {
            category = i$.next();
            categoryMap = configMap.get(category);
            inner$ = categoryMap.keySet().iterator();
            while (inner$.hasNext()) {
                key = inner$.next();
                value = categoryMap.get(key);
                if (value instanceof Node) {
                    String categoryKey = category + '.' + key;
                    String valueString = ((Node) value).getTextContent();
                    p.put(categoryKey, valueString);
                    logger.debug("Setting " + categoryKey + '=' + valueString);
                }
            }
        }

        this.setProperties(p);
    }
}