package com.swisscom.ais.client.rest;

import com.swisscom.ais.client.CoreValues;
import com.swisscom.ais.client.SignatureConfig;
import com.swisscom.ais.client.rest.model.pendingreq.AISPendingRequest;
import com.swisscom.ais.client.rest.model.pendingreq.AsyncPendingRequest;
import com.swisscom.ais.client.rest.model.signreq.*;
import com.swisscom.ais.client.utils.Utils;

import java.util.Arrays;

public class ModelBuilder {

    public static AISSignRequest buildAisSignRequest(String digestAlgorithm,
                                                     String digestContent,
                                                     String signatureType) {
        // Input documents --------------------------------------------------------------------------------
        // TODO this only allows one input document to be specified
        DocumentHash documentHash = new DocumentHash();
        documentHash.setId(Utils.generateDocumentId());
        documentHash.setDsigDigestMethod(new DsigDigestMethod().withAlgorithm(digestAlgorithm));
        // TODO change this to support multiple document digest inputs
        documentHash.setDsigDigestValue(digestContent);

        InputDocuments inputDocuments = new InputDocuments();
        inputDocuments.setDocumentHash(documentHash);

        // Optional inputs --------------------------------------------------------------------------------
//        AddTimestamp addTimestamp = new AddTimestamp();
//        addTimestamp.setType(CoreValues.TIMESTAMP_TYPE_RFC_3161); // TODO

        ClaimedIdentity claimedIdentity = new ClaimedIdentity();
        claimedIdentity.setName("ais-90days-trial-withRAservice");

//        ScPhone phone = new ScPhone();
//        phone.setScLanguage(config.getPromptLanguage());
//        phone.setScMSISDN(config.getPromptMsisdn());
//        phone.setScMessage(config.getPromptMessage());

//        ScStepUpAuthorisation stepUpAuthorisation = new ScStepUpAuthorisation();
//        stepUpAuthorisation.setScPhone(phone);

//        ScCertificateRequest certificateRequest = new ScCertificateRequest();
//        certificateRequest.setScDistinguishedName(config.getDistinguishedName());
//        certificateRequest.setScStepUpAuthorisation(stepUpAuthorisation);

        OptionalInputs optionalInputs = new OptionalInputs();
//        optionalInputs.setAddTimestamp(addTimestamp);
        optionalInputs.setAdditionalProfile(Arrays.asList("urn:oasis:names:tc:dss:1.0:profiles:timestamping"));
        optionalInputs.setClaimedIdentity(claimedIdentity);
        optionalInputs.setSignatureType(signatureType); // TODO
        optionalInputs.setScAddRevocationInformation(""); // TODO
//        optionalInputs.setScCertificateRequest(certificateRequest);

        // Sign request --------------------------------------------------------------------------------
        SignRequest request = new SignRequest();
        request.setRequestID(Utils.generateRequestId());
        request.setProfile(CoreValues.SWISSCOM_BASIC_PROFILE);
        request.setInputDocuments(inputDocuments);
        request.setOptionalInputs(optionalInputs);

        AISSignRequest requestWrapper = new AISSignRequest();
        requestWrapper.setSignRequest(request);
        return requestWrapper;
    }

    public static AISPendingRequest buildAisPendingRequest(SignatureConfig config, String responseId) {
        com.swisscom.ais.client.rest.model.pendingreq.ClaimedIdentity claimedIdentity =
            new com.swisscom.ais.client.rest.model.pendingreq.ClaimedIdentity();
        claimedIdentity.setName(config.getClaimedIdentityName());

        com.swisscom.ais.client.rest.model.pendingreq.OptionalInputs optionalInputs =
            new com.swisscom.ais.client.rest.model.pendingreq.OptionalInputs();
        optionalInputs.setAsyncResponseID(responseId);
        optionalInputs.setClaimedIdentity(claimedIdentity);

        AsyncPendingRequest request = new AsyncPendingRequest();
        request.setProfile(CoreValues.SWISSCOM_BASIC_PROFILE);
        request.setOptionalInputs(optionalInputs);

        AISPendingRequest requestWrapper = new AISPendingRequest();
        requestWrapper.setAsyncPendingRequest(request);
        return requestWrapper;
    }

}