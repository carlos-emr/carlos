package org.w3._2001.xmlschema;

import jakarta.xml.bind.DatatypeConverter;
import java.util.Calendar;
import jakarta.xml.bind.annotation.adapters.XmlAdapter;

public class Adapter1 extends XmlAdapter<String, Calendar>
{
    public Calendar unmarshal(final String s) {
        return DatatypeConverter.parseDateTime(s);
    }
    
    public String marshal(final Calendar calendar) {
        if (calendar == null) {
            return null;
        }
        return DatatypeConverter.printDateTime(calendar);
    }
}
