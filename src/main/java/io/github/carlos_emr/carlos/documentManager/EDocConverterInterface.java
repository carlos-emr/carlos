package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Contract for components that convert electronic documents between formats (e.g., HL7, PDF, Image).
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}