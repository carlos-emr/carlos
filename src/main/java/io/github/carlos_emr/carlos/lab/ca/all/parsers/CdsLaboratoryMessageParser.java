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
package io.github.carlos_emr.carlos.lab.ca.all.parsers;

import ca.uhn.hl7v2.HL7Exception;
import ca.uhn.hl7v2.model.Segment;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsLaboratoryMeaning;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Year;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.regex.Pattern;

/** CDS text and temporal precision stay independent of legacy trimming and the server timezone. */
public class CdsLaboratoryMessageParser extends CMLHandler {
    private static final Pattern TIMESTAMP=Pattern.compile("([0-9]{14}|[0-9]{12}|[0-9]{8}|[0-9]{6}|[0-9]{4})(\\.[0-9]{1,9})?([+-][0-9]{4})?");
    private static final DateTimeFormatter DATE=DateTimeFormatter.ofPattern("uuuuMMdd").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter MINUTE=DateTimeFormatter.ofPattern("uuuuMMddHHmm").withResolverStyle(ResolverStyle.STRICT);
    private static final DateTimeFormatter SECOND=new DateTimeFormatterBuilder().appendPattern("uuuuMMddHHmmss")
            .appendFraction(ChronoField.NANO_OF_SECOND,0,9,true).toFormatter(Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    private record Field(int number,int repetition,int component,int subcomponent) { }
    private final java.util.Map<Segment,java.util.Map<Field,String>> literalFields=new java.util.IdentityHashMap<>();

    @Override public void init(String body) throws HL7Exception {
        literalFields.clear();
        if(body==null || body.length()>1_048_576) throw new HL7Exception("CDS_LAB_NATIVE_MESSAGE_REQUIRES_REVIEW");
        super.init(body);
        // HAPI 1.x strips leading field whitespace and does not decode arbitrary UTF-8 hex
        // escapes. Keep bounded generated fields beside the parsed structure, without changing CML.
        String[] segments=body.replace("\r\n","\r").split("\r",-1);
        if(segments.length>64) throw new HL7Exception("CDS_LAB_NATIVE_MESSAGE_REQUIRES_REVIEW");
        int group=-1,observation=-1,note=0;
        for(String encoded:segments) {
            if(encoded.isEmpty()) continue;
            String[] fields=encoded.split("\\|",-1);
            Segment segment;
            switch(fields[0]) {
                case "MSH" -> segment=msg.getMSH();
                case "PID" -> segment=msg.getRESPONSE().getPATIENT().getPID();
                case "OBR" -> { group++; observation=-1; segment=msg.getRESPONSE().getORDER_OBSERVATION(group).getOBR(); }
                case "OBX" -> { observation++; note=0; segment=msg.getRESPONSE().getORDER_OBSERVATION(group).getOBSERVATION(observation).getOBX(); }
                case "NTE" -> segment=msg.getRESPONSE().getORDER_OBSERVATION(group).getOBSERVATION(observation).getNTE(note++);
                default -> throw new HL7Exception("CDS_LAB_NATIVE_SEGMENT_UNREADABLE");
            }
            if(fields.length>64) throw new HL7Exception("CDS_LAB_NATIVE_MESSAGE_REQUIRES_REVIEW");
            for(int field=1;field<fields.length;field++) {
                if(fields[0].equals("MSH") && field==1) continue;
                int number=fields[0].equals("MSH")?field+1:field;
                String[] repetitions=fields[field].split("~",-1);
                for(int rep=0;rep<repetitions.length;rep++) {
                    String[] components=repetitions[rep].split("\\^",-1);
                    for(int component=0;component<components.length;component++) {
                        String[] subcomponents=components[component].split("&",-1);
                        for(int sub=0;sub<subcomponents.length;sub++) {
                            literalFields.computeIfAbsent(segment,ignored->new java.util.HashMap<>())
                                    .put(new Field(number,rep,component+1,sub+1),decode(subcomponents[sub]));
                        }
                    }
                }
            }
        }
    }

    private static String decode(String encoded) throws HL7Exception {
        StringBuilder value=new StringBuilder(encoded.length());
        for(int index=0;index<encoded.length();) {
            if(encoded.charAt(index)!='\\') { value.append(encoded.charAt(index++)); continue; }
            int end=encoded.indexOf('\\',index+1);
            if(end<0) throw new HL7Exception("CDS_LAB_NATIVE_ESCAPE_UNREADABLE");
            String code=encoded.substring(index+1,end);
            switch(code) {
                case "E" -> value.append('\\'); case "F" -> value.append('|'); case "S" -> value.append('^');
                case "R" -> value.append('~'); case "T" -> value.append('&');
                default -> {
                    try {
                        if(!code.startsWith("X") || code.length()<3) throw new IllegalArgumentException();
                        byte[] bytes=java.util.HexFormat.of().parseHex(code.substring(1));
                        value.append(java.nio.charset.StandardCharsets.UTF_8.newDecoder()
                                .onMalformedInput(java.nio.charset.CodingErrorAction.REPORT)
                                .onUnmappableCharacter(java.nio.charset.CodingErrorAction.REPORT)
                                .decode(java.nio.ByteBuffer.wrap(bytes)));
                    } catch(IllegalArgumentException | java.nio.charset.CharacterCodingException invalid) {
                        throw new HL7Exception("CDS_LAB_NATIVE_ESCAPE_UNREADABLE");
                    }
                }
            }
            index=end+1;
        }
        return value.toString();
    }

    @Override public String getMsgType() { return "CARLOS-CDS"; }
    @Override protected String getString(String value) { return value==null?"":value; }
    @Override public boolean isOBXAbnormal(int i,int j) { return CdsLaboratoryMeaning.isAbnormal(getOBXAbnormalFlag(i,j)); }
    @Override public String getOBXResultStatus(int i,int j) {
        String raw=obx(i,j,11,1);
        if(CdsLaboratoryMeaning.isFinal(raw)) return "Final";
        return "I".equalsIgnoreCase(raw)?"Pending":raw;
    }
    @Override public String getOBXResult(int i,int j) { return obx(i,j,5,1); }
    @Override public String getOBXName(int i,int j) { return obx(i,j,3,2); }
    @Override public String getOBXNameLong(int i,int j) { return obx(i,j,3,2); }
    @Override public String getOBXIdentifier(int i,int j) { return obx(i,j,3,1); }
    @Override public String getOBXAbnormalFlag(int i,int j) { return obx(i,j,8,1); }
    @Override public String getAccessionNum() { return obr(0,3,1); }
    @Override public String getFirstName() { return field(msg.getRESPONSE().getPATIENT().getPID(),5,2); }
    @Override public String getLastName() { return field(msg.getRESPONSE().getPATIENT().getPID(),5,1); }
    @Override public String getPatientLocation() { return field(msg.getMSH(),4,1); }
    @Override public String getOBXComment(int i,int j,int k) {
        try { return field(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBSERVATION(j).getNTE(k),3,1); }
        catch(Exception unreadable) { throw new IllegalStateException("CDS_LAB_NATIVE_FIELD_UNREADABLE"); }
    }
    @Override public String getOBXReferenceRange(int i,int j) { return obx(i,j,7,1); }
    @Override public String getOBXUnits(int i,int j) { return obx(i,j,6,1); }
    @Override public String getOBRName(int i) { return obr(i,4,2); }
    @Override public String getOBRIdentifier(int i) { return obr(i,4,1); }
    @Override public String getTimeStamp(int i,int j) { return formatDateTime(obr(i,7,1)); }

    private String obx(int i,int j,int field,int component) {
        try { return field(getOBXSegment(i,j),field,component); }
        catch(Exception unreadable) { throw new IllegalStateException("CDS_LAB_NATIVE_FIELD_UNREADABLE"); }
    }
    private String obr(int i,int field,int component) {
        try { return field(msg.getRESPONSE().getORDER_OBSERVATION(i).getOBR(),field,component); }
        catch(Exception unreadable) { throw new IllegalStateException("CDS_LAB_NATIVE_FIELD_UNREADABLE"); }
    }

    private String field(Segment segment,int field,int component) {
        return literalFields.getOrDefault(segment,java.util.Map.of()).getOrDefault(new Field(field,0,component,1),"");
    }

    @Override protected String formatDateTime(String plain) {
        if(plain==null || plain.isEmpty()) return "";
        var match=TIMESTAMP.matcher(plain);
        if(!match.matches()) throw new IllegalStateException("CDS_LAB_NATIVE_TIMESTAMP_UNREADABLE");
        String digits=match.group(1), fraction=match.group(2)==null?"":match.group(2), zone=match.group(3);
        try {
            if(zone!=null && Math.abs(ZoneOffset.of(zone).getTotalSeconds())>14*3600) throw new IllegalArgumentException();
            if(!fraction.isEmpty() && digits.length()!=14) throw new IllegalArgumentException();
            String display=switch(digits.length()) {
                case 4 -> { Year.parse(digits); yield digits; }
                case 6 -> YearMonth.parse(digits,DateTimeFormatter.ofPattern("uuuuMM")).toString();
                case 8 -> LocalDate.parse(digits,DATE).toString();
                case 12 -> LocalDateTime.parse(digits,MINUTE).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm"));
                case 14 -> LocalDateTime.parse(digits+fraction,SECOND).format(DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss"))+fraction;
                default -> throw new IllegalArgumentException();
            };
            return display+(zone==null?"":zone.substring(0,3)+":"+zone.substring(3));
        } catch(java.time.DateTimeException | IllegalArgumentException invalid) { throw new IllegalStateException("CDS_LAB_NATIVE_TIMESTAMP_UNREADABLE"); }
    }
}
