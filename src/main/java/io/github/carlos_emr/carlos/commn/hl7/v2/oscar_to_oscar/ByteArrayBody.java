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
 
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */


package io.github.carlos_emr.carlos.commn.hl7.v2.oscar_to_oscar;

import java.io.IOException;
import java.io.OutputStream;

import org.apache.hc.client5.http.entity.mime.AbstractContentBody;
import org.apache.hc.core5.http.ContentType;

public class ByteArrayBody extends AbstractContentBody {

    private byte[] byteArray;
    private String fileName;

    public ByteArrayBody(byte[] byteArray, String fileName) {
        super(ContentType.APPLICATION_OCTET_STREAM);
        this.byteArray = byteArray;
        this.fileName = fileName;
    }

    @Override
    public void writeTo(OutputStream outputStream) throws IOException {
        outputStream.write(byteArray);
        outputStream.flush();
    }

    @Override
    public String getFilename() {
        return (fileName);
    }

    @Override
    public long getContentLength() {
        return (byteArray.length);
    }

}
