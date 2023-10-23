package com.swisscom.ais.client;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.swisscom.ais.client.rest.model.etsi.ETSISignResponse;
import com.swisscom.ais.client.rest.model.etsi.auth.TokenResponse;
import com.swisscom.ais.client.rest.model.signresp.AISSignResponse;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.pdfbox.io.IOUtils;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Paths;

import static org.apache.hc.core5.http.ContentType.APPLICATION_JSON;

public class TestServlet {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static class Etsi {

        public static class Success extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/etsi/token":
                        jsonResponseCreated(response, "etsi-token-success.json", TokenResponse.class);
                        return;
                    case "/etsi/sign":
                        jsonResponseOk(response, "etsi-sign-success.json", ETSISignResponse.class);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }

        public static class TokenError extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/etsi/token":
                        jsonResponseError(response);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }

        public static class SignError extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/etsi/token":
                        jsonResponseCreated(response, "etsi-token-success.json", TokenResponse.class);
                        return;
                    case "/etsi/sign":
                        jsonResponseError(response);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }

        public static class MultipleSuccess extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/etsi/token":
                        jsonResponseCreated(response, "etsi-token-success.json", TokenResponse.class);
                        return;
                    case "/etsi/sign":
                        jsonResponseOk(response, "etsi-sign-multiple-success.json", ETSISignResponse.class);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }
    }

    public static class Dss {

        public static class Success extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-success.json";
            }
        }

        public static class ConnectionError extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/dss/sign":
                        jsonResponseError(response);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }

        public static class SignError extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error.json";
            }
        }

        public static class SignErrorSubsystemStepUpTimeout extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-step-up-timeout.json";
            }
        }

        public static class SignErrorSubsystemSerialNumberMismatch extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-serial-number-mismatch.json";
            }
        }

        public static class SignErrorSubsystemStepUpCancel extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-step-up-cancel.json";
            }
        }

        public static class SignErrorSubsystemInsufficientDataMissingMsisdn extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-insufficient-data-missing-msisdn.json";
            }
        }

        public static class SignErrorSubsystemServiceErrorInvalidPassword extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-service-error-invalid-password.json";
            }
        }

        public static class SignErrorSubsystemServiceErrorInvalidOtp extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-error-subsystem-service-error-invalid-otp.json";
            }
        }

        public static class MultipleSuccess extends DssSignOkResponseServlet {
            String dataFileName() {
                return "ais-sign-multiple-docs-success.json";
            }
        }

        public static class MultiplePending extends HttpServlet {

            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/dss/sign":
                        jsonResponseOk(response, "ais-sign-multiple-docs-pending.json", AISSignResponse.class);
                        return;
                    case "/dss/pending":
                        jsonResponseOk(response, "ais-sign-multiple-docs-success.json", AISSignResponse.class);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }

        abstract static class DssSignOkResponseServlet extends HttpServlet {

            abstract String dataFileName();

            protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
                final String url = request.getRequestURI();
                switch (url) {
                    case "/dss/sign":
                        jsonResponseOk(response, dataFileName(), AISSignResponse.class);
                        return;
                    default:
                        throw new RuntimeException(
                            String.format("No mock has been defined for url '%s'", url)
                        );
                }
            }
        }
    }

    private static void jsonResponseOk(final HttpServletResponse response,
                                       final String responsePayloadFile,
                                       final Class responseClass) throws IOException {
        jsonResponse(response, HttpServletResponse.SC_OK, responsePayloadFile, responseClass);
    }

    private static void jsonResponseCreated(final HttpServletResponse response,
                                            final String responsePayloadFile,
                                            final Class responseClass) throws IOException {
        jsonResponse(response, HttpServletResponse.SC_CREATED, responsePayloadFile, responseClass);
    }

    private static void jsonResponseError(final HttpServletResponse response) {
        response.setContentType(APPLICATION_JSON.toString());
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
    }

    private static void jsonResponse(final HttpServletResponse response,
                                     final int status,
                                     final String responsePayloadFile,
                                     final Class responseClass) throws IOException {
        response.setContentType(APPLICATION_JSON.toString());
        response.setStatus(status);
        response.getWriter().println(
            mapper.writer().writeValueAsString(new ObjectMapper().readValue(
                IOUtils.toByteArray(new FileInputStream(
                    Paths.get("src/test/resources/data/" + responsePayloadFile).toAbsolutePath().toString()
                )),
                responseClass
            ))
        );
    }
}
