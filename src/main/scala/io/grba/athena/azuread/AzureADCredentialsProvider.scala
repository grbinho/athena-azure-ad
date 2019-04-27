package io.grba.athena.azuread

import java.io.IOException

import com.simba.athena.amazonaws.util.IOUtils
import com.simba.athena.iamsupport.plugin.SamlCredentialsProvider
import com.simba.athena.shaded.apache.commons.logging.LogFactory
import com.simba.athena.shaded.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import com.simba.athena.shaded.apache.http.util.EntityUtils

import scala.util.{Failure, Success, Try}

class AzureADCredentialsProvider extends SamlCredentialsProvider {
  private val logger = LogFactory.getFactory.getInstance(classOf[AzureADCredentialsProvider])

  val TENANT_ID_PROPERTY = "aad_tenant_id"
  val APP_ID_PROPERTY = "aad_app_id"
  val HOST_PROPERTY = "idp_host"
  val USERNAME_PROPERTY = "user"
  val PASSWORD_PROPERTY = "password"

  var m_tenant_id: Option[String] = None
  var m_app_id: Option[String] = None

  override def addParameter(key: String, value: String): Unit = {
    super.addParameter(key, value)

    if (TENANT_ID_PROPERTY == key) m_tenant_id = Some(value)
    if (APP_ID_PROPERTY == key) m_app_id = Some(value)
  }

  //TODO: Windows integrated authentication

  private def checkAndGetProperty(property: Option[String], propertyName: String): String =
    property match {
      case None => throw new IOException(s"Missing required property: ${propertyName}.")
      case Some(value) => value
    }

  @throws(classOf[IOException])
  override def getSamlAssertion: String = {
    val username = checkAndGetProperty(Option(m_userName), USERNAME_PROPERTY)
    val password = checkAndGetProperty(Option(m_password), PASSWORD_PROPERTY)
    val host = checkAndGetProperty(Option(m_idpHost), HOST_PROPERTY)
    val tenantId = checkAndGetProperty(m_tenant_id, TENANT_ID_PROPERTY)
    val appId = checkAndGetProperty(m_app_id, APP_ID_PROPERTY)

    logger.trace("Initiating Azure AD forms authentication.")
    formBasedAuthentication(host, tenantId, appId, username, password)
  }

  private def logResponseHeaders(response: CloseableHttpResponse): Unit = {
    val headersString = response.getAllHeaders.foldRight("")((h, acc) => acc.concat(s"${h.getName}:${h.getValue}\n"))
    logger.trace(s"Response headers: ${headersString}")
  }

  private def formBasedAuthentication(host: String, tenantId: String, appId: String, username: String, password: String): String = {
    val samlUrl = s"https://${host}/${tenantId}/saml2"
    val loginUrl = s"https://${host}/${tenantId}/login"

    Try {
      val samlUtils = new SAMLUtils(appId)
      val loginFormUtils = new LoginFormUtils()
      val httpClient = this.getHttpClient
      val samlRequest = samlUtils.getSAMLRequest

      val loginRequestUrl = s"${samlUrl}?SAMLRequest=${samlRequest}"
      val loginPageGetRequest = new HttpGet(loginRequestUrl)
      val loginPageResult = httpClient.execute(loginPageGetRequest)

      if (loginPageResult.getStatusLine.getStatusCode != 200) {
        throw new IOException(s"Failed to send request: ${loginPageResult.getStatusLine.getReasonPhrase}")
      }

      val loginPage = EntityUtils.toString(loginPageResult.getEntity)
      logResponseHeaders(loginPageResult)

      val loginFormConfig = loginFormUtils.getFormConfigFromPage(loginPage)
      val xMsRequestId = loginPageResult.getHeaders("x-ms-request-id").head.getValue
      val loginPostRequest = new HttpPost(loginUrl)

      val loginForm = loginFormUtils.setupLoginForm(loginFormConfig, username, password, xMsRequestId)

      loginPostRequest.setEntity(loginForm)
      val loginResponse = httpClient.execute(loginPostRequest)
      logResponseHeaders(loginResponse)

      if (loginResponse.getStatusLine.getStatusCode != 200) {
        throw new IOException(s"Failed to send request: ${loginResponse.getStatusLine.getReasonPhrase}")
      }

      val loginResult = EntityUtils.toString(loginResponse.getEntity)
      val samlResponse = samlUtils.getSAMLResponse(loginResult.toString)

      IOUtils.closeQuietly(httpClient, logger)

      samlResponse
    } match {
      case Success(r) => r
      case Failure(ex: IOException) => throw ex
      case Failure(ex) => throw new IOException(ex.getMessage, ex)
    }
  }
}
