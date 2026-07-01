package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.Measurement;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MeasurementTo1;
/**
 * Converter for patient measurement data in REST APIs.
 * <p>
 * Handles the translation between internal measurement entity models and the DTOs
 * exposed by the CARLOS EMR REST web services.
 * </p>
 */


public class MeasurementConverter extends AbstractConverter<Measurement, MeasurementTo1> {
    @Override
    public Measurement getAsDomainObject(LoggedInInfo loggedInInfo, MeasurementTo1 t) throws ConversionException {
        // Convert internal measurement values to the standard REST DTO format for API consistency.
        Measurement d = new Measurement();
        //Sets the properties
        //d.setId(t.getId());
        d.setType(t.getType());
        d.setDemographicId(t.getDemographicId());
        d.setProviderNo(t.getProviderNo());
        d.setDataField(t.getDataField());
        d.setMeasuringInstruction(t.getMeasuringInstruction());
        d.setComments(t.getComments());
        d.setDateObserved(t.getDateObserved());
        d.setAppointmentNo(t.getAppointmentNo());
        d.setCreateDate(t.getCreateDate());
        return d;
    }

    @Override
    public MeasurementTo1 getAsTransferObject(LoggedInInfo loggedInInfo, Measurement d) throws ConversionException {
        MeasurementTo1 t = new MeasurementTo1();
        t.setId(d.getId());
        t.setType(d.getType());
        t.setDemographicId(d.getDemographicId());
        t.setProviderNo(d.getProviderNo());
        t.setDataField(d.getDataField());
        t.setMeasuringInstruction(d.getMeasuringInstruction());
        t.setComments(d.getComments());
        t.setDateObserved(d.getDateObserved());
        t.setAppointmentNo(d.getAppointmentNo());
        t.setCreateDate(d.getCreateDate());
        return t;
    }
}