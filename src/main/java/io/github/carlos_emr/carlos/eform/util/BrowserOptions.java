/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.eform.util;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pinned Chromium capabilities sent unchanged to the local W3C service. */
final class BrowserOptions {
    private final List<String> args = new ArrayList<>();
    private final Map<String, Object> chrome = new LinkedHashMap<>();
    private final Map<String, Object> capabilities = new LinkedHashMap<>();
    BrowserOptions() {
        chrome.put("args", args);
        capabilities.put("browserName", "chrome");
        capabilities.put("goog:chromeOptions", chrome);
    }
    void addArguments(String... values) { args.addAll(List.of(values)); }
    void setBinary(String binary) { chrome.put("binary", binary); }
    void setAcceptInsecureCerts(boolean accept) { capabilities.put("acceptInsecureCerts", accept); }
    void setCapability(String key, Object value) { capabilities.put(key, value); }
    Map<String, Object> asMap() { return Map.copyOf(capabilities); }
}
