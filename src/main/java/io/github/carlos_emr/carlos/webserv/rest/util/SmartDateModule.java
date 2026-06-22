package io.github.carlos_emr.carlos.webserv.rest.util;

import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.Date;

/**
 * Jackson module that registers the SmartDateSerializer (and its inverse
 * SmartDateDeserializer) for automatic date/datetime format differentiation
 */
public class SmartDateModule extends SimpleModule {

    public SmartDateModule() {
        super("SmartDateModule");
        addSerializer(Date.class, new SmartDateSerializer());
        // Inverse of the serializer so Date values can round-trip through the REST API
        // (e.g. a "HH:mm:ss" startTime echoed back to updateAppointment).
        addDeserializer(Date.class, new SmartDateDeserializer());
    }
}