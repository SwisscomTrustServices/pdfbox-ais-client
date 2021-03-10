# Get authentication details for the AIS client
To start using the Swisscom AIS service and this client library, you need to get a set of authentication details from Swisscom. 
This section walks you through the process. For reference, you can have a look
at the documentation available for [Swisscom Trust Services (AIS included)](https://trustservices.swisscom.com/en/downloads/) and, more specifically, to the
[Reference Guide for AIS](http://documents.swisscom.com/product/1000255-Digital_Signing_Service/Documents/Reference_Guide/Reference_Guide-All-in-Signing-Service-en.pdf).

Using the following link you can request for a free All-in Signing Service test account valid for 90 days: http://documents.swisscom.com/product/filestore/lib/920aa2d3-d413-4776-8630-5ee9b06587b7/OrderTestaccount-en.pdf 
The authentication between an AIS client and the AIS service relies on TLS client authentication. Therefore, you need a certificate
that is enrolled on the Swisscom AIS side. For these steps, a local installation of [OpenSSL](https://www.openssl.org/) is needed
(for Windows, the best option is to use the one that comes with GIT for Windows (see _<git>/usr/bin/openssl.exe_)).

Generate first a private key:
```shell
openssl genrsa -des3 -out my-ais.key 2048
```
Then generate a Certificate Signing Request (CSR):
```shell
openssl req -new -key my-ais.key -out my-ais.csr
```
You will be asked for the following:
```text
Country Name (2 letter code) [AU]: US
State or Province Name (full name) [Some-State]: YourCity
Locality Name (eg, city) []: YourCity
Organization Name (eg, company) [Internet Widgits Pty Ltd]: TEST Your Company
Organizational Unit Name (eg, section) []: For test purposes only
Common Name (e.g. server FQDN or YOUR name) []: TEST Your Name
Email Address []: your.name@yourmail.com
```

Then generate a self-signed certificate (the duration must be 90 days):
```shell
openssl x509 -req -days 90 -in my-ais.csr -signkey my-ais.key -out my-ais.crt
```
The resulting certificate needs to be sent to the Swisscom AIS team for creating an account linked to this certificate. 
This might vary from case to case, so please get in touch with Swisscom and discuss the final steps for authorizing the certificate.

## Get the AIS Claimed Identities and the relevant CA certificates
Besides the TLS client certificate, you also need the Claimed Identity strings to use with the AIS Client and the trusted 
TLS server certificates. 

## Using these details
As an example, once you have the TLS client certificate authorized and enrolled on the Swisscom side and once you have obtained the relevant
Claimed Identities and CA certificates, you can use them for configuring the AIS client in the following way:

Configuration way:
```properties
# ...
server.cert.file=/home/user/ais-server.crt
# ...
client.auth.keyFile=/home/user/ais-client.key
client.auth.keyPassword=secret
client.cert.file=/home/user/ais-client.crt
# ...
signature.claimedIdentityName=ais-90days-trial
signature.claimedIdentityKey=keyEntity
signature.distinguishedName=cn=TEST User, givenname=Max, surname=Maximus, c=US, serialnumber=abcdefabcdefabcdefabcdefabcdef
```

Programmatically way:
```java
RestClientConfiguration restConfig = new RestClientConfiguration();
// ...
restConfig.setServerCertificateFile("/home/user/ais-server.crt");
restConfig.setClientKeyFile("/home/user/ais-client.key");
restConfig.setClientKeyPassword("secret");
restConfig.setClientCertificateFile("/home/user/ais-client.crt");

// ...
UserData userData = new UserData();
userData.setClaimedIdentityName("ais-90days-trial");
userData.setClaimedIdentityKey("keyEntity");
userData.setDistinguishedName("cn=TEST User, givenname=Max, surname=Maximus, c=US, serialnumber=abcdefabcdefabcdefabcdefabcdef");
```

