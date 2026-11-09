/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility.xml;

import java.util.Calendar;

/** The published dateTime is default; a named profile retains MOXy's historical date-only birthday. */
public final class BirthdayXmlAdapter extends XmlCompatibilityAdapter<String, Calendar> {
    private final CalendarDateTimeXmlAdapter published = new CalendarDateTimeXmlAdapter();
    private final CalendarXmlAdapter legacy = new CalendarXmlAdapter();

    @Override
    public Calendar unmarshal(String value) {
        return published.unmarshal(value);
    }

    @Override
    public String marshal(Calendar value) {
        return XmlWireProfile.current() == XmlWireProfile.MOXY_DATE_ONLY_BIRTHDAY
                ? legacy.marshal(value) : published.marshal(value);
    }
}
