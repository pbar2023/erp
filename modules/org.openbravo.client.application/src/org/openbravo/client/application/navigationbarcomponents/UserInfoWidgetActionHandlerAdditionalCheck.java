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
package org.openbravo.client.application.navigationbarcomponents;

import java.util.List;

import org.openbravo.model.ad.access.Role;

/**
 * Subclasses of this interface can be used by UserInfoWidgetActionHandler to perform additional
 * checks on Role change request on those applications defined by the instance
 */
public interface UserInfoWidgetActionHandlerAdditionalCheck {
  void checkRole(Role role, String appName);

  /**
   * List of Application search keys where the logic implemented in this checker is applicable
   *
   * @return a List of the application searchKeys where it is applicable to execute this checker
   */
  List<String> getApplicableAppSearchKeyList();
}
