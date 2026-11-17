/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.tickler.pageUtil;

/** Dedicated suggested-text route; request parameters cannot select the tickler editor. */
public class EditTicklerTextSuggest2Action extends EditTickler2Action {
    @Override
    protected boolean suggestedTextRoute() { return true; }
}
