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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;

import java.io.BufferedReader;
import java.io.StringReader;
import java.sql.SQLException;
import java.util.Map;

import javax.enterprise.inject.Instance;
import javax.mail.Transport;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.codehaus.jettison.json.JSONObject;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.mockito.ArgumentMatchers;
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
import org.openbravo.model.ad.access.UserRoles;
import org.openbravo.model.ad.system.Client;
import org.openbravo.model.ad.system.Language;
import org.openbravo.model.authentication.EmailType;
import org.openbravo.model.common.enterprise.EmailServerConfiguration;
import org.openbravo.model.common.enterprise.EmailTemplate;
import org.openbravo.model.common.enterprise.Organization;
import org.openbravo.test.base.TestConstants;

public class ForgotPasswordServiceTest extends WeldBaseTest {

  @InjectMocks
  ForgotPasswordService forgotPasswordService;

  @Mock
  private Instance<ForgotPasswordServiceValidator> validateInstances;

  @Mock
  private ForgotPasswordServiceValidator forgotPwdServiceValidator;

  @Mock
  private Transport transport;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  private final String APP_NAME = "POS2";
  private final static String TEST_USER_NAME = "FORGOT_PWD_USER";
  private final static String TEST_USER_PWD = PasswordHash.generateHash("FORGOT_PWD_USER_PWD");
  private static User user;
  private static Client client;
  private static Organization org;
  private static EmailServerConfiguration conf;

  @BeforeClass
  public static void createUser() {
    if (userExists()) {
      return;
    }
    OBContext.setAdminMode(true);
    try {
      Language language = OBDal.getInstance().get(Language.class, "128");

      User testUser = OBProvider.getInstance().get(User.class);
      testUser.setClient(OBDal.getInstance().getProxy(Client.class, TEST_CLIENT_ID));
      testUser.setOrganization(OBDal.getInstance().getProxy(Organization.class, TEST_ORG_ID));
      testUser.setName(TEST_USER_NAME);
      testUser.setUsername(TEST_USER_NAME);
      testUser.setPassword(TEST_USER_PWD);
      testUser.setActive(true);
      testUser.setEmail("test@test.com");
      testUser.setLocked(false);
      testUser.setSsoonly(false);
      testUser.setDefaultLanguage(language);

      Role role = OBDal.getInstance().get(Role.class, TestConstants.Roles.FB_GRP_ADMIN);
      role.setWebServiceEnabled(true);

      UserRoles userRoles = OBProvider.getInstance().get(UserRoles.class);
      userRoles.setClient(OBDal.getInstance().getProxy(Client.class, TEST_CLIENT_ID));
      userRoles.setOrganization(OBDal.getInstance().getProxy(Organization.class, "0"));
      userRoles.setUserContact(testUser);
      userRoles.setRole(role);

      client = OBDal.getInstance().get(Client.class, TEST_CLIENT_ID);
      org = OBDal.getInstance().get(Organization.class, TEST_ORG_ID);
      conf = OBProvider.getInstance().get(EmailServerConfiguration.class);
      conf.setClient(client);
      conf.setOrganization(org);
      conf.setSmtpServer("smtp.gmail.com");
      conf.setSMTPAuthentification(true);
      conf.setSmtpServerAccount("smtp@accounttestOB.com");
      conf.setSmtpConnectionSecurity("SSL");
      conf.setSmtpPort((long) 465);
      conf.setSmtpServerSenderAddress("sender@test.com");

      EmailTemplate template = OBProvider.getInstance().get(EmailTemplate.class);
      template.setClient(client);
      template.setOrganization(org);
      template.setEmailType(
          OBDal.getInstance().get(EmailType.class, "5209BE52755B49C582F034E9B98B3F33"));
      template.setLanguage(language); // English UK
      template.setBody("This is a body for an email template");
      template.setSubject("Test");

      updateOrganizationLanguageToEnglishGB(language);
      OBDal.getInstance().save(template);
      OBDal.getInstance().save(conf);
      OBDal.getInstance().save(testUser);
      OBDal.getInstance().save(userRoles);
      OBDal.getInstance().commitAndClose();
      user = testUser;
    } catch (Exception ex) {
      throw new OBException(
          "Could not create user for Openbravo Reset Password With Token Service testing", true);
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @AfterClass
  public static void cleanUpDB() throws SQLException {
    OBContext.setAdminMode(true);
    try {
      cleanUpUserToken();
      cleanUpUser();
      cleanUpEmailServerConfiguration();
      resetOrganizationLanguageToNull();
      cleanUpEmailTemplate();
      OBDal.getInstance().commitAndClose();
    } finally {
      OBContext.restorePreviousMode();
    }
  }

  @Test
  public void testProcessForgotPwdRequest_NoException() throws Exception {
    try {
      OBContext.setAdminMode(true);
      String requestBody = new JSONObject(Map.of("appName", APP_NAME, "client", TEST_CLIENT_ID,
          "organization", TEST_ORG_ID, "userOrEmail", TEST_USER_NAME)).toString();
      BufferedReader reader = new BufferedReader(new StringReader(requestBody));
      when(request.getReader()).thenReturn(reader);

      when(validateInstances.select(any())).thenReturn(validateInstances);
      when(validateInstances.get()).thenReturn(forgotPwdServiceValidator);
      doNothing().when(forgotPwdServiceValidator)
          .validate(ArgumentMatchers.any(Client.class), ArgumentMatchers.any(Organization.class),
              ArgumentMatchers.any(JSONObject.class));

      forgotPasswordService.processForgotPwdRequest(request, response);

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  public static void updateOrganizationLanguageToEnglishGB(Language language) {
    String hql = "UPDATE Organization SET language = :language WHERE id = :orgId";

    OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("orgId", org.getId())
        .setParameter("language", language)
        .executeUpdate();
  }

  public static void resetOrganizationLanguageToNull() {
    String hql = "UPDATE Organization SET language = null WHERE id = :orgId";

    OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("orgId", org.getId())
        .executeUpdate();
  }

  public static void cleanUpUser() throws SQLException {
    String hql = "DELETE FROM ADUser u WHERE u.id = :userId";

    OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("userId", user.getId())
        .executeUpdate();
    OBDal.getInstance().commitAndClose();
  }

  private static void cleanUpEmailServerConfiguration() {
    OBContext.setAdminMode(true);
    try {
      String hql = "DELETE FROM EmailServerConfiguration WHERE smtpServerAccount = :testAccount";

      OBDal.getInstance()
          .getSession()
          .createQuery(hql)
          .setParameter("testAccount", "smtp@accounttestOB.com")
          .executeUpdate();

    } finally {
      OBContext.restorePreviousMode();
    }
  }

  private static void cleanUpEmailTemplate() {
    String hql = "DELETE FROM EmailTemplate e WHERE e.body = 'This is a body for an email template'";

    OBDal.getInstance().getSession().createQuery(hql).executeUpdate();
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

  private static void cleanUpUserToken() {
    String hql = "DELETE FROM ADUserPwdResetToken WHERE client = :clientId AND organization = :orgId AND userContact = :userId";

    OBDal.getInstance()
        .getSession()
        .createQuery(hql)
        .setParameter("clientId", client)
        .setParameter("orgId", org)
        .setParameter("userId", user)
        .executeUpdate();
  }

}
