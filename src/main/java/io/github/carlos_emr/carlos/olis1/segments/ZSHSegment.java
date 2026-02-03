/**
 * Copyright (c) 2008-2012 Indivica Inc.
 * <p>
 * This software is made available under the terms of the
 * GNU General Public License, Version 2, 1991 (GPLv2).
 * License details are available via "indivica.ca/gplv2"
 * and "gnu.org/licenses/gpl-2.0.html".
 */

package io.github.carlos_emr.carlos.olis1.segments;

import io.github.carlos_emr.carlos.commn.model.Provider;

public class ZSHSegment implements Segment {

    private Provider provider;

    public ZSHSegment(Provider provider) {
        this.provider = provider;
    }

    @Override
    public String getSegmentHL7String() {
        try {
            return "ZSH|" + provider.getProviderNo() + "|" + provider.getLastName() + " " + provider.getFirstName();
        } catch (Exception e) {
            return "ZSH|-1|system";
        }
    }
}
