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
package io.gravitee.policy.mock;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.DisplayNameGeneration;
import org.junit.jupiter.api.DisplayNameGenerator;

@DisplayNameGeneration(DisplayNameGenerator.ReplaceUnderscores.class)
abstract class AbstractMockPolicyIntegrationTest extends AbstractPolicyTest<MockPolicy, MockPolicyConfiguration> {

    protected void assertMockResponseWithElHeadersAndBody(String path, HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend").withHeader("fakeHeader", "fakeValue")));

        client
            .request(HttpMethod.GET, path)
            .flatMap(request -> request.putHeader("reqHeader", "reqHeaderValue").rxSend())
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.headers().contains("X-Mock-Policy")).isTrue();
                assertThat(response.headers().get("X-Mock-Policy")).isEqualTo("Passed through mock policy");
                assertThat(response.headers().contains("X-Mock-Policy-Second")).isTrue();
                assertThat(response.headers().get("X-Mock-Policy-Second")).isEqualTo("reqHeaderValue");
                assertThat(response.headers().contains("fakeHeader")).isFalse();
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body).hasToString("path=" + path);
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }

    protected void assertEmptyContentNoContentType(String path, HttpClient client) {
        client
            .request(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(204);
                assertThat(response.headers().contains("Content-Type")).isFalse();
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body).hasToString("");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }

    protected void assertContentTypeNotOverridden(String path, HttpClient client) {
        client
            .request(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                assertThat(response.headers().get("Content-Type")).isEqualTo("application/custom+json");
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body).hasToString("{\"message\":\"hello\"}");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }

    protected void assertElReturnsNullGives500(String path, HttpClient client) {
        client
            .request(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body.toString()).isNotEmpty();
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }

    protected void assertElSyntaxErrorGives500(String path, HttpClient client) {
        client
            .request(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(500);
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body.toString()).isNotEmpty();
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }

    protected void assertHeaderElErrorIgnoredSilently(String path, HttpClient client) {
        client
            .request(HttpMethod.GET, path)
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(200);
                // Valid header should be present
                assertThat(response.headers().get("X-Valid-Header")).isEqualTo("valid-value");
                // Invalid EL header should be silently ignored (not present)
                assertThat(response.headers().contains("X-Invalid-EL-Header")).isFalse();
                return response.rxBody();
            })
            .test()
            .awaitDone(30, TimeUnit.SECONDS)
            .assertComplete()
            .assertValue(body -> {
                assertThat(body).hasToString("{\"status\":\"ok\"}");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(anyUrl()));
    }
}
