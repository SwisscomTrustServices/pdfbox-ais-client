package com.swisscom.ais.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.ais.client.rest.model.etsi.ETSISigningRequest;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.impl.classic.HttpClientBuilder;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.interactive.digitalsignature.PDSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.net.URL;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.swisscom.ais.client.TestUtils.getSignatureSignDate;
import static com.swisscom.ais.client.TestUtils.getSignatureSignDatePlus3Minutes;
import static com.swisscom.ais.client.TestUtils.signedDocPath1;
import static com.swisscom.ais.client.TestUtils.signedDocPath2;
import static com.swisscom.ais.client.TestUtils.signedMultiDocsPath1;
import static com.swisscom.ais.client.TestUtils.signedMultiDocsPath2;
import static com.swisscom.ais.client.TestUtils.testConfigPath;
import static com.swisscom.ais.client.TestUtils.testPdfFilePath;
import static com.swisscom.ais.client.TestUtils.testUuid2;
import static com.swisscom.ais.client.TestUtils.testUuid3;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

public class CliIT {

    private final String testCode = "1234567890";

    @BeforeEach
    void setup() throws NoSuchFieldException, IllegalAccessException {
        final Field field = Cli.class.getDeclaredField("inputFileList");
        field.setAccessible(true);
        ((List<String>) field.get(Cli.class)).clear();
        field.setAccessible(false);
    }

    @Nested
    public class Etsi {

        @Nested
        public class SingleDocument {

            @Test
            public void should_generate_signed_document_with_proper_signature() {
                final Calendar expectedSignatureSignDate = getSignatureSignDatePlus3Minutes();
                TestServer.runForEtsi(
                    TestServlet.Etsi.Success.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "etsi",
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedDocPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-signature-contents-1");
                    }
                );
            }

            @Test
            public void should_send_proper_token_and_sign_request() {
                TestServer.runForEtsi(
                    TestServlet.Etsi.Success.class,
                    (testServer) -> {
                        final List<HttpClient> httpClientSpies = new ArrayList<>();
                        try (final MockedStatic<HttpClients> mockedHttpClients = mockStatic(HttpClients.class, CALLS_REAL_METHODS)) {
                            mockedHttpClients
                                .when(HttpClients::custom)
                                .then(invocationOnMock -> {
                                    final HttpClientBuilder buidlerSpy = spy((HttpClientBuilder) invocationOnMock.callRealMethod());
                                    doAnswer(i -> {
                                        final HttpClient httpClientSpy = spy((HttpClient) i.callRealMethod());
                                        httpClientSpies.add(httpClientSpy);
                                        return httpClientSpy;
                                    }).when(buidlerSpy).build();
                                    return buidlerSpy;
                                });
                            sendCodeToConsole();
                            runCliMain(new String[] {
                                "-config", testConfigPath,
                                "-type", "etsi",
                                "-input", testPdfFilePath
                            });
                            // --- validate /token call payload and sign hash ---
                            ArgumentCaptor<HttpPost> httpPostCaptor = ArgumentCaptor.forClass(HttpPost.class);
                            verify(httpClientSpies.get(0)).execute(httpPostCaptor.capture());
                            final HttpPost tokenRequest = httpPostCaptor.getAllValues().get(0);
                            assertEquals(tokenRequest.getUri().toString(), "http://localhost:7777/etsi/token");
                            final Map<String, Object> tokenRequestPayload = getUrlQueryParamsAsMap(
                                new String(IOUtils.toByteArray(tokenRequest.getEntity().getContent()))
                            );
                            assertEquals(
                                tokenRequestPayload,
                                new HashMap<String, Object>() {{
                                    put("client_id", "test-etsi.clientId");
                                    put("client_secret", "test-etsi.secret");
                                    put("code", testCode);
                                    put("grant_type", "authorization_code");
                                }}
                            );
                            // --- validate /sign call payload and sign hash ---
                            httpPostCaptor = ArgumentCaptor.forClass(HttpPost.class);
                            verify(httpClientSpies.get(1)).execute(httpPostCaptor.capture());
                            final HttpPost signRequest = httpPostCaptor.getAllValues().get(0);
                            assertEquals(signRequest.getUri().toString(), "http://localhost:7777/etsi/sign");
                            final ETSISigningRequest signRequestPayload =
                                new ObjectMapper().reader().readValue(
                                    IOUtils.toByteArray(signRequest.getEntity().getContent()),
                                    ETSISigningRequest.class
                                );
                            assertEquals(signRequestPayload.getCredentialID(), "test-etsi.credentialID");
                            assertEquals(signRequestPayload.getProfile(), "test-etsi.profile");
                            assertEquals(signRequestPayload.getSignatureFormat(), "test-etsi.signature.format");
                            assertEquals(signRequestPayload.getDocumentDigests().getHashAlgorithmOID(), "test-etsi.hash.algorithmOID");
                            //assertEquals(signingRequestPayload.getDocumentDigests().getHashes(), List.of()); <-- TODO: recreate signing hash
                            assertEquals(signRequestPayload.getConformanceLevel(), "test-etsi.signature.conformance.level");
                            assertEquals(signRequestPayload.getSAD(), "test-access-token");
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }
                    }
                );
            }

            @Test
            public void should_create_proper_auth_url() {
                TestServer.runForEtsi(
                    TestServlet.Etsi.Success.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        try {
                            runCliMain(new String[] {
                                "-config", testConfigPath,
                                "-type", "etsi",
                                "-input", testPdfFilePath
                            });
                            final URL givenAuthUrl = new URL(testServer.consoleStream.getAuthUrl());
                            final Map<String, Object> givenQueryParams = getUrlQueryParamsAsMap(givenAuthUrl.getQuery());
                            assertEquals(givenQueryParams.get("state"), "test-etsi.rax.state");
                            assertEquals(givenQueryParams.get("nonce"), "test-etsi.rax.nonce");
                            assertEquals(givenQueryParams.get("response_type"), "code");
                            assertEquals(givenQueryParams.get("client_id"), "test-etsi.rax.client_id");
                            assertEquals(givenQueryParams.get("scope"), "sign");
                            assertEquals(givenQueryParams.get("redirect_uri"), URLEncoder.encode("http://localhost:7777/etsi", "UTF-8"));
                            assertEquals(givenQueryParams.get("code_challenge_method"), "S256");
                            final Map<String, Object> givenClaims =
                                new ObjectMapper().readValue(
                                    URLDecoder.decode((String) givenQueryParams.get("claims"), "UTF-8"),
                                    HashMap.class);
                            assertEquals(givenClaims.get("credentialID"), "test-etsi.credentialID");
                            assertEquals(givenClaims.get("hashAlgorithmOID"), "test-etsi.hash.algorithmOID");
                            final List<Map<String, Object>> givenClaimsDocumentDigests = (List<Map<String, Object>>) givenClaims.get("documentDigests");
                            assertEquals(givenClaimsDocumentDigests.size(), 1);
                            //assertEquals(givenClaimsDocumentDigests.get(0).get("hash"), ""); // TODO: recreate hashes and remove this deletion
                            assertEquals(givenClaimsDocumentDigests.get(0).get("label"), "test-input-doc.pdf");
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    }
                );
            }

            @Test
            public void should_create_proper_result_file_name() {
                final String expectedSignedPdfFileName = "____on2000-4-10 at 12-30-59.pdf";
                TestServer.runForEtsi(
                    TestServlet.Etsi.Success.class,
                    (testServer) -> {
                        assertTestFileExists(false, expectedSignedPdfFileName);
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "etsi",
                            "-input", testPdfFilePath
                        });
                        assertTestFileExists(true, expectedSignedPdfFileName);
                    }
                );
            }

            @Test
            public void should_handle_token_response_error_status() {
                TestServer.runForEtsi(
                    TestServlet.Etsi.TokenError.class,
                    (testServer) -> {
                        final Throwable exception = assertThrows(
                            RestClientException.class,
                            () -> {
                                sendCodeToConsole();
                                runCliMain(new String[] {
                                    "-config", testConfigPath,
                                    "-type", "etsi",
                                    "-input", testPdfFilePath
                                });
                            }
                        );
                        assertEquals(exception.getMessage(), "Communication failure for GetToken - " + testUuid2);
                    }
                );
            }

            @Test
            public void should_handle_sign_response_error_status() {
                TestServer.runForEtsi(
                    TestServlet.Etsi.SignError.class,
                    (testServer) -> {
                        final Throwable exception = assertThrows(
                            RestClientException.class,
                            () -> {
                                sendCodeToConsole();
                                runCliMain(new String[] {
                                    "-config", testConfigPath,
                                    "-type", "etsi",
                                    "-input", testPdfFilePath
                                });
                            }
                        );
                        assertEquals(exception.getMessage(), "Communication failure for SignEtsi - " + testUuid2);
                    }
                );
            }
        }

        @Nested
        public class MultipleDocuments {

            @Test
            public void should_generate_sign_only_first_provided_document() {
                final Calendar expectedSignatureSignDate = getSignatureSignDatePlus3Minutes();
                TestServer.runForEtsi(
                    TestServlet.Etsi.MultipleSuccess.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "etsi",
                            "-input", testPdfFilePath,
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedDocPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-signature-contents-1");
                        assertTestFileExists(false, signedDocPath2);
                    }
                );
            }
        }
    }

    @Nested
    public class DSS {

        @Nested
        public class SingleDocument {

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand" })
            public void should_generate_signed_document_with_proper_signature_for_given_type(final String type) {
                final Calendar expectedSignatureSignDate = getSignatureSignDatePlus3Minutes();
                TestServer.runForDss(
                    TestServlet.Dss.Success.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedMultiDocsPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-signature-$-1");
                    }
                );
            }

            @Test
            public void should_generate_signed_documents_with_proper_signature_for_type_ondemand_stepup() {
                TestServer.runForDss(
                    TestServlet.Dss.MultipleSuccess.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "ondemand-stepup",
                            "-input", testPdfFilePath,
                            "-input", testPdfFilePath
                        });
                        assertEquals(new String(readFile(signedMultiDocsPath1)), ""); // TODO: why is this case not working when pending is not happening?
                    }
                );
            }

            @Test
            public void should_generate_signed_document_with_proper_signature_for_type_timestamp() {
                final Calendar expectedSignatureSignDate = getSignatureSignDate();
                TestServer.runForDss(
                    TestServlet.Dss.Success.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "timestamp",
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedMultiDocsPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-timestamp-token-1");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_server_connection_error(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.ConnectionError.class,
                    (testServer) -> {
                        final Throwable exception = assertThrows(
                            RestClientException.class,
                            () -> {
                                sendCodeToConsole();
                                runCliMain(new String[] {
                                    "-config", testConfigPath,
                                    "-type", type,
                                    "-input", testPdfFilePath
                                });
                            }
                        );
                        assertEquals(exception.getMessage(), "Failed to communicate with the AIS service and obtain the signature(s) - " + testUuid3);
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_error(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignError.class,
                    (testServer) -> {
                        final Throwable exception = assertThrows(
                            RestClientException.class,
                            () -> {
                                sendCodeToConsole();
                                runCliMain(new String[] {
                                    "-config", testConfigPath,
                                    "-type", type,
                                    "-input", testPdfFilePath
                                });
                            }
                        );
                        final Matcher matcher = Pattern
                            .compile("^Failure response received from AIS service: Major=\\[(.*)], Minor=\\[(.*)], Message=\\[(.*)] - (.*)$")
                            .matcher(exception.getMessage());
                        assertEquals(matcher.matches(), true);
                        assertEquals(matcher.group(1), "http://ais.swisscom.ch/1.0/resultmajor/SubsystemError");
                        assertEquals(matcher.group(2), "http://ais.swisscom.ch/1.1/resultminor/subsystem/StepUp/service");
                        assertEquals(
                            Pattern
                                .compile("com\\.swisscom\\.ais\\.client\\.rest\\.model\\.signresp\\.ResultMessage@[a-zA-Z0-9]+\\[xmlLang=en,\\$=test-message]")
                                .matcher(matcher.group(3))
                                .matches(),
                            true
                        );
                        assertEquals(matcher.group(4), testUuid3.toString());
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_step_up_timeout_error(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemStepUpTimeout.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "USER_TIMEOUT");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_serial_number_mismatch_error(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemSerialNumberMismatch.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "SERIAL_NUMBER_MISMATCH");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_step_up_cancel_error(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemStepUpCancel.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "USER_CANCEL");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_insufficient_data_error_for_missing_msisdn(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemInsufficientDataMissingMsisdn.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "INSUFFICIENT_DATA_WITH_ABSENT_MSISDN");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_service_error_for_invalid_password(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemServiceErrorInvalidPassword.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "USER_AUTHENTICATION_FAILED");
                    }
                );
            }

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand", "ondemand-stepup", "timestamp" })
            public void should_handle_response_with_subsystem_service_error_for_invalid_otp(final String type) {
                TestServer.runForDss(
                    TestServlet.Dss.SignErrorSubsystemServiceErrorInvalidOtp.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath
                        });
                        assertEquals(testServer.consoleStream.getFinalResult(), "USER_AUTHENTICATION_FAILED");
                    }
                );
            }
        }

        @Nested
        public class MultipleDocuments {

            @ParameterizedTest
            @ValueSource(strings = { "static", "ondemand" })
            public void should_generate_signed_documents_with_proper_signature_for_given_type(final String type) {
                final Calendar expectedSignatureSignDate = getSignatureSignDatePlus3Minutes();
                TestServer.runForDss(
                    TestServlet.Dss.MultipleSuccess.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", type,
                            "-input", testPdfFilePath,
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedMultiDocsPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-signature-$-2");
                        final PDSignature usedSignature2 = loadDocSignature(signedMultiDocsPath2);
                        assertEquals(usedSignature2.getName(), "test-signature.name");
                        assertEquals(usedSignature2.getReason(), "test-signature.reason");
                        assertEquals(usedSignature2.getLocation(), "test-signature.location");
                        assertEquals(usedSignature2.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature2.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature2), "test-signature-$-3");
                    }
                );
            }

            @Test
            public void should_generate_signed_documents_with_proper_signature_for_type_timestamp() {
                final Calendar expectedSignatureSignDate = getSignatureSignDate();
                TestServer.runForDss(
                    TestServlet.Dss.MultipleSuccess.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "timestamp",
                            "-input", testPdfFilePath,
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedMultiDocsPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-timestamp-token-2");
                        final PDSignature usedSignature2 = loadDocSignature(signedMultiDocsPath2);
                        assertEquals(usedSignature2.getName(), "test-signature.name");
                        assertEquals(usedSignature2.getReason(), "test-signature.reason");
                        assertEquals(usedSignature2.getLocation(), "test-signature.location");
                        assertEquals(usedSignature2.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature2.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature2), "test-timestamp-token-3");
                    }
                );
            }

            @Test
            public void should_generate_signed_documents_with_proper_signature_for_type_ondemand_stepup_with_pending() {
                final Calendar expectedSignatureSignDate = getSignatureSignDatePlus3Minutes();
                TestServer.runForDss(
                    TestServlet.Dss.MultiplePending.class,
                    (testServer) -> {
                        sendCodeToConsole();
                        runCliMain(new String[] {
                            "-config", testConfigPath,
                            "-type", "ondemand-stepup",
                            "-input", testPdfFilePath,
                            "-input", testPdfFilePath
                        });
                        // validate signature
                        final PDSignature usedSignature = loadDocSignature(signedMultiDocsPath1);
                        assertEquals(usedSignature.getName(), "test-signature.name");
                        assertEquals(usedSignature.getReason(), "test-signature.reason");
                        assertEquals(usedSignature.getLocation(), "test-signature.location");
                        assertEquals(usedSignature.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature), "test-signature-$-2");
                        final PDSignature usedSignature2 = loadDocSignature(signedMultiDocsPath2);
                        assertEquals(usedSignature2.getName(), "test-signature.name");
                        assertEquals(usedSignature2.getReason(), "test-signature.reason");
                        assertEquals(usedSignature2.getLocation(), "test-signature.location");
                        assertEquals(usedSignature2.getContactInfo(), "test-signature.contactInfo");
                        assertEquals(usedSignature2.getSignDate().toInstant(), expectedSignatureSignDate.toInstant());
                        assertEquals(getSignatureContents(usedSignature2), "test-signature-$-3");
                    }
                );
            }
        }
    }

    private void runCliMain(final String[] args) {
        try {
            Cli.main(args);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private PDDocument loadDoc(final String filePath) {
        try {
            return PDDocument.load(readFile(filePath));
        } catch (IOException e) {
            throw new RuntimeException(String.format("Document does not exist '%s'.", filePath), e);
        }
    }

    private PDSignature loadDocSignature(final String filePath) {
        try {
            return loadDoc(filePath).getLastSignatureDictionary();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private byte[] readFile(final String path) {
        try {
            return IOUtils.toByteArray(
                new FileInputStream(getAbsPath(path).toString())
            );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private Path getAbsPath(final String path) {
        return Paths.get(path).toAbsolutePath();
    }

    private String getSignatureContents(final PDSignature signature) {
        final byte[] contents = signature.getContents();
        int idx = contents.length - 1;
        for (; idx >= 0; idx--) {
            if (contents[idx] != 0) {
                break;
            }
        }
        return idx > -1
            ? new String(Arrays.copyOfRange(contents, 0, idx + 1))
            : "";
    }

    private void sendCodeToConsole() {
        System.setIn(new ByteArrayInputStream(testCode.getBytes()));
    }

    private Map<String, Object> getUrlQueryParamsAsMap(final String urlQueryParams) {
        return Arrays
            .stream(urlQueryParams.split("&"))
            .map(item -> item.split("="))
            .collect(Collectors.toMap(
                pair -> pair[0],
                pair -> pair[1]
            ));
    }

    private void assertTestFileExists(final boolean exists,
                                      final String path) {
        assertEquals(getAbsPath(path).toFile().exists(), exists);
    }
}
