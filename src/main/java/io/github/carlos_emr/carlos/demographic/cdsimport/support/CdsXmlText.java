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

import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.io.Reader;
import java.nio.charset.Charset;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Bounded XML transport decoding, including markup limits before SAX can retain an attribute. */
public final class CdsXmlText {
    private static final int HEADER_LIMIT = 65536;
    private static final Pattern ENCODING = Pattern.compile("encoding\\s*=\\s*(['\"])([A-Za-z][A-Za-z0-9._-]*)\\1");
    private CdsXmlText() { }

    public static Reader reader(InputStream input, int maxMarkupCharacters) throws IOException {
        PushbackInputStream source = new PushbackInputStream(input, HEADER_LIMIT);
        byte[] header = source.readNBytes(1024);
        int skip = 0;
        Charset inferred = StandardCharsets.UTF_8;
        if (header.length >= 4 && header[0] == 0 && header[1] == 0 && header[2] == (byte) 0xfe && header[3] == (byte) 0xff) {
            inferred = Charset.forName("UTF-32BE"); skip = 4;
        } else if (header.length >= 4 && header[0] == (byte) 0xff && header[1] == (byte) 0xfe && header[2] == 0 && header[3] == 0) {
            inferred = Charset.forName("UTF-32LE"); skip = 4;
        } else if (header.length >= 3 && header[0] == (byte) 0xef && header[1] == (byte) 0xbb && header[2] == (byte) 0xbf) {
            skip = 3;
        } else if (header.length >= 2 && header[0] == (byte) 0xfe && header[1] == (byte) 0xff) {
            inferred = StandardCharsets.UTF_16BE; skip = 2;
        } else if (header.length >= 2 && header[0] == (byte) 0xff && header[1] == (byte) 0xfe) {
            inferred = StandardCharsets.UTF_16LE; skip = 2;
        } else if (header.length >= 4 && header[0] == 0 && header[1] == 0 && header[2] == 0 && header[3] == '<') {
            inferred = Charset.forName("UTF-32BE");
        } else if (header.length >= 4 && header[0] == '<' && header[1] == 0 && header[2] == 0 && header[3] == 0) {
            inferred = Charset.forName("UTF-32LE");
        } else if (header.length >= 4 && header[0] == 0 && header[1] == '<' && header[2] == 0) {
            inferred = StandardCharsets.UTF_16BE;
        } else if (header.length >= 4 && header[0] == '<' && header[1] == 0 && header[3] == 0) {
            inferred = StandardCharsets.UTF_16LE;
        }
        String declaration = new String(header, skip, header.length - skip, inferred);
        if (declaration.startsWith("<?xml") && !declaration.contains("?>")) {
            byte[] rest = source.readNBytes(HEADER_LIMIT - header.length);
            byte[] joined = new byte[header.length + rest.length];
            System.arraycopy(header, 0, joined, 0, header.length);
            System.arraycopy(rest, 0, joined, header.length, rest.length);
            header = joined;
            declaration = new String(header, skip, header.length - skip, inferred);
            if (!declaration.contains("?>")) throw CdsPaths.failure("CDS_XML_DECLARATION_LIMIT");
        }
        Charset actual = inferred;
        if (declaration.startsWith("<?xml")) {
            Matcher match = ENCODING.matcher(declaration.substring(0, declaration.indexOf("?>") + 2));
            if (match.find()) {
                try {
                    String named = match.group(2).toUpperCase(Locale.ROOT);
                    if (named.equals("UTF-16") && inferred.name().startsWith("UTF-16")) actual = inferred;
                    else if (named.equals("UTF-32") && inferred.name().startsWith("UTF-32")) actual = inferred;
                    else actual = Charset.forName(named);
                    if (skip > 0 && !actual.equals(inferred)) throw CdsPaths.failure("CDS_XML_ENCODING_MISMATCH");
                } catch (IllegalArgumentException invalid) {
                    throw CdsPaths.failure("CDS_XML_ENCODING_UNSUPPORTED");
                }
            }
        }
        source.unread(header, skip, header.length - skip);
        Reader decoded = new InputStreamReader(source, actual.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT));
        return new MarkupReader(decoded, maxMarkupCharacters);
    }

    /** Constant-space transport signature detection, fed by the source-copy loop without another file pass. */
    public static final class Signature {
        private final int[] prefix=new int[4];
        private int prefixSize,unit=1,unitBytes,value;
        private boolean initialized,little,decided,xml;

        public void accept(byte[] bytes, int offset, int length) {
            if (decided) return;
            int end=offset+length;
            while (offset<end && prefixSize<4 && !initialized) prefix[prefixSize++]=bytes[offset++]&255;
            if (!initialized && prefixSize==4) initialize();
            while (offset<end && !decided) acceptByte(bytes[offset++]&255);
        }

        public boolean xml() {
            if (!initialized) initialize();
            return xml;
        }

        private void initialize() {
            initialized=true; int skip=0;
            if (prefixSize>=4 && prefix[0]==0 && prefix[1]==0 && prefix[2]==254 && prefix[3]==255) {unit=4;skip=4;}
            else if (prefixSize>=4 && prefix[0]==255 && prefix[1]==254 && prefix[2]==0 && prefix[3]==0) {unit=4;little=true;skip=4;}
            else if (prefixSize>=3 && prefix[0]==239 && prefix[1]==187 && prefix[2]==191) skip=3;
            else if (prefixSize>=2 && prefix[0]==254 && prefix[1]==255) {unit=2;skip=2;}
            else if (prefixSize>=2 && prefix[0]==255 && prefix[1]==254) {unit=2;little=true;skip=2;}
            else if (prefixSize>=4 && prefix[0]==0 && prefix[1]==0 && prefix[2]==0 && asciiStart(prefix[3])) unit=4;
            else if (prefixSize>=4 && asciiStart(prefix[0]) && prefix[1]==0 && prefix[2]==0 && prefix[3]==0) {unit=4;little=true;}
            else if (prefixSize>=2 && prefix[0]==0 && asciiStart(prefix[1])) unit=2;
            else if (prefixSize>=2 && asciiStart(prefix[0]) && prefix[1]==0) {unit=2;little=true;}
            for (int i=skip;i<prefixSize && !decided;i++) acceptByte(prefix[i]);
        }

        private void acceptByte(int next) {
            value|=next<<(8*(little?unitBytes:unit-1-unitBytes));
            if (++unitBytes!=unit) return;
            if (!xmlSpace(value)) {decided=true;xml=value=='<';}
            value=0;unitBytes=0;
        }
        private static boolean asciiStart(int value) {return value=='<' || xmlSpace(value);}
        private static boolean xmlSpace(int value) {return value==' ' || value=='\t' || value=='\n' || value=='\r';}
    }

    private static final class MarkupReader extends FilterReader {
        private final int maximum;
        private int state;
        private int markupCharacters;
        private char quote;
        private char previous;
        private char beforePrevious;
        private final StringBuilder prefix = new StringBuilder(9);

        MarkupReader(Reader input, int maximum) { super(input); this.maximum = maximum; }

        @Override public int read(char[] buffer, int offset, int count) throws IOException {
            int n = super.read(buffer, offset, count);
            for (int i = 0; i < n; i++) inspect(buffer[offset + i]);
            return n;
        }

        @Override public int read() throws IOException {
            int value = super.read();
            if (value >= 0) inspect((char) value);
            return value;
        }

        private void inspect(char value) throws IOException {
            if (state == 0) {
                if (value == '<') { state = 1; markupCharacters = 1; quote = 0; prefix.setLength(0); prefix.append(value); }
            } else if (state == 2) {
                if (beforePrevious == ']' && previous == ']' && value == '>') state = 0;
            } else {
                if (++markupCharacters > maximum) throw CdsPaths.failure("CDS_XML_MARKUP_LIMIT");
                if (state == 3) {
                    if (beforePrevious == '-' && previous == '-' && value == '>') state = 0;
                } else if (state == 4) {
                    if (previous == '?' && value == '>') state = 0;
                } else {
                    if (prefix.length() < 9) {
                        prefix.append(value);
                        if (prefix.toString().equals("<![CDATA[")) state = 2;
                        else if (prefix.toString().equals("<!--")) state = 3;
                        else if (prefix.toString().equals("<?")) state = 4;
                    }
                    if (state == 1) {
                        if (quote != 0) { if (value == quote) quote = 0; }
                        else if (value == '\'' || value == '"') quote = value;
                        else if (value == '>') state = 0;
                    }
                }
            }
            beforePrevious = previous; previous = value;
        }
    }
}
