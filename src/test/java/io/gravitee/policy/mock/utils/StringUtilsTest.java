/*
 * Copyright © 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.mock.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
class StringUtilsTest {

    private HttpServer server;
    private final AtomicInteger requests = new AtomicInteger();

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/", exchange -> {
            requests.incrementAndGet();
            byte[] body = "<!ENTITY y \"z\">".getBytes();
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private String serverUrl(String path) {
        return "http://" + server.getAddress().getHostString() + ":" + server.getAddress().getPort() + path;
    }

    @Test
    void should_not_resolve_external_general_entity() {
        String content = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY x SYSTEM \"" + serverUrl("/general") + "\">]><r>&x;</r>";

        boolean result = StringUtils.isXML(content);

        assertThat(requests).hasValue(0);
        assertThat(result).isFalse();
    }

    @Test
    void should_not_resolve_external_parameter_entity() {
        String content = "<?xml version=\"1.0\"?><!DOCTYPE r [<!ENTITY % p SYSTEM \"" + serverUrl("/parameter") + "\"> %p;]><r/>";

        boolean result = StringUtils.isXML(content);

        assertThat(requests).hasValue(0);
        assertThat(result).isFalse();
    }

    @Test
    void should_not_load_external_dtd() {
        String content = "<?xml version=\"1.0\"?><!DOCTYPE r SYSTEM \"" + serverUrl("/dtd") + "\"><r/>";

        boolean result = StringUtils.isXML(content);

        assertThat(requests).hasValue(0);
        assertThat(result).isFalse();
    }

    @Test
    void should_not_detect_xml_declaring_a_doctype() {
        assertThat(StringUtils.isXML("<!DOCTYPE r [<!ENTITY a \"b\">]><r>&a;</r>")).isFalse();
    }

    @ParameterizedTest
    @MethodSource("jsonTestCases")
    void should_detect_json_content(String content, boolean expectedResult) {
        boolean result = StringUtils.isJSON(content);
        assertThat(result).isEqualTo(expectedResult);
    }

    private static Stream<Arguments> jsonTestCases() {
        return Stream.of(
            // Valid JSON
            Arguments.of("{}", true),
            Arguments.of("{\"key\":\"value\"}", true),
            Arguments.of("{\"message\":\"hello\"}", true),
            Arguments.of("[]", true),
            Arguments.of("[1,2,3]", true),
            Arguments.of("[{\"a\":1},{\"b\":2}]", true),
            Arguments.of("   {\"a\":1}   ", true),
            // Invalid JSON
            Arguments.of("{\"a\":1, \"b\":2,}", false),
            Arguments.of("\"string\"", false),
            Arguments.of("123", false),
            Arguments.of("true", false),
            Arguments.of("false", false),
            Arguments.of("null", false),
            Arguments.of("{invalid}", false),
            Arguments.of("{key:value}", false),
            Arguments.of("<root></root>", false),
            Arguments.of("plain text", false),
            Arguments.of("{\"unclosed\":\"value", false),
            Arguments.of("", false),
            Arguments.of(null, false)
        );
    }

    @ParameterizedTest
    @MethodSource("xmlTestCases")
    void should_detect_xml_content(String content, boolean expectedResult) {
        boolean result = StringUtils.isXML(content);
        assertThat(result).isEqualTo(expectedResult);
    }

    private static Stream<Arguments> xmlTestCases() {
        return Stream.of(
            // Valid XML
            Arguments.of("<root><message>hello</message></root>", true),
            Arguments.of("<root attr=\"value\"><child/></root>", true),
            Arguments.of("<?xml version=\"1.0\" encoding=\"UTF-8\"?><root/>", true),
            Arguments.of("<empty/>", true),
            // Invalid XML
            Arguments.of("{\"json\":true}", false),
            Arguments.of("plain text", false),
            Arguments.of("<unclosed>", false),
            Arguments.of("<root><child></root>", false),
            Arguments.of("", false),
            Arguments.of(null, false)
        );
    }
}
