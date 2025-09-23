package ca.openosp.openo.webserv.rest.util;

import com.fasterxml.jackson.databind.module.SimpleModule;

import java.util.Date;

/**
 * Jackson module that registers the SmartDateSerializer for automatic
 * date/datetime format differentiation
 */
public class SmartDateModule extends SimpleModule {

    public SmartDateModule() {
        super("SmartDateModule");
        addSerializer(Date.class, new SmartDateSerializer());
    }
}