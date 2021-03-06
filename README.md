# Azure AD Credentials Provider for Athena JDBC driver

At the time of writing, Athena JDBC driver (JDBC42 v 2.0.7) does not support authentication via Azure AD.

Code in this repository enables that connection.

To use the provider, you will need to add this provides jar file together with athena JDBC jar to the tool of your choice and set `AwsCredentialsProviderClass` property to `io.grba.athena.azuread.AzureADCredentialsProvider`.

Apart from you domain username and password you need to set up some additional properties related to Azure AD:

`idp_host`: normally this will be `login.microsoftonline.com`
`azad_tenant_id`:  You can find this in Azure AD AWS app (Directory (tenant) ID)
`azad_app_id`:  You can find this in Azure AD AWS app (Application (client) ID)

Until it's officially supported by AWS, I hope this works for you!

You can download the JAR from releases page or just clone this repo and run `sbt assembly` to package the code.