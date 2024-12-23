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

package org.openbravo.test.process.order;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;

import java.math.BigDecimal;
import java.util.Date;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.client.kernel.RequestContext;
import org.openbravo.common.actionhandler.ProcessPurchaseOrderUtility;
import org.openbravo.dal.core.DalUtil;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBCriteria;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.WindowAccess;
import org.openbravo.model.ad.ui.Window;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.common.order.Order;
import org.openbravo.model.common.order.OrderLine;
import org.openbravo.test.base.OBBaseTest;

/**
 * Tests cases to check purchase order status
 */
public class PurchaseOrderStatusTransitionTest extends OBBaseTest {

  final static private Logger log = LogManager.getLogger();

  // User Openbravo
  private final String USER_ID = "100";
  // Client QA Testing
  private final String CLIENT_ID = "4028E6C72959682B01295A070852010D";
  // Organization Spain
  private final String ORGANIZATION_ID = "357947E87C284935AD1D783CF6F099A1";
  // Role QA Testing Admin
  private final String ROLE_ID = "4028E6C72959682B01295A071429011E";
  // Purchase Order: 800010
  private final String PURCHASEORDER_ID = "2C9CEDC0761A41DCB276A5124F8AAA90";

  /**
   * Test to check Booked Status when Purchase Order is in Pending Approval status and action is
   * Approve.
   */

  @Before
  public void initialize() {
    OBContext.setOBContext(USER_ID, ROLE_ID, CLIENT_ID, ORGANIZATION_ID);

    // Switch context with new Manual Role
    String roleId = createRole();
    OBContext.setOBContext(USER_ID, roleId, CLIENT_ID, ORGANIZATION_ID);

    VariablesSecureApp vars = new VariablesSecureApp(USER_ID, CLIENT_ID, ORGANIZATION_ID, roleId,
        OBContext.getOBContext().getLanguage().getLanguage());
    RequestContext.get().setVariableSecureApp(vars);
  }

  @After
  public void cleanUp() {
    OBDal.getInstance().rollbackAndClose();
  }

  @Test
  public void testBookedPurchaseOrderStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrderinPendingApprovalStatus();
    boolean createRoleAccessForProcessDefn = false;
    createRoleAccessForProcessDefn = ProcessPurchaseOrderUtility
        .createRoleAcessForProcessDefinition(roleId);
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_APPROVE, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Booked': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_BOOKED));
    if (createRoleAccessForProcessDefn) {
      ProcessPurchaseOrderUtility.removeRoleAcessForProcessDefinition(roleId);
    }
  }

  /**
   * Test to check Rejected Status when Purchase Order is in Pending Approval status and action is
   * Reject.
   */
  @Test
  public void testRejectedPurchaseOrderStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrderinPendingApprovalStatus();
    boolean createRoleAccessForProcessDefn = ProcessPurchaseOrderUtility
        .createRoleAcessForProcessDefinition(roleId);
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_REJECT, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Rejected': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_REJECTED));
    if (createRoleAccessForProcessDefn) {
      ProcessPurchaseOrderUtility.removeRoleAcessForProcessDefinition(roleId);
    }
  }

  /**
   * Test to check Draft Status when Purchase Order is in Rejected Status and action is Reactivate.
   */
  @Test
  public void testDraftPurchaseOrderStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrderinRejectedStatus();
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_REACTIVATE, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Draft': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_DRAFT));
  }

  /**
   * Test to check Closed Status when Purchase Order is in Rejected Status and action is Close.
   */

  @Test
  public void testClosedPurchaseOrderStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrderinRejectedStatus();
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_CLOSE, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Closed': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_CLOSE));
  }

  /**
   * Create Order in Pending Approval Status
   */

  private Order createOrderinPendingApprovalStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrder();
    boolean removeRoleAccessForProcessDefn = ProcessPurchaseOrderUtility
        .removeRoleAcessForProcessDefinition(roleId);
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_BOOK, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Pending Approval': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_PENDINGAPPROVAL));
    if (removeRoleAccessForProcessDefn) {
      ProcessPurchaseOrderUtility.createRoleAcessForProcessDefinition(roleId);
    }
    return testOrder;
  }

  /**
   * Create Order in Rejected Status
   */

  private Order createOrderinRejectedStatus() {
    String roleId = OBContext.getOBContext().getRole().getId();
    Order testOrder = createOrderinPendingApprovalStatus();
    boolean createRoleAccessForProcessDefn = ProcessPurchaseOrderUtility
        .createRoleAcessForProcessDefinition(roleId);
    ProcessPurchaseOrderUtility.manageDocumentStatusBasedOnAction(testOrder,
        ProcessPurchaseOrderUtility.DOCACTION_REJECT, roleId);
    OBDal.getInstance().refresh(testOrder);
    assertThat("Purchase Order Status is Rejected': ", testOrder.getDocumentStatus(),
        equalTo(ProcessPurchaseOrderUtility.DOCSTATUS_REJECTED));
    if (createRoleAccessForProcessDefn) {
      ProcessPurchaseOrderUtility.removeRoleAcessForProcessDefinition(roleId);
    }
    return testOrder;
  }

  /**
   * Create Purchase Order by making copy from existing PO.
   */

  private Order createOrder() {
    Order order = OBDal.getInstance().get(Order.class, PURCHASEORDER_ID);
    Order testOrder = (Order) DalUtil.copy(order, false);
    String documentNo = getNextDocNoForPurchaseOrder(order.getDocumentNo());
    testOrder.setDocumentNo(documentNo);
    testOrder.setOrderDate(new Date());
    testOrder.setScheduledDeliveryDate(new Date());
    testOrder.setSummedLineAmount(BigDecimal.ZERO);
    testOrder.setGrandTotalAmount(BigDecimal.ZERO);
    testOrder.setSkipffmvalidation(true);
    OBDal.getInstance().save(testOrder);
    for (OrderLine orderLine : order.getOrderLineList()) {
      OrderLine testOrderLine = (OrderLine) DalUtil.copy(orderLine, false);
      testOrderLine.setSalesOrder(testOrder);
      OBDal.getInstance().save(testOrderLine);
      testOrder.getOrderLineList().add(testOrderLine);
      OBDal.getInstance().save(testOrder);
    }
    OBDal.getInstance().flush();
    OBDal.getInstance().refresh(testOrder);
    log.debug("Order Created:" + testOrder.getDocumentNo());
    return testOrder;
  }

  /**
   * Returns the next Document Number for the Purchase Order with the given Document No.
   */
  private String getNextDocNoForPurchaseOrder(String testDocNo) {
    OBCriteria<Order> obc = OBDal.getInstance().createCriteria(Order.class);
    obc.add(Restrictions.like(Order.PROPERTY_DOCUMENTNO, testDocNo + "-%"));
    return testDocNo + "-" + obc.list().size();
  }

  /**
   * Create Role for Process Purchase Order Test
   */
  private String createRole() {
    try {
      OBContext.setAdminMode(false);
      // Create a manual role
      Role newRole = (Role) DalUtil.copy(OBDal.getInstance().get(Role.class, ROLE_ID), true);
      newRole.setName(getNameForNewRole(newRole.getName()));
      newRole.setManual(true);
      OBDal.getInstance().save(newRole);

      // Create window access with Purchase Order window for Role.
      WindowAccess windowAccess = OBProvider.getInstance().get(WindowAccess.class);
      windowAccess.setOrganization(OBDal.getInstance().get(Organization.class, "0"));
      windowAccess.setRole(newRole);
      windowAccess.setWindow(OBDal.getInstance().get(Window.class, "181"));
      windowAccess.setActive(true);
      windowAccess.setEditableField(true);
      OBDal.getInstance().save(windowAccess);

      newRole.getADWindowAccessList().add(windowAccess);
      OBDal.getInstance().save(newRole);
      OBDal.getInstance().flush();

      return newRole.getId();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  /**
   * Returns the name of role with count of roles with same name
   */
  private String getNameForNewRole(String roleName) {
    OBCriteria<Role> obc = OBDal.getInstance().createCriteria(Role.class);
    obc.add(Restrictions.like(Role.PROPERTY_NAME, roleName + "-%"));
    return roleName + "-" + obc.list().size();
  }
}
