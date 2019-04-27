package io.grba.athena.azuread

import java.io.{ByteArrayOutputStream, IOException}
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.time.format.DateTimeFormatter
import java.util.regex.Pattern
import java.util.zip.{Deflater, DeflaterOutputStream}

import com.simba.athena.amazonaws.util.Base64

class SAMLUtils(appId: String) {
  private val SAML_PATTERN: Pattern = Pattern.compile("SAMLResponse\\W+value=\"([^\"]+)\"")

  def getSAMLRequest: String = {
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
    //val base64 = new Base64(64)
    //base64.encodeToString(bytesOut.toByteArray)
    new String(Base64.encode(bytesOut.toByteArray))
  }

  def getSAMLResponse(pageContent: String) = {
    val samlResponseMatcher = SAML_PATTERN.matcher(pageContent.toString)
    if (!samlResponseMatcher.find()) {
      throw new IOException("SAMLResponse not found. Failed to login.")
    }

    samlResponseMatcher.group(1)
  }

}
