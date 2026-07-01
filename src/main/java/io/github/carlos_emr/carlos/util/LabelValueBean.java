package io.github.carlos_emr.carlos.util;
/**
 * A generic bean for holding a label-value pair.
 * <p>
 * Commonly used across the CARLOS EMR UI for populating dropdown lists, radio
 * button groups, and other selection controls.
 * </p>
 */


public class LabelValueBean {

    public LabelValueBean() {
        // Bind label and value for UI component rendering, typically used in select dropdowns.

    }
    public LabelValueBean(String label, String value) {
        this.label = label;
        this.value = value;
    }

    private String label = null;

    public String getLabel() {
        return this.label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    /**
     * The property which supplies the value returned to the server.
     */
    private String value = null;

    public String getValue() {
        return this.value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
