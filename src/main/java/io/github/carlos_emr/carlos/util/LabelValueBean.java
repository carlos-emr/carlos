package io.github.carlos_emr.carlos.util;

/**
 * Simple bean representing a label-value pair, commonly used for populating
 * HTML select options and other UI components where a display label is
 * associated with a form submission value.
 *
 * @since 2001-01-01
 */
public class LabelValueBean {

    /**
     * Creates a new instance with null label and value.
     */
    public LabelValueBean() {

    }

    /**
     * Creates a new instance with the specified label and value.
     *
     * @param label String the display label
     * @param value String the form submission value
     */
    public LabelValueBean(String label, String value) {
        this.label = label;
        this.value = value;
    }

    private String label = null;

    /**
     * Returns the display label.
     *
     * @return String the label, or null if not set
     */
    public String getLabel() {
        return this.label;
    }

    /**
     * Sets the display label.
     *
     * @param label String the label to set
     */
    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The property which supplies the value returned to the server.
     */
    private String value = null;

    /**
     * Returns the form submission value.
     *
     * @return String the value, or null if not set
     */
    public String getValue() {
        return this.value;
    }

    /**
     * Sets the form submission value.
     *
     * @param value String the value to set
     */
    public void setValue(String value) {
        this.value = value;
    }
}
