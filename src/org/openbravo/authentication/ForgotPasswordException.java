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
 * All portions are Copyright (C) 2024 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.authentication;

/**
 * This exception is thrown when a change password attempt fails
 */
public class ForgotPasswordException extends Exception {

  private static final long serialVersionUID = 1L;

  private final String result;
  private final String clientMsg;

  public ForgotPasswordException(String msg) {
    super(msg);
    this.result = "ERROR";
    this.clientMsg = "";
  }

  public ForgotPasswordException(String result, String clientMsg, String msg) {
    super(msg);
    this.result = result;
    this.clientMsg = clientMsg;
  }

  public String getResult() {
    return result;
  }

  public String getClientMsg() {
    return clientMsg;
  }
}
