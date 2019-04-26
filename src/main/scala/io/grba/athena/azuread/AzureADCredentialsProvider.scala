package io.grba.athena.azuread

import java.io.{ByteArrayOutputStream, IOException}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.zip.{Deflater, DeflaterOutputStream}

import com.google.gson.JsonParser
import com.simba.athena.amazonaws.util.IOUtils
import com.simba.athena.iamsupport.plugin.SamlCredentialsProvider
import com.simba.athena.shaded.apache.commons.logging.LogFactory
import com.simba.athena.shaded.apache.http.client.entity.UrlEncodedFormEntity
import com.simba.athena.shaded.apache.http.client.methods.{CloseableHttpResponse, HttpGet, HttpPost}
import com.simba.athena.shaded.apache.http.message.BasicNameValuePair
import com.simba.athena.shaded.apache.http.util.EntityUtils
import org.apache.commons.codec.binary.Base64

import scala.util.{Failure, Success, Try}

class AzureADCredentialsProvider extends SamlCredentialsProvider {
  private val SAML_PATTERN: Pattern = Pattern.compile("SAMLResponse\\W+value=\"([^\"]+)\"")

  val TENANT_ID_PROPERTY = "azad_tenant_id"
  val APP_ID_PROPERTY = "azad_app_id"

  var m_tenant_id: String = null
  var m_app_id: String = null

  override def addParameter(key: String, value: String): Unit = {
    super.addParameter(key, value)

    if (TENANT_ID_PROPERTY == key) m_tenant_id = value
    if (APP_ID_PROPERTY == key) m_app_id = value
  }

  //TODO: Windows integrated authentication

  val logger = LogFactory.getLog("AzureADCredentialsProvider")

  private def checkAndGetProperty(property: String, propertyName: String): String =
    Option(property) match {
      case None => throw new IOException(s"Missing required property: ${propertyName}.")
      case Some(value) => value
    }

  @throws(classOf[IOException])
  override def getSamlAssertion: String = {
    val username = Option(m_userName)
    val password = Option(m_password)

    val host = checkAndGetProperty(m_idpHost, "idp_host")
    val tenantId = checkAndGetProperty(m_tenant_id, TENANT_ID_PROPERTY)
    val appId = checkAndGetProperty(m_app_id, APP_ID_PROPERTY)


    (username, password) match {
      case (Some(u), Some(p)) => formBasedAuthentication(host, tenantId, appId, u, p)
      case _ => throw new IOException("Missing required property: username or password. Azure AD credentials provider does not currently support Windows integrated authentication. Please provide username and password.")
    }

  }

  private def getSAMLRequest(appId: String): String = {
    val id = java.util.UUID.randomUUID().toString
    val issueInstant = java.time.Instant.now()
    val samlRequest =
      s"""|<samlp:AuthnRequest
          |xmlns="urn:oasis:names:tc:SAML:2.0:metadata"
          |ID="id${id}"
          |Version="2.0" IssueInstant="${DateTimeFormatter.ISO_INSTANT.format(issueInstant)}"
          |xmlns:samlp="urn:oasis:names:tc:SAML:2.0:protocol">
          |<Issuer xmlns="urn:oasis:names:tc:SAML:2.0:assertion">${appId}</Issuer>
          |</samlp:AuthnRequest>""".stripMargin

    val encodedSamlRequest = deflateBase64Encode(samlRequest)
    URLEncoder.encode(encodedSamlRequest)
  }

  private def deflateBase64Encode(input: String): String = {
    val bytesOut = new ByteArrayOutputStream()
    val deflater = new Deflater(Deflater.DEFAULT_COMPRESSION, true);
    val deflaterStream = new DeflaterOutputStream(bytesOut, deflater);
    deflaterStream.write(input.getBytes(StandardCharsets.UTF_8))
    deflaterStream.finish()
    val base64 = new Base64(64)
    base64.encodeToString(bytesOut.toByteArray)
  }

  private def logResponseHeaders(response: CloseableHttpResponse): Unit = {
    val headersString = response.getAllHeaders.foldRight("")((h, acc) => acc.concat(s"${h.getName}:${h.getValue}\n"))
    logger.info(s"Response headers: ${headersString}")
  }

  private def getConfigJson(pageContents: String): String = {
    val startIndex = pageContents.indexOf("$Config=") + "$Config=".length
    val endIndex = pageContents.indexOf("//]]", startIndex)
    val configString = pageContents.substring(startIndex, endIndex)
    val configStringClean = configString.trim().stripSuffix(";")
    configStringClean
  }

  case class LoginFormConfig(sFT: String, sCtx: String, canary: String)

  private def getFormConfigFromPage(loginPageContent: String) : LoginFormConfig = {
    val configJsonString = getConfigJson(loginPageContent)
    val jsonConfig = new JsonParser().parse(configJsonString).getAsJsonObject

    val sFT = jsonConfig.get("sFT").getAsString
    val sCtx = jsonConfig.get("sCtx").getAsString
    val canary = jsonConfig.get("canary").getAsString

    LoginFormConfig(sFT, sCtx, canary)
  }

  private def formBasedAuthentication(host: String, tenantId: String, appId: String, username: String, password: String): String = {
    val samlUrl = s"https://${host}/${tenantId}/saml2"
    val loginUrl = s"https://${host}/${tenantId}/login"

    Try {
      val httpClient = this.getHttpClient
      val samlRequest = getSAMLRequest(appId)

      val loginRequestUrl = s"${samlUrl}?SAMLRequest=${samlRequest}"
      val loginPageGetRequest = new HttpGet(loginRequestUrl)
      val loginPageResult = httpClient.execute(loginPageGetRequest)

      if (loginPageResult.getStatusLine.getStatusCode != 200) {
        throw new IOException(s"Failed to send request: ${loginPageResult.getStatusLine.getReasonPhrase}")
      }

      import collection.JavaConverters._

      val loginPage = EntityUtils.toString(loginPageResult.getEntity)
      logResponseHeaders(loginPageResult)

      val loginFormConfig = getFormConfigFromPage(loginPage)
      val xMsRequestId = loginPageResult.getHeaders("x-ms-request-id").head.getValue

      val loginFormValues = List(
        new BasicNameValuePair("login", username),
        new BasicNameValuePair("loginfmt", username),
        new BasicNameValuePair("passwd", password),
        new BasicNameValuePair("LoginOptions", "1"),
        new BasicNameValuePair("hpgrequestid", xMsRequestId),
        new BasicNameValuePair("canary", loginFormConfig.canary),
        new BasicNameValuePair("ctx", loginFormConfig.sCtx),
        new BasicNameValuePair("flowToken", loginFormConfig.sFT)
      )

      val loginForm = new UrlEncodedFormEntity(loginFormValues.asJava)
      val loginPostRequest = new HttpPost(loginUrl)

      loginPostRequest.setEntity(loginForm)
      val loginResponse = httpClient.execute(loginPostRequest)
      logResponseHeaders(loginResponse)

      if (loginResponse.getStatusLine.getStatusCode != 200) {
        throw new IOException(s"Failed to send request: ${loginResponse.getStatusLine.getReasonPhrase}")
      }

      val loginResult = EntityUtils.toString(loginResponse.getEntity)

      val samlResponseMatcher = SAML_PATTERN.matcher(loginResult.toString)
      if (!samlResponseMatcher.find()) {
        throw new IOException("SAMLResponse not found. Failed to login.")
      }

      IOUtils.closeQuietly(httpClient, logger)

      samlResponseMatcher.group(1)
    } match {
      case Success(r) => r
      case Failure(ex: IOException) => throw ex
      case Failure(ex) => throw new IOException(ex.getMessage, ex)
    }
  }
}
