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
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.ok;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import io.gravitee.apim.gateway.tests.sdk.AbstractPolicyTest;
import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.policy.mock.MockPolicy;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.reactivex.rxjava3.observers.TestObserver;
import io.vertx.core.http.HttpMethod;
import io.vertx.rxjava3.core.buffer.Buffer;
import io.vertx.rxjava3.core.http.HttpClient;
import io.vertx.rxjava3.core.http.HttpClientRequest;
import lombok.SneakyThrows;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * @author Yann TAVERNIER (yann.tavernier at graviteesource.com)
 * @author GraviteeSource Team
 */
@GatewayTest
@DeployApi("/apis/mock.json")
class MockPolicyIntegrationTest extends AbstractPolicyTest<MockPolicy, MockPolicyConfiguration> {

    @Test
    @DisplayName("Should use mock without calling endpoint")
    @SneakyThrows
    void shouldUseMock(HttpClient client) {
        wiremock.stubFor(get("/endpoint").willReturn(ok("response from backend")));
        final TestObserver<Buffer> obs = client
            .rxRequest(HttpMethod.GET, "/test")
            .map(req -> req.putHeader("reqHeader", "reqHeaderValue"))
            .flatMap(HttpClientRequest::rxSend)
            .flatMap(response -> {
                assertThat(response.statusCode()).isEqualTo(400);
                assertThat(response.headers().contains("X-Mock-Policy")).isTrue();
                assertThat(response.headers().get("X-Mock-Policy")).isEqualTo("Passed through mock policy");
                assertThat(response.headers().contains("X-Mock-Policy-Second")).isTrue();
                assertThat(response.headers().get("X-Mock-Policy-Second")).isEqualTo("reqHeaderValue");
                return response.body();
            })
            .test();

        awaitTerminalEvent(obs);
        obs
            .assertComplete()
            .assertValue(body -> {
                assertThat(body).hasToString("mockContent");
                return true;
            })
            .assertNoErrors();

        wiremock.verify(0, getRequestedFor(urlPathEqualTo("/endpoint")));
    }
}
