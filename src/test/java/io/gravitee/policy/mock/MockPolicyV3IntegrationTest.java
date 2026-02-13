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
import io.gravitee.definition.model.ExecutionMode;
import io.vertx.rxjava3.core.http.HttpClient;
import org.junit.jupiter.api.Test;

@GatewayTest(v2ExecutionMode = ExecutionMode.V3)
class MockPolicyV3IntegrationTest extends AbstractMockPolicyIntegrationTest {

    @Test
    @DeployApi("/apis/v2/mock-v2.json")
    void should_use_mock_endpoint(HttpClient client) {
        assertMockResponseWithElHeadersAndBody("/v2-mock", client);
    }

    @Test
    @DeployApi("/apis/v2/mock-v2-empty-content.json")
    void should_have_empty_body_and_no_content_type_when_content_is_empty(HttpClient client) {
        assertEmptyContentNoContentType("/v2-mock-empty-content", client);
    }

    @Test
    @DeployApi("/apis/v2/mock-v2-content-type-predefined.json")
    void should_not_override_predefined_content_type(HttpClient client) {
        assertContentTypeNotOverridden("/v2-mock-content-type-predefined", client);
    }

    @Test
    @DeployApi("/apis/v2/mock-v2-el-returns-null.json")
    void should_return_500_when_el_returns_null(HttpClient client) {
        assertElReturnsNullGives500("/v2-mock-el-returns-null", client);
    }

    @Test
    @DeployApi("/apis/v2/mock-v2-el-syntax-error.json")
    void should_return_500_when_el_has_syntax_error(HttpClient client) {
        assertElSyntaxErrorGives500("/v2-mock-el-syntax-error", client);
    }

    @Test
    @DeployApi("/apis/v2/mock-v2-header-el-error.json")
    void should_ignore_header_with_el_error_silently(HttpClient client) {
        assertHeaderElErrorIgnoredSilently("/v2-mock-header-el-error", client);
    }
}
