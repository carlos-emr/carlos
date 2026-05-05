package io.github.carlos_emr.carlos.providers.gate;

/**
 * Action for viewing the provider fax configuration error page.
 * This action validates the required appointment privileges before displaying the error view.
 *
 * @since 2026-03-25
 */
public final class ViewProviderFaxErr2Action extends BaseProviderViewGate2Action {
    @Override
    protected String getSecurityObject() {
        return "_appointment";
    }

    @Override
    protected String getAccessRight() {
        return "r";
    }
}
