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

import java.io.IOException;
import java.io.Writer;
import java.sql.Timestamp;
import java.util.Date;
import java.util.Map;

import javax.inject.Inject;
import javax.persistence.Tuple;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.io.IOUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.User;
import org.openbravo.service.password.PasswordStrengthChecker;

/**
 * Second servlet created for the "Forgot Password" functionality, meant to be used when a user
 * wants to reset his password with a new one.
 * <p>
 * It receives the new password and makes all the appropriate checks to validate that the password
 * can be securely changed.
 */
public class ResetPasswordWithTokenService extends HttpServlet {

  private static final long serialVersionUID = 1L;
  private static final Logger log = LogManager.getLogger();

  @Inject
  private PasswordStrengthChecker passwordStrengthChecker;

  @Override
  public void doPost(HttpServletRequest request, HttpServletResponse response)
      throws IOException, ServletException {

    try {
      OBContext.setAdminMode(true);
      JSONObject body = new JSONObject(IOUtils.toString(request.getReader()));

      String token = body.optString("token");
      String newPwd = body.optString("newPassword");
      if (!passwordStrengthChecker.isStrongPassword(newPwd)) {
        log.warn("Password not strong enough."); // DO NOT LOG PASSWORDS / SECRETS
        throw new ForgotPasswordException("ERROR_PASSWORDNOTSTRONG", "",
            "Password not strong enough.");
      }

      String hqlToken = "select userContact.id, redeemed, creationDate from ADUserPwdResetToken where usertoken = :token";

      Tuple tokenEntry = OBDal.getInstance()
          .getSession()
          .createQuery(hqlToken, Tuple.class)
          .setParameter("token", token)
          .uniqueResult();

      if (tokenEntry == null) {
        log.warn("Token not found."); // DO NOT LOG PASSWORDS / SECRETS
        throw new ForgotPasswordException("Token not valid.");
      }

      String userId = tokenEntry.get(0, String.class);
      Boolean isRedeemed = tokenEntry.get(1, Boolean.class);
      Timestamp creationDate = tokenEntry.get(2, Timestamp.class);

      User user = OBDal.getInstance().get(User.class, userId);
      String hql = "select cli.resetPasswordLinkTimeout from ClientInformation cli where cli.client.id = :clientid";
      Long resetPasswordLinkTimeout = OBDal.getInstance()
          .getSession()
          .createQuery(hql, Long.class)
          .setParameter("clientid", user.getClient().getId())
          .uniqueResult();

      if (!checkExpirationOfToken(creationDate, isRedeemed, resetPasswordLinkTimeout)) {
        log.warn("Token expired"); // DO NOT LOG PASSWORDS / SECRETS
        throw new ForgotPasswordException("Token not valid.");
      }

      if (PasswordHash.matches(newPwd, user.getPassword())) {
        log.warn("The user has introduced the same password");
        throw new ForgotPasswordException("ERROR_SAMEPASSWORD", "",
            "The user has introduced the same password");
      }

      updateIsRedeemedValue(token, user);
      user.setPassword(PasswordHash.generateHash(newPwd));
      OBDal.getInstance().flush();

      writeResult(response, new JSONObject(
          Map.of("result", "SUCCESS", "userName", user.getUsername(), "userId", user.getId())));

    } catch (ForgotPasswordException ex) {
      JSONObject result = new JSONObject(Map.of("result", ex.getResult(), "clientMsg",
          ex.getClientMsg(), "message", ex.getMessage()));
      writeResult(response, result);
    } catch (JSONException ex) {
      JSONObject result = new JSONObject(Map.of("result", "ERROR", "message", ex.getMessage()));
      writeResult(response, result);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private boolean checkExpirationOfToken(Timestamp creationDate, boolean isRedeemed,
      Long expirationTimeMinutes) {
    Date now = new Date();
    long difference = now.getTime() - creationDate.getTime();
    long expirationTime = expirationTimeMinutes * 60 * 1000;
    boolean valid = difference < expirationTime;
    return !isRedeemed && valid;
  }

  private int updateIsRedeemedValue(String token, User user) {
    String hql = "UPDATE ADUserPwdResetToken SET redeemed = 'Y', updatedBy = :author, updated = now() WHERE usertoken = :token";

    return OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("token", token)
        .setParameter("author", user)
        .executeUpdate();
  }

  private void writeResult(HttpServletResponse response, JSONObject result) throws IOException {
    response.setContentType("application/json;charset=UTF-8");
    response.setHeader("Content-Type", "application/json;charset=UTF-8");

    final Writer w = response.getWriter();
    w.write(result.toString());
    w.close();
  }
}
