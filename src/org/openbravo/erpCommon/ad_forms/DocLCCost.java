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
 * All portions are Copyright (C) 2014-2018 Openbravo SLU
 * All Rights Reserved.
 * Contributor(s):  ______________________________________.
 *************************************************************************
 */
package org.openbravo.erpCommon.ad_forms;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.util.ArrayList;

import javax.servlet.ServletException;

import org.apache.commons.lang.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.openbravo.base.secureApp.VariablesSecureApp;
import org.openbravo.costing.CostingUtils;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.data.FieldProvider;
import org.openbravo.database.ConnectionProvider;
import org.openbravo.erpCommon.utility.SequenceIdData;
import org.openbravo.model.common.currency.Currency;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.model.materialmgmt.cost.LandedCostCost;

public class DocLCCost extends AcctServer {

  private static final long serialVersionUID = 1L;
  private static final Logger log4jDocLCCost = LogManager.getLogger();

  /** AD_Table_ID */
  private String SeqNo = "0";
  private BigDecimal differenceAmt = BigDecimal.ZERO;

  public DocLCCost() {
  }

  /**
   * Constructor
   * 
   * @param AD_Client_ID
   *          AD_Client_ID
   */
  public DocLCCost(String AD_Client_ID, String AD_Org_ID, ConnectionProvider connectionProvider) {
    super(AD_Client_ID, AD_Org_ID, connectionProvider);
  }

  @Override
  public void loadObjectFieldProvider(ConnectionProvider conn,
      @SuppressWarnings("hiding") String AD_Client_ID, String Id) throws ServletException {
    setObjectFieldProvider(DocLCCostData.selectRegistro(conn, AD_Client_ID, Id));
  }

  /**
   * Load Document Details
   * 
   * @return true if loadDocumentType was set
   */
  @Override
  public boolean loadDocumentDetails(FieldProvider[] data, ConnectionProvider conn) {
    C_Currency_ID = NO_CURRENCY;

    DocumentType = AcctServer.DOCTYPE_LandedCostCost;
    log4jDocLCCost.debug("loadDocumentDetails - C_Currency_ID : " + C_Currency_ID);
    DateDoc = data[0].getField("DateTrx");
    differenceAmt = new BigDecimal(data[0].getField("differenceamt"));
    loadDocumentType(); // lines require doc type
    // Contained Objects
    p_lines = loadLines(conn);
    return true;
  } // loadDocumentDetails

  /**
   * Load Invoice Line
   * 
   * @return DocLine Array
   */
  private DocLine[] loadLines(ConnectionProvider conn) {
    ArrayList<Object> list = new ArrayList<Object>();

    DocLineLCCostData[] data = null;
    try {
      data = DocLineLCCostData.select(conn, Record_ID);
      for (int i = 0; i < data.length; i++) {
        String Line_ID = data[i].mLcMatchedId;
        DocLine_LCCost docLine = new DocLine_LCCost(DocumentType, Record_ID, Line_ID);
        docLine.loadAttributes(data[i], this);
        docLine.m_C_Currency_ID = data[i].cCurrencyId;
        docLine.setWarehouseId(data[i].mWarehouseId);
        docLine.m_C_BPartner_ID = data[i].cBpartnerId;
        docLine.m_M_Product_ID = data[i].mProductId;

        docLine.m_C_Costcenter_ID = data[i].cCostcenterId;
        docLine.m_User1_ID = data[i].user1id;
        docLine.m_User2_ID = data[i].user2id;
        docLine.m_C_Activity_ID = data[i].cActivityId;
        docLine.m_C_Campaign_ID = data[i].cCampaignId;
        docLine.m_A_Asset_ID = data[i].aAssetId;

        docLine.m_DateAcct = DocLineLCCostData.selectAcctDateOrMaxInvoiceAcctDate(conn, DateDoc, Record_ID);
        docLine.setLandedCostTypeId(data[i].mLcTypeId);
        docLine.setIsMatchingAdjusted(data[i].ismatchingadjusted);
        docLine.setLcCostId(data[i].mLcCostId);
        // -- Source Amounts
        String amt = data[i].amount;
        docLine.setAmount(amt);
        list.add(docLine);
      }
    } catch (ServletException e) {
      log4jDocLCCost.warn(e);
    }
    // Return Array
    DocLine[] dl = new DocLine[list.size()];
    list.toArray(dl);
    return dl;
  } // loadLines

  /**
   * Get Balance
   * 
   * @return Zero (always balanced)
   */
  @Override
  public BigDecimal getBalance() {
    BigDecimal retValue = ZERO;
    return retValue;
  } // getBalance

  /**
   * Create Facts (the accounting logic) for MMS, MMR.
   * 
   * <pre>
   *  Shipment
   *      CoGS            DR
   *      Inventory               CR
   *  Shipment of Project Issue
   *      CoGS            DR
   *      Project                 CR
   *  Receipt
   *      Inventory       DR
   *      NotInvoicedReceipt      CR
   * </pre>
   * 
   * @param as
   *          accounting schema
   * @return Fact
   */
  @Override
  public Fact createFact(AcctSchema as, ConnectionProvider conn, Connection con,
      VariablesSecureApp vars) throws ServletException {
    // Select specific definition
    String strClassname = AcctServerData.selectTemplateDoc(conn, as.m_C_AcctSchema_ID,
        DocumentType);
    if (strClassname.equals("")) {
      strClassname = AcctServerData.selectTemplate(conn, as.m_C_AcctSchema_ID, AD_Table_ID);
    }
    if (!strClassname.equals("")) {
      try {
        DocLCCostTemplate newTemplate = (DocLCCostTemplate) Class.forName(strClassname)
            .getDeclaredConstructor()
            .newInstance();
        return newTemplate.createFact(this, as, conn, con, vars);
      } catch (Exception e) {
        log4j.error("Error while creating new instance for DocLCCostTemplate - " + e);
      }
    }
    C_Currency_ID = as.getC_Currency_ID();
    int stdPrecision = 0;
    OBContext.setAdminMode(true);
    try {
      stdPrecision = OBDal.getInstance()
          .get(Currency.class, this.C_Currency_ID)
          .getStandardPrecision()
          .intValue();
    } finally {
      OBContext.restorePreviousMode();
    }

    // create Fact Header
    Fact fact = new Fact(this, as, Fact.POST_Actual);
    String Fact_Acct_Group_ID = SequenceIdData.getUUID();
    String amtDebit = "0";
    String amtCredit = "0";
    DocLine_LCCost line = null;
    Account acctLC = null;
    BigDecimal totalAmount = BigDecimal.ZERO;
    // Lines
    // Added lines: amt to credit, account: landed cost account (with dimensions)
    for (int i = 0; p_lines != null && i < p_lines.length; i++) {
      line = (DocLine_LCCost) p_lines[i];

      BigDecimal amount = new BigDecimal(line.getAmount()).setScale(stdPrecision,
          RoundingMode.HALF_UP);
      acctLC = getLandedCostAccount(line.getLandedCostTypeId(), amount, as, conn);

      log4jDocLCCost.debug("previous to creteline, line.getAmount(): " + line.getAmount());

      amtDebit = "";
      amtCredit = amount.toString();

      fact.createLine(line, acctLC, line.m_C_Currency_ID, amtDebit, amtCredit, Fact_Acct_Group_ID,
          nextSeqNo(SeqNo), DocumentType, line.m_DateAcct, null, conn);

      totalAmount = totalAmount.add(amount);

    }

    // added one line: amt to debit, account: landed cost account (without dimensions)
    if (totalAmount.compareTo(BigDecimal.ZERO) != 0) {
      DocLine line2 = new DocLine(DocumentType, Record_ID, line.m_TrxLine_ID);
      line2.copyInfo(line);

      line2.m_C_BPartner_ID = "";
      line2.m_M_Product_ID = "";
      line2.m_C_Project_ID = "";
      line2.m_C_Costcenter_ID = "";
      line2.m_User1_ID = "";
      line2.m_User2_ID = "";
      line2.m_C_Activity_ID = "";
      line2.m_C_Campaign_ID = "";
      line2.m_A_Asset_ID = "";

      fact.createLine(line2, acctLC, line2.m_C_Currency_ID,
          "Y".equals(line.getIsMatchingAdjusted()) ? totalAmount.add(differenceAmt).toString()
              : totalAmount.toString(),
          amtDebit, Fact_Acct_Group_ID, nextSeqNo(SeqNo), DocumentType, line2.m_DateAcct, null,
          conn);
    }

    // if there is difference between matched amt and cost amt, then accounting is generated
    if (differenceAmt.compareTo(BigDecimal.ZERO) != 0) {
      // if cost adjustment has been generated, then the account is distributed between the goods
      // shipments lines
      if ("Y".equals(line.getIsMatchingAdjusted())) {
        DocLineLCCostData[] dataRcptLineAmt = DocLineLCCostData.selectRcptLineAmt(conn,
            line.getLcCostId());

        for (int j = 0; j < dataRcptLineAmt.length; j++) {
          DocLineLCCostData lineRcpt = dataRcptLineAmt[j];

          BigDecimal rcptAmount = new BigDecimal(lineRcpt.amount).setScale(stdPrecision,
              RoundingMode.HALF_UP);
          amtDebit = "";
          amtCredit = rcptAmount.toString();

          DocLine line4 = new DocLine(DocumentType, Record_ID, line.m_TrxLine_ID);
          line4.copyInfo(line);
          line4.m_C_BPartner_ID = "";
          line4.m_M_Product_ID = lineRcpt.mProductId;
          line4.m_C_Project_ID = "";
          line4.m_C_Costcenter_ID = "";
          line4.m_User1_ID = "";
          line4.m_User2_ID = "";
          line4.m_C_Activity_ID = "";
          line4.m_C_Campaign_ID = "";
          line4.m_A_Asset_ID = "";

          ProductInfo p = new ProductInfo(line4.m_M_Product_ID, conn);

          // If transaction uses Standard Algorithm IPD account will be used, else Asset account
          LandedCostCost landedCostCost = OBDal.getInstance()
              .get(LandedCostCost.class, line.m_TrxHeader_ID);
          Organization org = OBContext.getOBContext()
              .getOrganizationStructureProvider(landedCostCost.getClient().getId())
              .getLegalEntity(landedCostCost.getOrganization());
          Account account = null;
          if (StringUtils
              .equals(CostingUtils.getCostDimensionRule(org, landedCostCost.getCreationDate())
                  .getCostingAlgorithm()
                  .getJavaClassName(), "org.openbravo.costing.StandardAlgorithm")) {
            account = p.getAccount(ProductInfo.ACCTTYPE_P_IPV, as, conn);
          } else {
            account = p.getAccount(ProductInfo.ACCTTYPE_P_Asset, as, conn);
          }

          fact.createLine(line4, account, line4.m_C_Currency_ID, amtCredit, amtDebit,
              Fact_Acct_Group_ID, nextSeqNo(SeqNo), DocumentType, line4.m_DateAcct, null, conn);

        }

      }
    }

    SeqNo = "0";
    return fact;
  } // createFact

  /**
   * @return the seqNo
   */
  public String getSeqNo() {
    return SeqNo;
  }

  /**
   * @param seqNo
   *          the seqNo to set
   */
  public void setSeqNo(String seqNo) {
    SeqNo = seqNo;
  }

  /**
   * @return the serialVersionUID
   */
  public static long getSerialVersionUID() {
    return serialVersionUID;
  }

  public String nextSeqNo(String oldSeqNo) {
    log4jDocLCCost.debug("DocLCCost - oldSeqNo = " + oldSeqNo);
    BigDecimal seqNo = new BigDecimal(oldSeqNo);
    SeqNo = (seqNo.add(new BigDecimal("10"))).toString();
    log4jDocLCCost.debug("DocLCCost - nextSeqNo = " + SeqNo);
    return SeqNo;
  }

  /**
   * Get the account for Accounting Schema
   * 
   * @param as
   *          accounting schema
   * @return Account
   */
  public final Account getLandedCostAccount(String lcTypeId, BigDecimal amount, AcctSchema as,
      ConnectionProvider conn) {
    String Account_ID = "";
    DocLineLCCostData[] data = null;
    Account acct = null;
    try {
      DocLineLCCostData[] dataAcctType = DocLineLCCostData.selectLCAccount(conn, lcTypeId);
      if (!"".equals(dataAcctType[0].accountId)) {
        data = DocLineLCCostData.selectGlitem(conn, dataAcctType[0].accountId,
            as.getC_AcctSchema_ID());
        if (data.length > 0) {
          Account_ID = data[0].glitemDebitAcct;
          if (amount != null && amount.signum() < 0) {
            Account_ID = data[0].glitemCreditAcct;
          }
        }
      } else if (!"".equals(dataAcctType[0].mProductId)) {
        data = DocLineLCCostData.selectLCProduct(conn, dataAcctType[0].mProductId,
            as.getC_AcctSchema_ID());
        if (data.length > 0) {
          Account_ID = data[0].accountId;
        }
      } else {
        log4jDocLCCost
            .warn("getLCCostAccount - NO account for landed cost type " + dataAcctType[0].name);
        return null;
      }

      // No account
      if (Account_ID.equals("")) {
        log4jDocLCCost
            .warn("getLCCostAccount - NO account for landed cost type =" + dataAcctType[0].name);
        return null;
      }
      // Return Account
      acct = Account.getAccount(conn, Account_ID);
    } catch (ServletException e) {
      log4jDocLCCost.warn(e);
    }
    return acct;
  } // getAccount

  /**
   * Get Document Confirmation
   * 
   * not used
   */
  @Override
  public boolean getDocumentConfirmation(ConnectionProvider conn, String strRecordId) {
    return true;
  }

  @Override
  public String getServletInfo() {
    return "Servlet for the accounting";
  } // end of getServletInfo() method
}
