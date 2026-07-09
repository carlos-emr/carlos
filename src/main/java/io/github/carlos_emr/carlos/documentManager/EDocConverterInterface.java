package io.github.carlos_emr.carlos.documentManager;

import java.io.OutputStream;

/**
 * Interface defining the contract for services that convert or format
 * various electronic document formats into standard system-compatible representations.
 */
public interface EDocConverterInterface {
  void convert(String html, OutputStream os) throws Exception;
}