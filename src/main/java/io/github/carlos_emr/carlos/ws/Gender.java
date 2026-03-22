package io.github.carlos_emr.carlos.ws;

import jakarta.xml.bind.annotation.XmlEnum;
import jakarta.xml.bind.annotation.XmlType;

/**
 * Enumeration of gender values for the HNR web service client model.
 *
 * <p>Values: M (Male), F (Female), T (Transgender), O (Other), U (Unknown).
 *
 * @since 2012-08-13
 */
@XmlType(name = "gender")
@XmlEnum
public enum Gender
{
    M, 
    F, 
    T, 
    O, 
    U;
    
    /**
     * Returns the string value of this gender code.
     *
     * @return String the single-character gender code
     */
    public String value() {
        return this.name();
    }
    
    /**
     * Converts a string value to the corresponding Gender enum constant.
     *
     * @param s String the gender code string (M, F, T, O, or U)
     * @return Gender the matching enum constant
     * @throws IllegalArgumentException if the string does not match any gender code
     */
    public static Gender fromValue(final String s) {
        return valueOf(s);
    }
}
