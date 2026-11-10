/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.support;

import java.io.FilterInputStream;
import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/** Strict streaming XML base64 lexical parsing for explicitly declared binary report TextContent. */
public final class CdsBase64ValueParser {
    private CdsBase64ValueParser() { }

    public static InputStream decode(InputStream source) {
        Lexical lexical=new Lexical(source);
        InputStream decoded=Base64.getDecoder().wrap(lexical);
        return new FilterInputStream(decoded) {
            private boolean finished;
            private void finish() throws IOException {
                if (!finished) { finished=true; while (lexical.read()!=-1) { /* Validate trailing input after decoder padding. */ } }
            }
            @Override public int read() throws IOException {
                int value=super.read(); if (value<0)finish(); return value;
            }
            @Override public int read(byte[] bytes, int offset, int length) throws IOException {
                int count=in.read(bytes, offset, length); if (count<0)finish(); return count;
            }
        };
    }

    private static final class Lexical extends FilterInputStream {
        private int position;
        private int previous;
        private boolean secondPaddingRequired;
        private boolean ended;
        private Lexical(InputStream source) { super(new BufferedInputStream(source, 65536)); }
        @Override public int read() throws IOException {
            int value;
            do { value=in.read(); } while (value==' ' || value=='\r' || value=='\n' || value=='\t');
            if (value<0) {
                if (position!=0 || secondPaddingRequired)throw invalid();
                return -1;
            }
            if (ended)throw invalid();
            if (value=='=') {
                if (position==2 && !secondPaddingRequired) {
                    if ((previous&15)!=0)throw invalid();
                    secondPaddingRequired=true; position=3;
                } else if (position==3) {
                    if (!secondPaddingRequired && (previous&3)!=0)throw invalid();
                    secondPaddingRequired=false; position=0; ended=true;
                } else throw invalid();
            } else {
                if (secondPaddingRequired)throw invalid();
                int sextet=value>='A'&&value<='Z'?value-'A':value>='a'&&value<='z'?value-'a'+26
                        :value>='0'&&value<='9'?value-'0'+52:value=='+'?62:value=='/'?63:-1;
                if (sextet<0)throw invalid();
                previous=sextet; position=(position+1)%4;
            }
            return value;
        }
        @Override public int read(byte[] bytes, int offset, int length) throws IOException {
            if (length==0)return 0;
            int count=0;
            while (count<length) {
                int value=read(); if (value<0)return count==0?-1:count;
                bytes[offset+count++]=(byte) value;
            }
            return count;
        }
        private static IOException invalid() { return new CdsInputException("CDS_BINARY_REPORT_TEXT_ENCODING_INVALID", "Content/TextContent", ""); }
    }
}
