package io.github.carlos_emr.carlos.providers.gate;

public final class ViewProviderColourErr2Action extends BaseProviderViewGate2Action {
    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "r";
    }
}
