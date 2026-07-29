package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;
/**
 * Defines a contract for converting electronic documents from one format to another.
 */

public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}