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
package io.gravitee.policy.mock.swagger;

import static java.util.Collections.*;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.gravitee.common.http.HttpHeaders;
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.policy.api.swagger.Policy;
import io.gravitee.policy.api.swagger.v3.OAIOperationVisitor;
import io.gravitee.policy.mock.configuration.HttpHeader;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.util.*;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MockOAIOperationVisitor implements OAIOperationVisitor {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Optional<Policy> visit(OpenAPI oai, Operation operation) {
        Configuration configuration = new Configuration();

        final Map.Entry<String, ApiResponse> responseEntry = operation.getResponses().entrySet().iterator().next();

        // Set response status
        try {
            configuration.setStatus(Integer.parseInt(responseEntry.getKey()));
        } catch (NumberFormatException nfe) {
            // Fallback to 2xx
            configuration.setStatus(HttpStatusCode.OK_200);
        }

        // Set default headers
        configuration.setHeaders(Collections.singletonList(new HttpHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)));

        if (responseEntry.getValue().getContent() != null) {
            final Iterator<Map.Entry<String, io.swagger.v3.oas.models.media.MediaType>> iterator = responseEntry
                .getValue()
                .getContent()
                .entrySet()
                .iterator();
            if (iterator.hasNext()) {
                final io.swagger.v3.oas.models.media.MediaType mediaType = iterator.next().getValue();

                if (mediaType.getExample() != null) {
                    configuration.setResponse(mapper.convertValue(mediaType.getExample(), Map.class));
                } else if (mediaType.getExamples() != null) {
                    final Map.Entry<String, Example> next = mediaType.getExamples().entrySet().iterator().next();
                    configuration.setResponse(singletonMap(next.getKey(), next.getValue().getValue()));
                } else {
                    final Schema responseSchema = mediaType.getSchema();

                    if (responseSchema != null) {
                        Object obj = extractExampleFromSchema(oai, responseSchema);

                        if (obj != null) {
                            configuration.setResponse(obj);
                        }
                    }
                }
            }
        }

        try {
            Policy policy = new Policy();
            policy.setName("mock");
            if (configuration.getResponse() != null) {
                configuration.setContent(mapper.writeValueAsString(configuration.getResponse()));
            }
            policy.setConfiguration(mapper.writeValueAsString(configuration));
            return Optional.of(policy);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private Object extractExampleFromSchema(OpenAPI oai, Schema schema) {
        if (schema.getExamples() != null) {
            return schema.getExamples().get(0);
        }
        if (schema.getExample() != null) {
            return schema.getExample();
        }

        if (schema.getEnum() != null) {
            return schema.getEnum().get(0);
        }

        if (schema.getTypes() != null && schema.getTypes().contains("array") && schema.getItems() != null) {
            return List.of(extractExampleFromSchema(oai, schema.getItems()));
        }

        if (schema.getTypes() != null) {
            var validTypes = List.of("string", "boolean", "integer", "number");
            final Set<String> schemaTypes = schema.getTypes();
            String responseType = schemaTypes.stream().filter(t -> validTypes.contains(t)).findFirst().orElse(null);
            if (responseType != null) {
                final Random random = new Random();
                switch (responseType) {
                    case "string":
                        return "Mocked string";
                    case "boolean":
                        return random.nextBoolean();
                    case "integer":
                        return random.nextInt(1000);
                    case "number":
                        return random.nextDouble();
                }
            }
        }
        Map<String, Object> objectResult = new HashMap<>();

        if (schema.get$ref() != null) {
            Schema refSchema = getSchema$Ref(oai, schema.get$ref());
            Object refObject = extractExampleFromSchema(oai, refSchema);
            if (refObject != null) {
                if (refSchema.getTypes() != null && refSchema.getTypes().contains("object")) {
                    objectResult.putAll((Map<String, Object>) refObject);
                } else {
                    return refObject;
                }
            }
        }

        if (schema.getProperties() != null) {
            final Map<String, Schema> schemaProperties = schema.getProperties();
            schemaProperties.forEach((key, value) -> {
                Object subSchema = extractExampleFromSchema(oai, value);
                if (subSchema != null) {
                    objectResult.put(key, subSchema);
                }
            });
        }
        if (schema.getAdditionalProperties() != null) {
            if (schema.getAdditionalProperties() instanceof Schema additionalProperties) {
                // If the additional property is an empty object, we consider it as a string
                if (additionalProperties.getTypes() == null || additionalProperties.getTypes().isEmpty()) {
                    additionalProperties.setTypes(Set.of("string"));
                }

                objectResult.put("additionalProperty", extractExampleFromSchema(oai, additionalProperties));
            } else {
                // For 3.0.0, additionalProperties can be a boolean, we consider it as a string
                Schema stringSchema = new Schema<>();
                stringSchema.setTypes(Set.of("string"));
                objectResult.put("additionalProperty", extractExampleFromSchema(oai, stringSchema));
            }
        }

        if (schema.getAllOf() != null) {
            final List<Schema> allOfSchema = schema.getAllOf();
            allOfSchema.forEach(subSchema -> {
                Object subSchemaExample = extractExampleFromSchema(oai, subSchema);
                if (subSchemaExample != null) {
                    objectResult.putAll((Map<String, Object>) subSchemaExample);
                }
            });
        }

        return objectResult.isEmpty() ? null : objectResult;
    }

    private Schema getSchema$Ref(OpenAPI oai, String ref) {
        if (ref == null || oai.getComponents() == null) {
            return null;
        }
        final String simpleRef = ref.substring(ref.lastIndexOf('/') + 1);
        return oai.getComponents().getSchemas().get(simpleRef);
    }

    private class Configuration {

        private int status;

        private List<HttpHeader> headers = new ArrayList<>();

        private String content;

        @JsonIgnore
        private Object response;

        @JsonIgnore
        private boolean array;

        public Object getResponse() {
            return response;
        }

        public void setResponse(Object response) {
            this.response = response;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        public List<HttpHeader> getHeaders() {
            return headers;
        }

        public void setHeaders(List<HttpHeader> headers) {
            this.headers = headers;
        }

        public int getStatus() {
            return status;
        }

        public void setStatus(int status) {
            this.status = status;
        }
    }
}
