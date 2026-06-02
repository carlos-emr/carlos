package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Interface for electronic document conversion services.
 * Specifies the contract for components that transform document formats (e.g., Word to PDF) before they are attached to a chart.
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}