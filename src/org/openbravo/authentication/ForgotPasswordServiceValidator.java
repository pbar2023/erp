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

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import javax.enterprise.util.AnnotationLiteral;

import org.codehaus.jettison.json.JSONObject;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;

/**
 * Validator intended to be used in the {@link ForgotPasswordService} servlet, which validates the
 * received request for the application in which it is being used.
 */
public interface ForgotPasswordServiceValidator {

  public void validate(Client client, Organization org, JSONObject body)
      throws ForgotPasswordException;

  @javax.inject.Qualifier
  @Retention(RetentionPolicy.RUNTIME)
  @Target({ ElementType.FIELD, ElementType.METHOD, ElementType.TYPE })
  public @interface ApplicationName {
    String value();
  }

  /**
   * A class used to select the correct component provider.
   * 
   * @author mtaal
   */
  @SuppressWarnings("all")
  public static class Selector
      extends AnnotationLiteral<ForgotPasswordServiceValidator.ApplicationName>
      implements ForgotPasswordServiceValidator.ApplicationName {
    private static final long serialVersionUID = 1L;

    final String value;

    public Selector(String value) {
      this.value = value;
    }

    @Override
    public String value() {
      return value;
    }
  }
}
