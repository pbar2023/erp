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
 * All portions are Copyright (C) 2010-2022 Openbravo SLU 
 * All Rights Reserved. 
 * Contributor(s):  ______________________________________.
 ************************************************************************
 */
package org.openbravo.erpCommon.ad_process;

import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.model.ad.module.DataPackage;
import org.openbravo.model.ad.module.Module;
import org.openbravo.model.ad.module.ModuleDBPrefix;
import org.openbravo.model.ad.module.ModuleDependency;
import org.openbravo.model.ad.system.Language;
import org.openbravo.scheduling.Process;
import org.openbravo.scheduling.ProcessBundle;

/**
 * This process creates a standard module for customization.
 * 
 */
public class CreateCustomModule implements Process {
  private static final String MODULE_NAME = "Custom Module";
  private static final String MODULE_PAKCAGE = "mySystem.customModule";
  private static final String MODULE_DBPREFIX = "CUSTOM";

  @Override
  public void execute(ProcessBundle bundle) throws Exception {
    OBContext.setAdminMode();
    try {
      OBCriteria<Module> modCriteria = OBDal.getInstance().createCriteria(Module.class);
      modCriteria.add(Restrictions.ilike(Module.PROPERTY_NAME, MODULE_NAME));
      if (modCriteria.count() != 0) {
        OBError msg = new OBError();
        msg.setType("Info");
        msg.setMessage(MODULE_NAME + " @ModuleAlreadyExists@");
        bundle.setResult(msg);
        return;
      }
      Module module = OBProvider.getInstance().get(Module.class);
      module.setActive(true);
      module.setName(MODULE_NAME);
      module.setJavaPackage(MODULE_PAKCAGE);
      module.setDescription(MODULE_NAME + " is an autogenerated module to store new developments.");
      module.setInDevelopment(true);
      module.setVersion("1.0.0");
      OBCriteria<Language> langCriteria = OBDal.getInstance().createCriteria(Language.class);
      langCriteria.add(Restrictions.eq(Language.PROPERTY_LANGUAGE, "en_US"));
      module.setLanguage(langCriteria.list().get(0));
      OBDal.getInstance().save(module);

      ModuleDBPrefix dbp = OBProvider.getInstance().get(ModuleDBPrefix.class);
      dbp.setActive(true);
      dbp.setName(MODULE_DBPREFIX);
      dbp.setModule(module);
      OBDal.getInstance().save(dbp);

      DataPackage pack = OBProvider.getInstance().get(DataPackage.class);
      pack.setActive(true);
      pack.setJavaPackage(MODULE_PAKCAGE);
      pack.setModule(module);
      pack.setName(MODULE_PAKCAGE);
      OBDal.getInstance().save(pack);

      ModuleDependency dep = OBProvider.getInstance().get(ModuleDependency.class);
      dep.setActive(true);
      Module core = OBDal.getInstance().get(Module.class, "0");
      dep.setModule(module);
      dep.setDependentModule(core);
      dep.setDependantModuleName(core.getName());
      dep.setFirstVersion(core.getVersion());
      dep.setIncluded(false);
      OBDal.getInstance().save(dep);
      OBDal.getInstance().commitAndClose();

      OBError msg = new OBError();
      msg.setType("Success");
      msg.setTitle("@Success@");
      msg.setMessage("@Success@");
      bundle.setResult(msg);
    } finally {
      OBContext.restorePreviousMode();
    }
  }
}
