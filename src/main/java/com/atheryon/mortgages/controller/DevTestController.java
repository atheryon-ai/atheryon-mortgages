package com.atheryon.mortgages.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.annotation.Profile;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@RestController
@RequestMapping("/api/dev/tests")
@Profile("dev")
@Tag(name = "Test Runner", description = "E2E test execution and results (dev only)")
public class DevTestController {

    private static final String TEST_PACKAGE = "com.atheryon.mortgages.integration";

    private static final List<Map<String, Object>> TEST_CATALOG = List.of(
            testEntry("SRS-1", "Product Enquiry", "ProductEnquiryE2ETest",
                    "Product browsing, filtering, and rate comparison"),
            testEntry("SRS-2", "Application Capture", "ApplicationCaptureE2ETest",
                    "Application creation, party linking, security, documents"),
            testEntry("SRS-3", "Application Submission", "ApplicationSubmissionE2ETest",
                    "Validation and submission workflow"),
            testEntry("SRS-4", "Assessment & Verification", "AssessmentVerificationE2ETest",
                    "Assessment begin, document verification, serviceability"),
            testEntry("SRS-5", "Decisioning & Approval", "DecisioningE2ETest",
                    "Automated and manual decision engine"),
            testEntry("SRS-6", "Offer & Acceptance", "OfferAcceptanceE2ETest",
                    "Offer generation, acceptance, and expiry"),
            testEntry("SRS-W", "Withdrawal", "WithdrawalE2ETest",
                    "Application withdrawal at various stages")
    );

    private static Map<String, Object> testEntry(String processId, String processName,
                                                   String className, String description) {
        return Map.of(
                "processId", processId,
                "processName", processName,
                "className", className,
                "fullClassName", TEST_PACKAGE + "." + className,
                "description", description
        );
    }

    @GetMapping
    @Operation(summary = "Get test catalog grouped by SRS process")
    public ResponseEntity<Map<String, Object>> getCatalog() {
        return ResponseEntity.ok(Map.of(
                "catalog", TEST_CATALOG,
                "totalClasses", TEST_CATALOG.size()
        ));
    }

    @PostMapping(value = "/run", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "Run selected tests and stream output via SSE")
    public SseEmitter runTests(@RequestBody(required = false) Map<String, Object> request) {
        SseEmitter emitter = new SseEmitter(600_000L); // 10 minutes

        List<String> testClasses;
        if (request != null && request.containsKey("tests")) {
            @SuppressWarnings("unchecked")
            List<String> requested = (List<String>) request.get("tests");
            testClasses = requested;
        } else {
            testClasses = TEST_CATALOG.stream()
                    .map(t -> (String) t.get("fullClassName"))
                    .toList();
        }

        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("status")
                        .data(Map.of("phase", "starting", "tests", testClasses.size())));

                List<String> command = new ArrayList<>();
                command.add("./gradlew");
                command.add("test");
                for (String tc : testClasses) {
                    command.add("--tests");
                    command.add(tc);
                }

                ProcessBuilder pb = new ProcessBuilder(command);
                pb.directory(new File(System.getProperty("user.dir")));
                pb.redirectErrorStream(true);
                pb.environment().put("TERM", "dumb");

                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        try {
                            emitter.send(SseEmitter.event()
                                    .name("output")
                                    .data(line));
                        } catch (Exception e) {
                            process.destroyForcibly();
                            return;
                        }
                    }
                }

                int exitCode = process.waitFor();

                // Parse test results from XML
                Map<String, Object> results = parseTestResults();
                results.put("exitCode", exitCode);
                results.put("success", exitCode == 0);

                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data(results));
                emitter.complete();

            } catch (Exception e) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data(Map.of("message", e.getMessage())));
                    emitter.complete();
                } catch (IOException ignored) {
                    emitter.completeWithError(e);
                }
            }
        });
        executor.shutdown();

        emitter.onCompletion(executor::shutdownNow);
        emitter.onTimeout(executor::shutdownNow);

        return emitter;
    }

    @GetMapping("/results")
    @Operation(summary = "Get latest test results from Gradle XML reports")
    public ResponseEntity<Map<String, Object>> getResults() {
        return ResponseEntity.ok(parseTestResults());
    }

    private Map<String, Object> parseTestResults() {
        Map<String, Object> results = new LinkedHashMap<>();
        List<Map<String, Object>> suites = new ArrayList<>();
        int totalTests = 0, totalPassed = 0, totalFailed = 0, totalErrors = 0;
        double totalTime = 0;

        Path resultsDir = Paths.get(System.getProperty("user.dir"), "build", "test-results", "test");
        if (!Files.exists(resultsDir)) {
            results.put("message", "No test results found. Run tests first.");
            results.put("suites", suites);
            return results;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(resultsDir, "TEST-*.xml")) {
            var factory = DocumentBuilderFactory.newInstance();
            for (Path xmlFile : stream) {
                try {
                    var doc = factory.newDocumentBuilder().parse(xmlFile.toFile());
                    var testSuite = doc.getDocumentElement();

                    String suiteName = testSuite.getAttribute("name");
                    int tests = Integer.parseInt(testSuite.getAttribute("tests"));
                    int failures = Integer.parseInt(testSuite.getAttribute("failures"));
                    int errors = Integer.parseInt(testSuite.getAttribute("errors"));
                    double time = Double.parseDouble(testSuite.getAttribute("time"));

                    totalTests += tests;
                    totalFailed += failures;
                    totalErrors += errors;
                    totalPassed += (tests - failures - errors);
                    totalTime += time;

                    List<Map<String, Object>> testCases = new ArrayList<>();
                    var tcNodes = doc.getElementsByTagName("testcase");
                    for (int i = 0; i < tcNodes.getLength(); i++) {
                        var tc = tcNodes.item(i);
                        Map<String, Object> testCase = new LinkedHashMap<>();
                        testCase.put("name", tc.getAttributes().getNamedItem("name").getNodeValue());
                        testCase.put("time", tc.getAttributes().getNamedItem("time").getNodeValue());

                        boolean hasFail = false;
                        var children = tc.getChildNodes();
                        for (int j = 0; j < children.getLength(); j++) {
                            var child = children.item(j);
                            if ("failure".equals(child.getNodeName())) {
                                hasFail = true;
                                testCase.put("status", "FAILED");
                                testCase.put("failureMessage",
                                        child.getAttributes().getNamedItem("message") != null
                                                ? child.getAttributes().getNamedItem("message").getNodeValue()
                                                : child.getTextContent().substring(0, Math.min(500, child.getTextContent().length())));
                            } else if ("error".equals(child.getNodeName())) {
                                hasFail = true;
                                testCase.put("status", "ERROR");
                                testCase.put("errorMessage",
                                        child.getAttributes().getNamedItem("message") != null
                                                ? child.getAttributes().getNamedItem("message").getNodeValue()
                                                : "Unknown error");
                            }
                        }
                        if (!hasFail) {
                            testCase.put("status", "PASSED");
                        }
                        testCases.add(testCase);
                    }

                    // Map class name to SRS process
                    String shortName = suiteName.substring(suiteName.lastIndexOf('.') + 1);
                    String processId = TEST_CATALOG.stream()
                            .filter(t -> shortName.equals(t.get("className")))
                            .map(t -> (String) t.get("processId"))
                            .findFirst().orElse("?");

                    suites.add(Map.of(
                            "name", suiteName,
                            "shortName", shortName,
                            "processId", processId,
                            "tests", tests,
                            "failures", failures,
                            "errors", errors,
                            "passed", tests - failures - errors,
                            "time", String.format("%.3fs", time),
                            "testCases", testCases
                    ));
                } catch (Exception e) {
                    // Skip unparseable files
                }
            }
        } catch (IOException e) {
            results.put("error", "Failed to read test results: " + e.getMessage());
        }

        suites.sort(Comparator.comparing(s -> (String) s.get("processId")));

        results.put("summary", Map.of(
                "totalTests", totalTests,
                "passed", totalPassed,
                "failed", totalFailed,
                "errors", totalErrors,
                "totalTime", String.format("%.3fs", totalTime)
        ));
        results.put("suites", suites);
        return results;
    }
}
