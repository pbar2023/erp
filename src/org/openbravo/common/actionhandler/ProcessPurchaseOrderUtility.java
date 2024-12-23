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
package org.openbravo.common.actionhandler;

import java.sql.SQLException;
import java.util.stream.Collectors;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.application.Process;
import org.openbravo.client.application.ProcessAccess;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.erpCommon.reference.ActionButtonData;
import org.openbravo.erpCommon.reference.PInstanceProcessData;
import org.openbravo.erpCommon.utility.OBError;
import org.openbravo.erpCommon.utility.OBMessageUtils;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.erpCommon.utility.Utility;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.domain.List;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.order.Order;
import org.openbravo.service.db.DalConnectionProvider;

public class ProcessPurchaseOrderUtility {

  private static final Logger log = Logger.getLogger(ProcessPurchaseOrderUtility.class);
  private static final String C_ORDER_POST_PROCESS_ID = "104";
  private static final String ORDER_ACTION_REFERENCE_LIST_ID = "FF80818130217A35013021A672400035";
  public static final String PURCHASE_ORDER_PROCESS_ACCESS_ID = "074F7113FD4546EFB27928103383E8CD";

  // Document Actions
  public static final String DOCACTION_CLOSE = "CL";
  public static final String DOCACTION_BOOK = "CO";
  public static final String DOCACTION_APPROVE = "AP";
  public static final String DOCACTION_REJECT = "RJ";
  public static final String DOCACTION_REACTIVATE = "RE";
  public static final String DOCACTION_PROCESS = "PR";
  public static final String DOCACTION_VOID = "VO";

  // Document Statuses
  public static final String DOCSTATUS_DRAFT = "DR";
  public static final String DOCSTATUS_BOOKED = "CO";
  public static final String DOCSTATUS_CLOSE = "CL";
  public static final String DOCSTATUS_PENDINGAPPROVAL = "PA";
  public static final String DOCSTATUS_REJECTED = "RJ";
  public static final String DOCSTATUS_UNDERWAY = "IP";

  /**
   * Manage the document status based on action for a given purchase order.
   */
  public static JSONObject manageDocumentStatusBasedOnAction(Order order, String docAction,
      String roleId) {

    JSONObject msg = new JSONObject();
    try {
      switch (docAction) {
        case DOCACTION_BOOK:
          if (!roleHasPrivilegesToProcessPurchaseOrder(roleId)) {
            // Set processed = Y, in order make document read only in Pending Approval Status
            order.setProcessed(true);
            updateDocumentStatus(order, DOCSTATUS_PENDINGAPPROVAL, DOCACTION_APPROVE,
                DOCACTION_APPROVE);
            msg = createReturnMessage("success");
          } else {
            // In order to use existing BOOK action from c_order_post1
            // set DocStatus = DR and DocAction = CO.
            updateDocumentStatus(order, DOCSTATUS_DRAFT, DOCACTION_BOOK, DOCACTION_CLOSE);
            msg = createReturnMessage(processOrder(order.getId()));
          }
          break;
        case DOCACTION_APPROVE:
          if (!roleHasPrivilegesToProcessPurchaseOrder(roleId)) {
            msg = createErrorMessage(
                String.format(OBMessageUtils.messageBD("NoRolePrivilegeToApprovePO")));
            break;
          } else {
            // In order to use existing BOOK action from c_order_post1
            // set DocStatus = DR and DocAction = CO.
            order.setProcessed(false);
            updateDocumentStatus(order, DOCSTATUS_DRAFT, DOCACTION_BOOK, DOCACTION_CLOSE);
            msg = createReturnMessage(processOrder(order.getId()));
          }
          break;
        case DOCACTION_REJECT:
          if (!roleHasPrivilegesToProcessPurchaseOrder(roleId)) {
            msg = createErrorMessage(
                String.format(OBMessageUtils.messageBD("NoRolePrivilegeToRejectPO")));
            break;
          }
          // Set processed = Y, in order make document read only in Rejected Status
          order.setProcessed(true);
          updateDocumentStatus(order, DOCSTATUS_REJECTED, DOCACTION_CLOSE, DOCACTION_CLOSE);
          msg = createReturnMessage("success");
          break;
        case DOCACTION_REACTIVATE:
          // In order to use existing REACTIVATE action from c_order_post1
          // set Processed = Y, DocStatus = CO and DocAction = RE.
          order.setProcessed(true);
          updateDocumentStatus(order, DOCSTATUS_BOOKED, DOCACTION_REACTIVATE, DOCACTION_BOOK);
          msg = createReturnMessage(processOrder(order.getId()));
          break;
        case DOCACTION_CLOSE:
          // In order to use existing CLOSE action from c_order_post1
          // set DocStatus = CO and DocAction = CL.
          updateDocumentStatus(order, DOCSTATUS_BOOKED, DOCACTION_CLOSE, "--");
          msg = createReturnMessage(processOrder(order.getId()));
          break;
        case DOCACTION_PROCESS:
          // set DocStatus = IP and DocAction = PR.
          updateDocumentStatus(order, DOCSTATUS_DRAFT, DOCACTION_PROCESS, DOCACTION_BOOK);
          msg = createReturnMessage(processOrder(order.getId()));
          break;
        case DOCACTION_VOID:
          // In order to use existing CLOSE action from c_order_post1
          // set DocStatus = DR and DocAction = VO.
          updateDocumentStatus(order, DOCSTATUS_DRAFT, DOCACTION_VOID, "--");
          msg = createReturnMessage(processOrder(order.getId()));
          break;
        default:
          break;
      }
      return msg;
    } catch (JSONException e) {
      log.error("Error while updating purchase order status.." + e.getMessage());
    } catch (ServletException e) {
      log.error("Error while updating purchase order status.." + e.getMessage());
    } catch (SQLException e) {
      log.error("Error while updating purchase order status.." + e.getMessage());
    }
    return null;
  }

  /**
   * Method to process order using C_Order_Post DB procedure, roll backs in case of error and
   * returns the message
   */

  private static OBError processOrder(String strCOrderId) throws SQLException, ServletException {
    VariablesSecureApp vars = RequestContext.get().getVariablesSecureApp();
    DalConnectionProvider conn = new DalConnectionProvider(true);
    OBError myMessage = null;
    String pinstance = SequenceIdData.getUUID();
    try {
      PInstanceProcessData.insertPInstance(conn, pinstance, C_ORDER_POST_PROCESS_ID, strCOrderId,
          "N", vars.getUser(), vars.getClient(), vars.getOrg());
    } catch (ServletException ex) {
      myMessage = Utility.translateError(conn, vars, vars.getLanguage(), ex.getMessage());
      return myMessage;
    }
    ActionButtonData.process104(conn, pinstance);
    PInstanceProcessData[] pinstanceData = PInstanceProcessData.select(conn, pinstance);
    myMessage = Utility.getProcessInstanceMessage(conn, vars, pinstanceData);
    if (myMessage.getType().equals("Error")) {
      OBDal.getInstance().rollbackAndClose();
    }
    return myMessage;
  }

  /**
   * Create a JSON error message with the given text.
   */
  private static JSONObject createErrorMessage(String messageText) throws JSONException {
    JSONObject errorMessage = new JSONObject();
    errorMessage.put("severity", "error");
    errorMessage.put("title", "Process Order");
    errorMessage.put("text", messageText);
    JSONObject jsonMessage = new JSONObject();
    jsonMessage.put("message", errorMessage);
    return jsonMessage;
  }

  /**
   * Check whether Role has access to Process Definition to Process Purchase Order
   */
  public static boolean roleHasPrivilegesToProcessPurchaseOrder(String roleId) {
    try {
      // Use admin context as user role may not have access to Role entity
      OBContext.setAdminMode(false);
      Role role = OBDal.getInstance().get(Role.class, roleId);
      OBDal.getInstance().refresh(role);
      // if the Role does not have Manual flag set then assume that it has process definition access
      if (!role.isManual()) {
        return true;
      }
      // Check process definition access only for manual roles.
      return !role.getOBUIAPPProcessAccessList()
          .stream()
          .filter(pda -> StringUtils.equals(pda.getObuiappProcess().getId(),
              PURCHASE_ORDER_PROCESS_ACCESS_ID) && pda.isActive())
          .collect(Collectors.toList())
          .isEmpty();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Updates the document status and action for a given purchase order.
   */
  private static void updateDocumentStatus(Order order, String newStatus, String newDocAction,
      String newProcessAction) {
    if (newStatus != null) {
      order.setDocumentStatus(newStatus);
    }
    if (newDocAction != null) {
      order.setDocumentAction(newDocAction);
    }
    if (newProcessAction != null) {
      order.setProcessPo(newProcessAction);
    }
    OBDal.getInstance().save(order);
    OBDal.getInstance().flush();
  }

  /**
   * Return message on successful process
   */
  private static JSONObject createReturnMessage(String msgType) {
    JSONObject jsonRequest = new JSONObject();
    try {
      JSONObject successMessage = new JSONObject();
      successMessage.put("severity", msgType);
      successMessage.put("title", "");
      successMessage.put("text", "Process completed successfully");
      jsonRequest.put("message", successMessage);
    } catch (JSONException e) {
      log.error("Error in createReturnMessage", e);
    }
    return jsonRequest;
  }

  /**
   * Return message on successful process
   */
  private static JSONObject createReturnMessage(OBError msg) {
    JSONObject jsonRequest = new JSONObject();
    try {
      JSONObject successMessage = new JSONObject();
      successMessage.put("severity", msg.getType().toLowerCase());
      successMessage.put("title", msg.getTitle());
      successMessage.put("text", msg.getMessage());
      jsonRequest.put("message", successMessage);
    } catch (JSONException e) {
      log.error("Error in createReturnMessage", e);
    }
    return jsonRequest;
  }

  /**
   * Get default document action for document based on current document status
   */

  public static String getDefaultDocumentAction(String docStatus) {
    switch (docStatus) {
      case DOCSTATUS_DRAFT:
        return getDocActionIdFromSearchKey(DOCACTION_BOOK);
      case DOCSTATUS_PENDINGAPPROVAL:
        return getDocActionIdFromSearchKey(DOCACTION_APPROVE);
      case DOCSTATUS_BOOKED:
      case DOCSTATUS_REJECTED:
        return getDocActionIdFromSearchKey(DOCACTION_CLOSE);
      case DOCSTATUS_UNDERWAY:
        return getDocActionIdFromSearchKey(DOCACTION_BOOK);
      default:
        break;
    }
    return "";
  }

  /**
   * Get next document action for document based on current document status from reference list
   */
  private static String getDocActionIdFromSearchKey(String docAction) {
    OBCriteria<List> deliveryNoteActionCriteria = OBDal.getInstance().createCriteria(List.class);
    deliveryNoteActionCriteria
        .add(Restrictions.eq(List.PROPERTY_REFERENCE + ".id", ORDER_ACTION_REFERENCE_LIST_ID));
    deliveryNoteActionCriteria.add(Restrictions.eq(List.PROPERTY_SEARCHKEY, docAction));
    deliveryNoteActionCriteria.setMaxResults(1);
    return ((List) deliveryNoteActionCriteria.uniqueResult()).getId();
  }

  /**
   * Create access to Process Definition: Process Purchase Order Action Handler from Role used in
   * the test
   */

  public static boolean createRoleAcessForProcessDefinition(String roleId) {
    if (!ProcessPurchaseOrderUtility.roleHasPrivilegesToProcessPurchaseOrder(roleId)) {
      try {
        OBContext.setAdminMode(false);
        ProcessAccess processDefinitionAccess = OBProvider.getInstance().get(ProcessAccess.class);
        processDefinitionAccess.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
        processDefinitionAccess.setRole(OBDal.getInstance().get(Role.class, roleId));
        processDefinitionAccess.setObuiappProcess(OBDal.getInstance()
            .get(Process.class, ProcessPurchaseOrderUtility.PURCHASE_ORDER_PROCESS_ACCESS_ID));
        processDefinitionAccess.setActive(true);
        OBDal.getInstance().save(processDefinitionAccess);
        OBDal.getInstance().flush();
      } finally {
        OBContext.restorePreviousMode();
      }
      return true;
    }
    return false;
  }

  /**
   * Remove access to Process Definition: Process Purchase Order Action Handler from Role used in
   * the test
   */

  public static boolean removeRoleAcessForProcessDefinition(String roleId) {
    boolean removedRoleAccessForProcessDefn = false;
    try {
      OBContext.setAdminMode(false);
      Role role = OBDal.getInstance().get(Role.class, roleId);
      OBDal.getInstance().refresh(role);
      java.util.List<ProcessAccess> processDefnAccessList = role.getOBUIAPPProcessAccessList()
          .stream()
          .filter(pda -> StringUtils.equals(pda.getObuiappProcess().getId(),
              ProcessPurchaseOrderUtility.PURCHASE_ORDER_PROCESS_ACCESS_ID) && role.isActive())
          .collect(Collectors.toList());
      for (ProcessAccess processDefnAccess : processDefnAccessList) {
        OBDal.getInstance().remove(processDefnAccess);
        role.getOBUIAPPProcessAccessList().remove(processDefnAccess);
        OBDal.getInstance().save(role);
        OBDal.getInstance().flush();
        removedRoleAccessForProcessDefn = true;
      }
    } finally {
      OBContext.restorePreviousMode();
    }
    return removedRoleAccessForProcessDefn;
  }
}
