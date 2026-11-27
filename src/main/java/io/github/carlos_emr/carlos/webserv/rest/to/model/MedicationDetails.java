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

    public static Integer wholeQuantity(String quantity) {
        if (quantity == null) return null;
        try {
            return new BigDecimal(quantity.strip()).intValueExact();
        } catch (IllegalArgumentException | ArithmeticException incompatible) {
            return null;
        }
    }

    public void validateQuantity(Integer legacyQuantity) {
        if (legacyQuantity != null && !legacyQuantity.equals(wholeQuantity(quantityText))) {
            throw new IllegalArgumentException("Legacy and exact medication quantities disagree");
        }
    }

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

    @XmlAccessorType(XmlAccessType.FIELD)
    public static class Component implements Serializable {
        private static final long serialVersionUID = 1L;
        private String name;
        private String strength;
        private String unit;

        public Component() {}

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
