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
package io.gravitee.policy.mock.v3;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.gravitee.policy.mock.swagger.MockOAIOperationVisitor;
import io.swagger.parser.OpenAPIParser;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.parser.core.models.ParseOptions;
import java.io.IOException;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

public class MockOAIOperationVisitorTest {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    private final MockOAIOperationVisitor cut = new MockOAIOperationVisitor();

    @ParameterizedTest
    @CsvSource(
        value = {
            "openapi/array-response.json, /resources, openapi/array-response-expected.json",
            "openapi/simple-response.json, /resource, openapi/simple-response-expected.json",
        }
    )
    public void shouldGenerateMock(String pathToOpenAPI, String resourceName, String pathToExpectedConfiguration) throws IOException {
        ParseOptions options = new ParseOptions();
        options.setResolveFully(true);

        OpenAPI openAPI = new OpenAPIParser().readLocation("src/test/resources/" + pathToOpenAPI, null, options).getOpenAPI();

        Operation getResourceOperation = openAPI.getPaths().get(resourceName).getGet();

        Optional<Policy> mockPolicy = cut.visit(openAPI, getResourceOperation);

        assertThat(mockPolicy).isPresent();
        Policy mockPolicyValue = mockPolicy.get();

        MockPolicyConfiguration expectedConfiguration = mapper.readValue(
            this.getClass().getClassLoader().getResourceAsStream(pathToExpectedConfiguration),
            MockPolicyConfiguration.class
        );
        assertThat(mockPolicyValue.getConfiguration()).isEqualTo(mapper.writeValueAsString(expectedConfiguration));
    }
}
