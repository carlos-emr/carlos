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
package io.github.carlos_emr.carlos.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;

import org.jdom2.Attribute;
import org.jdom2.Document;
import org.jdom2.Element;
import org.jdom2.input.SAXBuilder;
import org.junit.Test;
import io.github.carlos_emr.carlos.utility.MiscUtils;

public class QuickTest {

    @Test
    public void testThis() {
        InputStream is = null;
        try {

            is = this.getClass().getClassLoader().getResourceAsStream("oscar/oscarPrevention/PreventionItems.xml");

            SAXBuilder parser = new SAXBuilder();
            Document doc = parser.build(is);
            Element root = doc.getRootElement();
            List items = root.getChildren("item");
            for (int i = 0; i < items.size(); i++) {
                Element e = (Element) items.get(i);
                List attr = e.getAttributes();
                HashMap<String, String> h = new HashMap<String, String>();
                for (int j = 0; j < attr.size(); j++) {
                    Attribute att = (Attribute) attr.get(j);
                    h.put(att.getName(), att.getValue());
                    //System.out.print(att.getValue() + ",");
                }
                System.out.println(h.get("desc"));

            }

            //for each item, make a hashmap, and add all attributes to it.
            //add hashmap to prevList
            //add hashmap to another map by name attribute

        } catch (Exception e) {
            MiscUtils.getLogger().error("Error", e);
        } finally {
            try {
                if (is != null) is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
