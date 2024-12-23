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
package org.openbravo.modulescript;

import java.sql.PreparedStatement;

import org.openbravo.database.ConnectionProvider;

/**
 * This module script is used to create Media Size records based on existing Product Media records
 * 
 * As part of RM-17696, URL and Media_Size fields of M_Product_Media have been deprecated and 
 * replaced by a new table M_Product_Media_Size. In order not to lose existing data, Media Size records 
 * will be created for each existing media, keeping the URL and Media Size of the original record.
 */
public class MigrateProductMediaSizeAndURL extends ModuleScript {

  @Override
  public void execute() {
    try {
      ConnectionProvider cp = getConnectionProvider();
      PreparedStatement ps = null;

      StringBuilder sqlStr = new StringBuilder();
      sqlStr.append("INSERT INTO m_product_media_size ");
      sqlStr.append("SELECT ");
      sqlStr.append("  get_uuid(), pm.ad_client_id, pm.ad_org_id, 'Y', NOW(), '100', NOW(), '100', ");
      sqlStr.append("  pm.m_product_media_id, pm.url, pm.media_size ");
      sqlStr.append("FROM m_product_media pm ");
      sqlStr.append("WHERE pm.url IS NOT NULL AND pm.media_size IS NOT NULL ");
      sqlStr.append("AND NOT EXISTS (SELECT 1 FROM m_product_media_size pms ");
      sqlStr.append("                WHERE pms.m_product_media_id = pm.m_product_media_id)");

      ps = cp.getPreparedStatement(sqlStr.toString());
      ps.executeUpdate();
      ps.close();
      
    } catch (Exception e) {
      handleError(e);
    }
  }

  @Override
  protected ModuleScriptExecutionLimits getModuleScriptExecutionLimits() {
    return new ModuleScriptExecutionLimits("0", new OpenbravoVersion(3, 0, 223900), new OpenbravoVersion(3, 0, 251001));
  }

  @Override
  protected boolean executeOnInstall() {
    return false;
  }
}
