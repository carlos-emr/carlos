import re

with open('src/main/java/io/github/carlos_emr/OBChecklistHandler_99_12.java', 'r') as f:
    content = f.read()

# Replace complex startElement method
new_start_element = """    public void startElement(String namespaceURI, String localName, String rawName, Attributes atts) throws SAXException {
        String tag = localName.toLowerCase();
        if (tag.equals("recommendations")) {
            results.append("<center><table border=0 cellspacing=1 cellpadding=1 width=\\"100%\\">\\\\n\\\\n");
        } else if (tag.equals("week")) {
            for (int i = 0; i < atts.getLength(); i++) {
                if (atts.getLocalName(i).equals("number")) {
                    count = Integer.parseInt(atts.getValue(i));
                }
            }
            cal.set(now.get(Calendar.YEAR), now.get(Calendar.MONTH), now.get(Calendar.DAY_OF_MONTH));
            cal.add(Calendar.DATE, count * 7);
            results.append("<table border=0 cellspacing=1 cellpadding=1 width=\\"100%\\" datasrc='#xml_list'>\\\\n");
            results.append("<tr bgcolor='#CCFFCC'><td width='5%'></td><td width='5%'></td><td colspan='1'><span CLASS='.title'>\\\\n");
            if (count == 0) {
                results.append("Initial Assessment - Week ").append(count);
            } else {
                results.append(monthNames[cal.get(Calendar.MONTH)]).append(" ").append(cal.get(Calendar.DAY_OF_MONTH)).append(", ").append(cal.get(Calendar.YEAR)).append(" - Week ").append(count);
            }
            results.append("</span></td></tr>");
        } else if (tag.equals("item")) {
            checkbox = false;
            count = 3;
            String clname = "";
            String riskname = "";
            for (int i = 0; i < atts.getLength(); i++) {
                count = 1;
                if (atts.getLocalName(i).equals("name")) clname = atts.getValue(i);
                if (atts.getLocalName(i).equals("risk")) riskname = atts.getValue(i);
                if (atts.getLocalName(i).equals("checkbox")) checkbox = true;
            }

            if (riskname.equals("") || savedar1params.getProperty(riskname) != null) {
                results.append("<tr>");
                if (checkbox) {
                    results.append("<td width='5%' align='center'><input type='checkbox' name='xml_").append(clname).append("d' value='checked' datafld='xml_").append(clname).append("d'></td><td width='5%' align='center'><input type='checkbox' name='xml_").append(clname).append("na' value='checked' datafld='xml_").append(clname).append("na'></td>\\\\n");
                } else {
                    results.append("<td></td><td></td>");
                }
                results.append("<td colspan='").append(count).append("'>");
                if (savedar1params.getProperty(riskname) != null) {
                    results.append("<b>");
                    disprisk = true;
                } else disprisk = false;
                dispitem = true;
            } else {
                disprisk = false;
                dispitem = false;
            }
        } else if (dispitem) {
            if (tag.equals("b")) {
                results.append("<b>");
            } else if (tag.equals("i")) {
                results.append("<i>");
            } else if (tag.equals("font")) {
                results.append("<font ");
                for (int i = 0; i < atts.getLength(); i++) {
                    results.append(atts.getLocalName(i)).append("='").append(atts.getValue(i)).append("' ");
                }
                results.append(">");
            } else if (tag.equals("a")) {
                results.append("<a href=# onClick=\\"popupPage(400,500,'");
                for (int i = 0; i < atts.getLength(); i++) {
                    results.append(atts.getValue(i)).append("');return false;\\">");
                }
            }
        }
    }"""

content = re.sub(r'    public void startElement\(.*?\).*?^    }', new_start_element, content, flags=re.MULTILINE|re.DOTALL)

with open('src/main/java/io/github/carlos_emr/OBChecklistHandler_99_12.java', 'w') as f:
    f.write(content)
