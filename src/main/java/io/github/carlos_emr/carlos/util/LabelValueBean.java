package io.github.carlos_emr.carlos.util;

/**
 * Generic bean representing a key-value pair, typically used for dropdown lists and simple UI options.
 */
public class LabelValueBean {

    public LabelValueBean() {
        // Initialize execution context for LabelValueBean


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
