package io.grba.athena.azuread

import com.google.gson.JsonParser
import com.simba.athena.shaded.apache.http.client.entity.UrlEncodedFormEntity
import com.simba.athena.shaded.apache.http.message.BasicNameValuePair

class LoginFormUtils {

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
      new BasicNameValuePair("login", username),
      new BasicNameValuePair("loginfmt", username),
      new BasicNameValuePair("passwd", password),
      new BasicNameValuePair("LoginOptions", "1"),
      new BasicNameValuePair("hpgrequestid", hpgRequestId),
      new BasicNameValuePair("canary", loginFormConfig.canary),
      new BasicNameValuePair("ctx", loginFormConfig.sCtx),
      new BasicNameValuePair("flowToken", loginFormConfig.sFT)
    )

    new UrlEncodedFormEntity(loginFormValues.asJava)
  }

}
