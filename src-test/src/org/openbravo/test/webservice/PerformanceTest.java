/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.1  (the  "License"),  being   the  Mozilla   Public  License
 * Version 1.1  with a permitted attribution clause; you may not  use this
 * file except in compliance with the License. You  may  obtain  a copy of
 * the License at http://www.openbravo.com/legal/license.html 
 * Software distributed under the License  is  distributed  on  an "AS IS"
 * basis, WITHOUT WARRANTY OF ANY KIND, either express or implied. See the
 * License for the specific  language  governing  rights  and  limitations
 * under the License. 
 * The Original Code is Openbravo ERP. 
 * The Initial Developer of the Original Code is Openbravo SLU 
 * All portions are Copyright (C) 2009-2022 Openbravo SLU
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */

package org.openbravo.test.webservice;

import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Authenticator;
import java.net.HttpURLConnection;
import java.net.PasswordAuthentication;
import java.net.URI;
import java.net.URL;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.Test;
import org.openbravo.base.exception.OBException;

/**
 * Test class for reading large xml files from a webservice. Xml is streamed directly to a file.
 * 
 * NOTE: contains several hard-coded path (/tmp/test.xml) so is not for general usage in test
 * suites.
 * 
 * @author mtaal
 */

public class PerformanceTest extends BaseWSTest {
  private static final Logger log = LogManager.getLogger();

  @Test
  public void testPerformance() {
    try {
      final HttpURLConnection hc = createConnection("/ws/dal/BusinessPartner?includeChildren=false",
          "GET");
      hc.connect();
      final InputStream is = hc.getInputStream();
      final OutputStream os = new FileOutputStream("/tmp/test.xml");
      int totalRead = 0;
      int cnt = 0;
      while (true) {
        final byte[] bytes = new byte[50000];
        final int bytesRead = is.read(bytes);
        if (bytesRead == -1) {
          break;
        }
        os.write(bytes, 0, bytesRead);
        totalRead += bytesRead;
        if ((cnt++ % 100) == 0) {
          System.err.println(totalRead);
          os.flush();
        }
      }
      os.close();
    } catch (final Exception e) {
      throw new OBException(e);
    }
  }

  /**
   * Creates a HTTP connection.
   * 
   * @param wsPart
   * @param method
   *          POST, PUT, GET or DELETE
   * @return the created connection
   * @throws Exception
   */
  @Override
  protected HttpURLConnection createConnection(String wsPart, String method) throws Exception {
    Authenticator.setDefault(new Authenticator() {
      @Override
      protected PasswordAuthentication getPasswordAuthentication() {
        return new PasswordAuthentication(LOGIN, PWD.toCharArray());
      }
    });
    log.debug(method + ": " + getOpenbravoURL() + wsPart);
    final URL url = new URI(getOpenbravoURL() + wsPart).toURL();
    final HttpURLConnection hc = (HttpURLConnection) url.openConnection();
    hc.setRequestMethod(method);
    hc.setAllowUserInteraction(false);
    hc.setDefaultUseCaches(false);
    hc.setDoOutput(true);
    hc.setDoInput(true);
    hc.setInstanceFollowRedirects(true);
    hc.setUseCaches(false);
    hc.setRequestProperty("Content-Type", "text/xml");
    return hc;
  }
}
