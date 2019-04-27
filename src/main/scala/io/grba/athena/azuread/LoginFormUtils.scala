package io.grba.athena.azuread

import com.google.gson.JsonParser
import com.simba.athena.shaded.apache.http.client.entity.UrlEncodedFormEntity
import com.simba.athena.shaded.apache.http.message.BasicNameValuePair

object LoginFormUtils {

  val LOGIN_FORM_KEY = "login"
  val LOGIN_FMT_FORM_KEY = "loginfmt"
  val PASSWORD_FORM_KEY = "passwd"
  val LOGIN_OPTIONS_FORM_KEY = "LoginOptions"
  val LOGIN_OPTIONS_FORM_VALUE = "1"
  val HPG_REQUEST_ID_FORM_KEY = "hpgrequestid"
  val CANARY_FORM_KEY = "canary"
  val CTX_FORM_KEY = "ctx"
  val FLOW_TOKEN_FORM_KEY = "flowToken"

  private def getConfigJson(pageContents: String): String = {
    val startIndex = pageContents.indexOf("$Config=") + "$Config=".length
    val endIndex = pageContents.indexOf("//]]", startIndex)
    val configString = pageContents.substring(startIndex, endIndex)
    val configStringClean = configString.trim().stripSuffix(";")
    configStringClean
  }

  case class LoginFormConfig(sFT: String, sCtx: String, canary: String)

  def getFormConfigFromPage(loginPageContent: String) : LoginFormConfig = {
    val configJsonString = getConfigJson(loginPageContent)
    val jsonConfig = new JsonParser().parse(configJsonString).getAsJsonObject

    val sFT = jsonConfig.get("sFT").getAsString
    val sCtx = jsonConfig.get("sCtx").getAsString
    val canary = jsonConfig.get("canary").getAsString

    LoginFormConfig(sFT, sCtx, canary)
  }

  def setupLoginForm(loginFormConfig: LoginFormConfig, username: String, password: String, hpgRequestId: String): UrlEncodedFormEntity = {
    import scala.collection.JavaConverters._

    val loginFormValues = List(
      new BasicNameValuePair(LOGIN_FORM_KEY, username),
      new BasicNameValuePair(LOGIN_FMT_FORM_KEY, username),
      new BasicNameValuePair(PASSWORD_FORM_KEY, password),
      new BasicNameValuePair(LOGIN_OPTIONS_FORM_KEY, LOGIN_OPTIONS_FORM_VALUE),
      new BasicNameValuePair(HPG_REQUEST_ID_FORM_KEY, hpgRequestId),
      new BasicNameValuePair(CANARY_FORM_KEY, loginFormConfig.canary),
      new BasicNameValuePair(CTX_FORM_KEY, loginFormConfig.sCtx),
      new BasicNameValuePair(FLOW_TOKEN_FORM_KEY, loginFormConfig.sFT)
    )

    new UrlEncodedFormEntity(loginFormValues.asJava)
  }

}
