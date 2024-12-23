/*
 *************************************************************************
 * The contents of this file are subject to the Openbravo  Public  License
 * Version  1.0  (the  "License"),  being   the  Mozilla   Public  License
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
 *************************************************************************
 */

package org.openbravo.modulescript;

import java.util.UUID;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.modulescript.ModuleScript;

public class CreateProcessPurchaseOrderProcessDefinitionAccess extends ModuleScript {
  private static final Logger log4j = Logger
      .getLogger(CreateProcessPurchaseOrderProcessDefinitionAccess.class);

  @Override
  public void execute() {
    try {
      // Get Role
      ConnectionProvider cp = getConnectionProvider();
      CreateProcessPurchaseOrderProcessDefinitionAccessData[] roleList = CreateProcessPurchaseOrderProcessDefinitionAccessData
          .select(cp);
      for (CreateProcessPurchaseOrderProcessDefinitionAccessData role : roleList) {
        String obuiappProcessAccessId = CreateProcessPurchaseOrderProcessDefinitionAccessData
            .existProcessDefinitionAccessForRole(cp, role.adRoleId);
        if (StringUtils.isBlank(obuiappProcessAccessId)) {
          obuiappProcessAccessId = UUID.randomUUID().toString().replace("-", "").toUpperCase();
          CreateProcessPurchaseOrderProcessDefinitionAccessData
              .insertProcessDefinitionAccessForRole(cp.getConnection(), cp, obuiappProcessAccessId,
                  role.adClientId, role.adOrgId, role.adRoleId);
        }
      }
    } catch (Exception e) {
      log4j.error("Error creating process purchase order process definition access for role");
      handleError(e);
    }
  }

  @Override
  protected ModuleScriptExecutionLimits getModuleScriptExecutionLimits() {
    return new ModuleScriptExecutionLimits("0", null,
        new OpenbravoVersion(3, 0, 251001));
  }

  @Override
  protected boolean executeOnInstall() {
    return false;
  }
}
