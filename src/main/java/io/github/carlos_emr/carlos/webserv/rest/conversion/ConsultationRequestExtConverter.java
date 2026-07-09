package io.github.carlos_emr.carlos.webserv.rest.conversion;

import io.github.carlos_emr.carlos.commn.model.ConsultationRequestExt;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsultationRequestExtTo1;

/**
 * Converter class specifically built to map between the internal ConsultationRequestExt
 * domain entities and their public-facing REST transfer object counterparts.
 */
public class ConsultationRequestExtConverter extends AbstractConverter<ConsultationRequestExt, ConsultationRequestExtTo1> {
    @Override
    public ConsultationRequestExt getAsDomainObject(LoggedInInfo loggedInInfo, ConsultationRequestExtTo1 t) throws ConversionException {
        // Process standard operational requirements ensuring context-specific compliance

        ConsultationRequestExt d = new ConsultationRequestExt();

        //d.setId(t.getId());
        if (t.getRequestId() != null) {
            d.setRequestId(t.getRequestId());
        }
        d.setKey(t.getKey());
        d.setValue(t.getValue());
        d.setDateCreated(t.getDateCreated());

        return d;
    }

    @Override
    public ConsultationRequestExtTo1 getAsTransferObject(LoggedInInfo loggedInInfo, ConsultationRequestExt d) throws ConversionException {
        ConsultationRequestExtTo1 t = new ConsultationRequestExtTo1();

        t.setId(d.getId());
        t.setRequestId(d.getRequestId());
        t.setKey(d.getKey());
        t.setValue(d.getValue());
        t.setDateCreated(d.getDateCreated());

        return t;
    }
}