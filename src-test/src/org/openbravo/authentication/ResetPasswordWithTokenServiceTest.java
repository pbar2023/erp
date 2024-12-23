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

import static org.junit.Assert.assertEquals;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.PrintWriter;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.Base64;
import java.util.Date;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.hibernate.criterion.Restrictions;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.openbravo.authentication.hashing.PasswordHash;
import org.openbravo.base.exception.OBException;
import org.openbravo.base.provider.OBProvider;
import org.openbravo.base.weld.test.WeldBaseTest;
import org.openbravo.dal.core.OBContext;
import org.openbravo.dal.service.OBDal;
import org.openbravo.model.ad.access.Role;
import org.openbravo.model.ad.access.User;
import org.openbravo.model.ad.access.UserPwdResetToken;
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.service.password.PasswordStrengthChecker;
import org.openbravo.test.base.TestConstants;

public class ResetPasswordWithTokenServiceTest extends WeldBaseTest {

  @InjectMocks
  private ResetPasswordWithTokenService resetPasswordWithTokenService;

  @Mock
  private PasswordStrengthChecker passwordStrengthChecker;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  @Mock
  private static OBContext obContext;

  private SecureRandom secureRandom;
  private Base64.Encoder base64Encoder;
  private String token;
  private final static String TEST_USER_NAME = "RSTPWD_User";
  private final static String TEST_USER_PWD = PasswordHash.generateHash("RSTPWD_User_pwd");
  private static User user;

  @BeforeClass
  public static void createUser() {
    if (userExists()) {
      return;
    }
    OBContext.setAdminMode(true);
    try {
      User testUser = OBProvider.getInstance().get(User.class);
      testUser.setClient(OBDal.getInstance().getProxy(Client.class, TEST_CLIENT_ID));
      testUser.setOrganization(OBDal.getInstance().getProxy(Organization.class, TEST_ORG_ID));
      testUser.setName(TEST_USER_NAME);
      testUser.setUsername(TEST_USER_NAME);
      testUser.setPassword(TEST_USER_PWD);

      Role role = OBDal.getInstance().get(Role.class, TestConstants.Roles.FB_GRP_ADMIN);
      role.setWebServiceEnabled(true);

      UserRoles userRoles = OBProvider.getInstance().get(UserRoles.class);
      userRoles.setClient(OBDal.getInstance().getProxy(Client.class, TEST_CLIENT_ID));
      userRoles.setOrganization(OBDal.getInstance().getProxy(Organization.class, "0"));
      userRoles.setUserContact(testUser);
      userRoles.setRole(role);

      OBDal.getInstance().save(testUser);
      OBDal.getInstance().save(userRoles);
      OBDal.getInstance().commitAndClose();
    } catch (Exception ex) {
      throw new OBException(
          "Could not create user for Openbravo Reset Password With Token Service testing", true);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @AfterClass
  public static void cleanUpUser() throws SQLException {
    OBContext.setAdminMode(true);
    try {
      String hql = "DELETE FROM ADUser u WHERE u.id = :userId";

      OBDal.getInstance()
          .getSession()
          .createQuery(hql)
          .setParameter("userId", user.getId())
          .executeUpdate();
      OBDal.getInstance().commitAndClose();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @Before
  public void initTests() {
    user = getUser();
    secureRandom = new SecureRandom();
    base64Encoder = Base64.getUrlEncoder();
    token = generateNewToken();
    createTokenEntry();
  }

  @After
  public void cleanUpToken() throws SQLException {
    // We need to remove the token
    OBContext.setAdminMode(true);
    try {
      String hql = "DELETE FROM ADUserPwdResetToken WHERE usertoken = :tokenhql";

      OBDal.getInstance()
          .getSession()
          .createQuery(hql)
          .setParameter("tokenhql", token)
          .executeUpdate();
      OBDal.getInstance().commitAndClose();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @Test
  public void testDoPost_Success() throws Exception {
    String newPassword = "StrongPassword123!";
    String requestBody = new JSONObject(Map.of("token", token, "newPassword", newPassword))
        .toString();
    BufferedReader reader = new BufferedReader(new StringReader(requestBody));
    when(request.getReader()).thenReturn(reader);
    StringWriter stringWriter = new StringWriter();
    PrintWriter responseWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(responseWriter);
    when(passwordStrengthChecker.isStrongPassword(newPassword)).thenReturn(true);

    resetPasswordWithTokenService.doPost(request, response);

    responseWriter.flush();
    String responseString = stringWriter.toString();
    JSONObject result = new JSONObject(responseString);
    assertEquals("SUCCESS", result.getString("result"));
    assertEquals(user.getId(), result.getString("userId"));
    assertEquals(user.getUsername(), result.getString("userName"));
  }

  @Test
  public void testDoPost_InvalidPasswordBecauseOfWeakness() throws Exception {
    String newPassword = "weakpassword";
    String requestBody = new JSONObject(Map.of("token", token, "newPassword", newPassword))
        .toString();
    BufferedReader reader = new BufferedReader(new StringReader(requestBody));
    when(request.getReader()).thenReturn(reader);
    StringWriter stringWriter = new StringWriter();
    PrintWriter responseWriter = new PrintWriter(stringWriter);
    when(response.getWriter()).thenReturn(responseWriter);
    when(passwordStrengthChecker.isStrongPassword(newPassword)).thenReturn(false);

    resetPasswordWithTokenService.doPost(request, response);

    responseWriter.flush();
    String responseString = stringWriter.toString();
    JSONObject result = new JSONObject(responseString);
    assertEquals("ERROR_PASSWORDNOTSTRONG", result.getString("result"));
    assertEquals("Password not strong enough.", result.getString("message"));
  }

  private void createTokenEntry() {
    OBContext.setAdminMode(true);
    try {
      Date fiveMinutesAgo = new Date(System.currentTimeMillis() - 5 * 60 * 1000);
      UserPwdResetToken resetToken = OBProvider.getInstance().get(UserPwdResetToken.class);
      resetToken.setClient(user.getClient());
      resetToken.setOrganization(user.getOrganization());
      resetToken.setUsertoken(token);
      resetToken.setUserContact(user);
      resetToken.setCreationDate(fiveMinutesAgo);
      OBDal.getInstance().save(resetToken);
      OBDal.getInstance().commitAndClose();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private String generateNewToken() {
    byte[] randomBytes = new byte[24];
    secureRandom.nextBytes(randomBytes);
    return base64Encoder.encodeToString(randomBytes);
  }

  private static boolean userExists() {
    String hql = "SELECT u FROM ADUser u WHERE u.name = :name";

    return OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("name", TEST_USER_NAME)
        .setMaxResults(1)
        .uniqueResult() != null;
  }

  private static User getUser() {
    OBContext.setAdminMode(true);
    try {
      List<User> users = OBDal.getInstance()
          .createCriteria(User.class)
          .add(Restrictions.eq(User.PROPERTY_NAME, TEST_USER_NAME))
          .setFilterOnActive(true)
          .setFilterOnReadableClients(false)
          .setFilterOnReadableOrganization(false)
          .list();
      return users.get(0);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

}
