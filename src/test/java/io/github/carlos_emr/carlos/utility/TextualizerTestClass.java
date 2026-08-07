/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.utility;

import jakarta.persistence.Column;
import java.util.Date;
import java.util.Objects;

/**
 * Test helper class for {@link io.github.carlos_emr.carlos.util.Textualizer} tests.
 *
 * <p>Contains all primitive and wrapper types to verify round-trip serialization
 * via the Textualizer toMap/fromMap API.</p>
 *
 * @since 2026-03-07
 */
public class TextualizerTestClass {

    @Column(name = "fancyColunmNameForDateObj")
    private Date dateObj;
    private Integer integerObj;
    private Boolean booleanObj;
    private Long longObj;
    private String stringObj;
    private Character characterObj;
    private Byte byteObj;
    private Short shortObj;
    private Float floatObj;
    private Double doubleObj;
    private int intPrim;
    private boolean booleanPrim;
    private long longPrim;
    private char charPrim;
    private byte bytePrim;
    private short shortPrim;
    private float floatPrim;
    private double doublePrim;

    public Date getDateObj() { return dateObj; }
    public void setDateObj(Date dateObj) { this.dateObj = dateObj; }
    public Integer getIntegerObj() { return integerObj; }
    public void setIntegerObj(Integer integerObj) { this.integerObj = integerObj; }
    public Boolean getBooleanObj() { return booleanObj; }
    public void setBooleanObj(Boolean booleanObj) { this.booleanObj = booleanObj; }
    public Long getLongObj() { return longObj; }
    public void setLongObj(Long longObj) { this.longObj = longObj; }
    public String getStringObj() { return stringObj; }
    public void setStringObj(String stringObj) { this.stringObj = stringObj; }
    public Character getCharacterObj() { return characterObj; }
    public void setCharacterObj(Character characterObj) { this.characterObj = characterObj; }
    public Byte getByteObj() { return byteObj; }
    public void setByteObj(Byte byteObj) { this.byteObj = byteObj; }
    public Short getShortObj() { return shortObj; }
    public void setShortObj(Short shortObj) { this.shortObj = shortObj; }
    public Float getFloatObj() { return floatObj; }
    public void setFloatObj(Float floatObj) { this.floatObj = floatObj; }
    public Double getDoubleObj() { return doubleObj; }
    public void setDoubleObj(Double doubleObj) { this.doubleObj = doubleObj; }
    public int getIntPrim() { return intPrim; }
    public void setIntPrim(int intPrim) { this.intPrim = intPrim; }
    public boolean isBooleanPrim() { return booleanPrim; }
    public void setBooleanPrim(boolean booleanPrim) { this.booleanPrim = booleanPrim; }
    public long getLongPrim() { return longPrim; }
    public void setLongPrim(long longPrim) { this.longPrim = longPrim; }
    public char getCharPrim() { return charPrim; }
    public void setCharPrim(char charPrim) { this.charPrim = charPrim; }
    public byte getBytePrim() { return bytePrim; }
    public void setBytePrim(byte bytePrim) { this.bytePrim = bytePrim; }
    public short getShortPrim() { return shortPrim; }
    public void setShortPrim(short shortPrim) { this.shortPrim = shortPrim; }
    public float getFloatPrim() { return floatPrim; }
    public void setFloatPrim(float floatPrim) { this.floatPrim = floatPrim; }
    public double getDoublePrim() { return doublePrim; }
    public void setDoublePrim(double doublePrim) { this.doublePrim = doublePrim; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TextualizerTestClass that = (TextualizerTestClass) o;
        return intPrim == that.intPrim && booleanPrim == that.booleanPrim
                && longPrim == that.longPrim && charPrim == that.charPrim
                && bytePrim == that.bytePrim && shortPrim == that.shortPrim
                && Float.compare(that.floatPrim, floatPrim) == 0
                && Double.compare(that.doublePrim, doublePrim) == 0
                && Objects.equals(dateObj, that.dateObj)
                && Objects.equals(integerObj, that.integerObj)
                && Objects.equals(booleanObj, that.booleanObj)
                && Objects.equals(longObj, that.longObj)
                && Objects.equals(stringObj, that.stringObj)
                && Objects.equals(characterObj, that.characterObj)
                && Objects.equals(byteObj, that.byteObj)
                && Objects.equals(shortObj, that.shortObj)
                && Objects.equals(floatObj, that.floatObj)
                && Objects.equals(doubleObj, that.doubleObj);
    }

    @Override
    public int hashCode() {
        return Objects.hash(dateObj, integerObj, booleanObj, longObj, stringObj,
                characterObj, byteObj, shortObj, floatObj, doubleObj,
                intPrim, booleanPrim, longPrim, charPrim, bytePrim,
                shortPrim, floatPrim, doublePrim);
    }
}
