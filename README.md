# PDFBox based AIS Java Client 

A Java client library for using the [Swisscom All-in Signing Service (AIS)](https://www.swisscom.ch/en/business/enterprise/offer/security/all-in-signing-service.html)
to sign and/or timestamp PDF documents. The library can be used either as a project dependency or as a command-line tool for batch operations. 
It relies on the [Apache PDFBox](https://pdfbox.apache.org/) library for PDF processing.

## Demo Video: PDFBox Client Video Demo

[![Watcht the video](https://i.imgur.com/14dJmDU.png)](https://youtu.be/bISFCfkqzWY)

See it on SharePoint

* https://swisscom-my.sharepoint.com/:v:/p/mike_sauerer/EWG7R_eMLh5Fk-zfgpmkYpwBzw91gfNLOrnTi01XBhNJ_A?e=iCKZUi



## Getting started

To start using the Swisscom AIS service and this client library, do the following:

1. Read the most frequent asked question when doing a POC. https://github.com/SwisscomTrustServices/AIS-Postman-Samples/blob/main/docs/Q-%26-A-all-topics-English.md
3. [Get authentication details to use with the AIS client](docs/get-authentication-details.md).   
4. [Build or download the AIS client binary package](docs/build-or-download.md)
5. [Configure the AIS client for your use case](docs/configure-the-AIS-client.md)
6. Use the AIS client, either [programmatically](docs/use-the-AIS-client-programmatically.md) or from the [command line](docs/use-the-AIS-client-via-CLI.md)

Other topics of interest might be:
* [On PAdES Long Term Validation support](docs/pades-long-term-validation.md)

## Quick examples

The rest of this page provides some quick examples for using the AIS client. Please see the links
above for detailed instructions on how to get authentication data, download and configure
the AIS client. The following snippets assume that you are already set up.

### Command line usage
Get a help listing by calling the client without any parameters:
```shell
./bin/ais-client.sh
```
or
```shell
./bin/ais-client.sh -help
```
Get a default configuration file set in the current folder using the _-init_ parameter:
```shell
./bin/ais-client.sh -init
```
Apply an On Demand signature with Step Up on a local PDF file:
```shell
./bin/ais-client.sh -type ondemand-stepup -input local-sample-doc.pdf -output test-sign.pdf
```
You can also add the following parameters for extra help:

- _-v_: verbose log output (sets most of the client loggers to debug)
- _-vv_: even more verbose log output (sets all the client loggers to debug, plus the Apache HTTP Client to debug, showing input and output HTTP traffic)
- _-config_: select a custom properties file for configuration (by default it looks for the one named _config.properties_)

More than one file can be signed/timestamped at once:
```shell
./bin/ais-client.sh -type ondemand-stepup -input doc1.pdf -input doc2.pdf -input doc3.pdf
```

You don't have to specify the output file:
```shell
./bin/ais-client.sh -type ondemand-stepup -input doc1.pdf
```
The output file name is composed from the input file name plus a configurable _suffix_ (by default it is "-signed-#time", where _#time_
is replaced at runtime with the current date and time). You can customize this suffix:
```shell
./bin/ais-client.sh -type ondemand-stepup -input doc1.pdf -suffix -output-#time 
```

### Programmatic usage
Once you add the AIS client library as a dependency to your project, you can configure it in the following way:
```java
    // configuration for the REST client; this is done once per application lifetime
    RestClientConfiguration restConfig = new RestClientConfiguration();
    restConfig.setRestServiceSignUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/sign");
    restConfig.setRestServicePendingUrl("https://ais.swisscom.com/AIS-Server/rs/v1.0/pending");
    restConfig.setServerCertificateFile("/home/user/ais-server.crt");
    restConfig.setClientKeyFile("/home/user/ais-client.key");
    restConfig.setClientKeyPassword("secret");
    restConfig.setClientCertificateFile("/home/user/ais-client.crt");

    RestClientImpl restClient = new RestClientImpl();
    restClient.setConfiguration(restConfig);

    // load the AIS client config; this is done once per application lifetime
    AisClientConfiguration aisConfig = new AisClientConfiguration();
    aisConfig.setSignaturePollingIntervalInSeconds(10);
    aisConfig.setSignaturePollingRounds(10);

    try (AisClientImpl aisClient = new AisClientImpl(aisConfig, restClient)) {
        // third, configure a UserData instance with details about this signature
        // this is done for each signature (can also be created once and cached on a per-user basis)
        UserData userData = new UserData();
        userData.setClaimedIdentityName("ais-90days-trial");
        userData.setClaimedIdentityKey("keyEntity");
        userData.setDistinguishedName("cn=TEST User, givenname=Max, surname=Maximus, c=US, serialnumber=abcdefabcdefabcdefabcdefabcdef");

        userData.setStepUpLanguage("en");
        userData.setStepUpMessage("Please confirm the signing of the document");
        userData.setStepUpMsisdn("40799999999");

        userData.setSignatureReason("For testing purposes");
        userData.setSignatureLocation("Topeka, Kansas");
        userData.setSignatureContactInfo("test@test.com");

        userData.setAddRevocationInformation(RevocationInformation.PADES);
        userData.setSignatureStandard(SignatureStandard.PADES);

        userData.setConsentUrlCallback((consentUrl, userData1) -> System.out.println("Consent URL: " + consentUrl));

        // fourth, populate a PdfHandle with details about the document to be signed. More than one PdfHandle can be given
        PdfHandle document = new PdfHandle();
        document.setInputFromFile("/home/user/input.pdf");
        document.setOutputToFile("/home/user/signed-output.pdf");

        // finally, do the signature
        SignatureResult result = aisClient.signWithOnDemandCertificateAndStepUp(Collections.singletonList(document), userData);
        if (result == SignatureResult.SUCCESS) {
            // yay!
        }
    }
```

## References

- [Swisscom All-In Signing Service homepage](https://www.swisscom.ch/en/business/enterprise/offer/security/all-in-signing-service.html)
- [Swisscom All-In Signing Service reference documentation (PDF)](http://documents.swisscom.com/product/1000255-Digital_Signing_Service/Documents/Reference_Guide/Reference_Guide-All-in-Signing-Service-en.pdf)
- [Swisscom Trust Services documentation](https://trustservices.swisscom.com/en/downloads/)
- [Apache PDFBox library](https://pdfbox.apache.org/)
