package com.swisscom.ais.client;

import com.swisscom.ais.client.utils.Utils;
import jakarta.servlet.http.HttpServlet;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.mockito.MockedStatic;

import java.io.File;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.Calendar;
import java.util.UUID;
import java.util.function.Consumer;

import static com.swisscom.ais.client.TestUtils.getSignatureSignDate;
import static com.swisscom.ais.client.TestUtils.testLocalDateTime1;
import static com.swisscom.ais.client.TestUtils.testLocalDateTime2;
import static com.swisscom.ais.client.TestUtils.testUuid1;
import static com.swisscom.ais.client.TestUtils.testUuid2;
import static com.swisscom.ais.client.TestUtils.testUuid3;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mockStatic;

public class TestServer<T extends HttpServlet> {

    static <T extends HttpServlet> void runForEtsi(final Class<T> servletClass,
                                                   final Consumer<TestServer> testToRun) {
        final TestServer testServer = new TestServer("/etsi/", servletClass);
        testServer.run(testToRun);
    }

    static <T extends HttpServlet> void runForDss(final Class<T> servletClass,
                                                  final Consumer<TestServer> testToRun) {
        final TestServer testServer = new TestServer("/dss/", servletClass);
        testServer.run(testToRun);
    }

    final TestUtils.TestConsoleStream consoleStream;
    final Server server;
    final Calendar signatureSignDate1;
    final Calendar signatureSignDate2;

    private TestServer(final String contextPath,
                       final Class<T> servletClass) {
        signatureSignDate1 = getSignatureSignDate();
        signatureSignDate2 = getSignatureSignDate();
        consoleStream = new TestUtils.TestConsoleStream();
        System.setOut(consoleStream);
        server = new Server();
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(7777);
        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath(contextPath);
        context.addServlet(servletClass, "/");
        server.setHandler(context);
        server.setConnectors(new Connector[]{ connector });
        try {
            server.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void run(final Consumer<TestServer> testToRun) {
        try (
            final MockedStatic<LocalDateTime> mockedLocalDateTime = mockStatic(LocalDateTime.class, CALLS_REAL_METHODS);
            final MockedStatic<Calendar> mockedCalendar = mockStatic(Calendar.class, CALLS_REAL_METHODS);
            final MockedStatic<Utils> mockedUtils = mockStatic(Utils.class, CALLS_REAL_METHODS);
            final MockedStatic<UUID> mockedUUID = mockStatic(UUID.class, CALLS_REAL_METHODS);
        ) {
            mockedUUID
                .when(UUID::randomUUID)
                .thenReturn(testUuid1, testUuid2, testUuid3);
            mockedCalendar
                .when(Calendar::getInstance)
                .thenReturn(signatureSignDate1, signatureSignDate2);
            mockedLocalDateTime
                .when(LocalDateTime::now)
                .thenReturn(testLocalDateTime1, testLocalDateTime2);
            mockedUtils
                .when(Utils::generateDocumentId)
                .thenReturn("DOC_ID#1", "DOC_ID#2");
            testToRun.accept(this);
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (server != null) {
                try {
                    server.stop();
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }
            removeTestsFilesInPath("");
            removeTestsFilesInPath("target/test-classes/");
        }
    }

    private static void removeTestsFilesInPath(final String dirPath) {
        for (final File file : Paths.get(dirPath).toAbsolutePath().toFile().listFiles()) {
            if (file.getName().startsWith("test-doc-signed-") || file.getName().startsWith("____on")) {
                file.delete();
            }
        }
    }
}
