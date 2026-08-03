package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Core interface defining the contract for services that convert electronic
 * documents (e.g., HL7, PDF) into internal standardized models.
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}