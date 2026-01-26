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

import io.gravitee.apim.gateway.tests.sdk.annotations.DeployApi;
import io.gravitee.apim.gateway.tests.sdk.annotations.GatewayTest;
import io.gravitee.apim.gateway.tests.sdk.connector.EndpointBuilder;
import io.gravitee.apim.gateway.tests.sdk.connector.EntrypointBuilder;
import io.gravitee.plugin.endpoint.EndpointConnectorPlugin;
import io.gravitee.plugin.endpoint.http.proxy.HttpProxyEndpointConnectorFactory;
import io.gravitee.plugin.entrypoint.EntrypointConnectorPlugin;
import io.gravitee.plugin.entrypoint.http.proxy.HttpProxyEntrypointConnectorFactory;
import io.vertx.rxjava3.core.http.HttpClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

@GatewayTest
class MockPolicyV4IntegrationTest extends AbstractMockPolicyIntegrationTest {

    @Override
    public void configureEntrypoints(Map<String, EntrypointConnectorPlugin<?, ?>> entrypoints) {
        entrypoints.putIfAbsent("http-proxy", EntrypointBuilder.build("http-proxy", HttpProxyEntrypointConnectorFactory.class));
    }

    @Override
    public void configureEndpoints(Map<String, EndpointConnectorPlugin<?, ?>> endpoints) {
        endpoints.putIfAbsent("http-proxy", EndpointBuilder.build("http-proxy", HttpProxyEndpointConnectorFactory.class));
    }

    @Test
    @DeployApi("/apis/v4/mock-v4.json")
    void should_use_mock_endpoint(HttpClient client) {
        assertMockResponseWithElHeadersAndBody("/v4-mock", client);
    }

    @Test
    @DeployApi("/apis/v4/mock-v4-empty-content.json")
    void should_have_empty_body_and_no_content_type_when_content_is_empty(HttpClient client) {
        assertEmptyContentNoContentType("/v4-mock-empty-content", client);
    }

    @Test
    @DeployApi("/apis/v4/mock-v4-content-type-predefined.json")
    void should_not_override_predefined_content_type(HttpClient client) {
        assertContentTypeNotOverridden("/v4-mock-content-type-predefined", client);
    }

    @Test
    @DeployApi("/apis/v4/mock-v4-el-returns-null.json")
    void should_return_500_when_el_returns_null(HttpClient client) {
        assertElReturnsNullGives500("/v4-mock-el-returns-null", client);
    }

    @Test
    @DeployApi("/apis/v4/mock-v4-el-syntax-error.json")
    void should_return_500_when_el_has_syntax_error(HttpClient client) {
        assertElSyntaxErrorGives500("/v4-mock-el-syntax-error", client);
    }

    @Test
    @DeployApi("/apis/v4/mock-v4-header-el-error.json")
    void should_ignore_header_with_el_error_silently(HttpClient client) {
        assertHeaderElErrorIgnoredSilently("/v4-mock-header-el-error", client);
    }
}
