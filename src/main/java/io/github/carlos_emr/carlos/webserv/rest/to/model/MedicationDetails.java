/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.xml.bind.annotation.XmlAccessType;
import jakarta.xml.bind.annotation.XmlAccessorType;
import jakarta.xml.bind.annotation.XmlElement;
import jakarta.xml.bind.annotation.XmlElementWrapper;
import java.io.Serializable;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import io.github.carlos_emr.carlos.prescript.util.DrugStrength;

/** Opt-in REST representation. Text retains exactly what the medication record stores. */
@XmlAccessorType(XmlAccessType.FIELD)
public class MedicationDetails implements Serializable {
    private static final long serialVersionUID = 1L;
    private static final ObjectMapper JSON = new ObjectMapper();

    private String quantityText;
    private String dispenseUnit;
    private String dosageText;
    @JsonProperty("components")
    @XmlElementWrapper(name = "components")
    @XmlElement(name = "component")
    private List<Component> components;
    private boolean componentDataComplete;

    /**
     * Constructs a {@link MedicationDetails} transfer object from stored exact medication fields and optional JSON components.
     *
     * <p>{@code componentDataComplete} is {@code true} only when {@code stored} decodes to a non-empty list where
     * every component is non-null with a non-blank name, positive numeric strength, and non-blank unit.</p>
     *
     * @param quantity String exact stored dispense quantity text, or {@code null}
     * @param dispenseUnit String exact stored dispense unit text, or {@code null}
     * @param dosage String exact stored dosage or strength text, or {@code null}
     * @param stored String JSON-encoded component array from storage, or {@code null} when no components are stored
     * @return MedicationDetails populated details object with {@code componentDataComplete} evaluated
     * @throws IllegalStateException if {@code stored} is non-null but cannot be parsed as a JSON list of components
     */
    public static MedicationDetails read(String quantity, String dispenseUnit, String dosage, String stored) {
        var details = new MedicationDetails();
        details.quantityText = quantity;
        details.dispenseUnit = dispenseUnit;
        details.dosageText = dosage;
        try {
            details.components = stored == null ? null : JSON.readValue(stored, new TypeReference<>() {});
        } catch (JsonProcessingException error) {
            throw new IllegalStateException("Stored medication components are invalid", error);
        }
        details.componentDataComplete = details.components != null && !details.components.isEmpty()
                && details.components.stream().allMatch(component -> component != null
                    && component.name != null && !component.name.isBlank()
                    && hasNumericStrength(component.strength)
                    && component.unit != null && !component.unit.isBlank());
        return details;
    }

    private static boolean hasNumericStrength(String text) {
        if (text == null) return false;
        try {
            return new BigDecimal(text.strip()).signum() > 0;
        } catch (NumberFormatException invalid) {
            return false;
        }
    }

    /**
     * Serializes {@code components} to JSON for persistence in a {@code MEDIUMTEXT} column (16,777,215 UTF-8 bytes max).
     *
     * @return String JSON representation of {@code components}, or {@code null} when {@code components} is {@code null}
     * @throws IllegalArgumentException if {@code components} contains a {@code null} entry, exceeds 16,777,215 UTF-8 bytes,
     *     or cannot be serialized to JSON
     */
    public String encodeComponents() {
        if (components == null) return null;
        if (components.stream().anyMatch(Objects::isNull)) {
            throw new IllegalArgumentException("An empty medication component was supplied");
        }
        try {
            String encoded = JSON.writeValueAsString(components);
            if (encoded.getBytes(StandardCharsets.UTF_8).length > 16_777_215) {
                throw new IllegalArgumentException("Medication components exceed the database capacity");
            }
            return encoded;
        } catch (JsonProcessingException error) {
            throw new IllegalArgumentException("Medication components could not be encoded", error);
        }
    }

    /**
     * Projects stored quantity text to a legacy exact {@link Integer} when representable without loss.
     *
     * @param quantity String stored dispense quantity text, or {@code null}
     * @return Integer exact integer quantity, or {@code null} when {@code quantity} is {@code null}, fractional,
     *     non-numeric, or outside the 32-bit signed integer range
     */
    public static Integer wholeQuantity(String quantity) {
        if (quantity == null) return null;
        try {
            return new BigDecimal(quantity.strip()).intValueExact();
        } catch (IllegalArgumentException | ArithmeticException incompatible) {
            return null;
        }
    }

    /**
     * Verifies that an echoed legacy integer quantity does not contradict {@code quantityText}.
     *
     * @param legacyQuantity Integer legacy quantity echoed on the request, or {@code null} when omitted
     * @throws IllegalArgumentException if {@code legacyQuantity} is non-null and disagrees with {@link #wholeQuantity(String)}
     */
    public void validateQuantity(Integer legacyQuantity) {
        if (legacyQuantity != null && !legacyQuantity.equals(wholeQuantity(quantityText))) {
            throw new IllegalArgumentException("Legacy and exact medication quantities disagree");
        }
    }

    /**
     * Verifies that echoed legacy single-ingredient strength fields do not contradict {@code dosageText}.
     *
     * @param legacyStrength Float legacy numeric strength echoed on the request, or {@code null} when omitted
     * @param legacyUnit String legacy strength unit echoed on the request, or {@code null} when omitted
     * @throws IllegalArgumentException if either non-null legacy field disagrees with the leading strength projected from {@code dosageText}
     */
    public void validateStrength(Float legacyStrength, String legacyUnit) {
        DrugStrength projected = DrugStrength.fromDosage(dosageText);
        if ((legacyStrength != null && !legacyStrength.equals(projected.amount()))
                || (legacyUnit != null && !legacyUnit.strip().equals(projected.unit()))) {
            throw new IllegalArgumentException("Legacy and exact medication strengths disagree");
        }
    }

    public String getQuantityText() { return quantityText; }
    public void setQuantityText(String quantityText) { this.quantityText = quantityText; }
    public String getDispenseUnit() { return dispenseUnit; }
    public void setDispenseUnit(String dispenseUnit) { this.dispenseUnit = dispenseUnit; }
    public String getDosageText() { return dosageText; }
    public void setDosageText(String dosageText) { this.dosageText = dosageText; }
    public List<Component> getComponents() { return components; }
    public void setComponents(List<Component> components) { this.components = components; }
    public boolean isComponentDataComplete() { return componentDataComplete; }

    /**
     * Single active ingredient entry in an opt-in {@link MedicationDetails} payload, preserving exact recorded
     * ingredient name, strength text, and unit text.
     */
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Component implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String strength;
        private String unit;

        /** Creates an empty component for JAXB/Jackson deserialization. */
        public Component() {}

        /**
         * Creates a component with the supplied ingredient name, strength text, and unit.
         *
         * @param name String ingredient name, or {@code null}
         * @param strength String exact strength text, or {@code null}
         * @param unit String strength unit text, or {@code null}
         */
        public Component(String name, String strength, String unit) {
            this.name = name;
            this.strength = strength;
            this.unit = unit;
        }

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStrength() { return strength; }
        public void setStrength(String strength) { this.strength = strength; }
        public String getUnit() { return unit; }
        public void setUnit(String unit) { this.unit = unit; }
    }
}
