/**
 * Copyright (C) 2015 The Gravitee team (http://gravitee.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.gravitee.policy.mock.swagger;

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
import io.swagger.v3.oas.models.media.ArraySchema;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.responses.ApiResponse;

import java.util.*;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MockOAIOperationVisitor implements OAIOperationVisitor {

    private final ObjectMapper mapper  = new ObjectMapper();

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
            final io.swagger.v3.oas.models.media.MediaType mediaType =
                    responseEntry.getValue().getContent().entrySet().iterator().next().getValue();
            if (mediaType.getExample() != null) {
                configuration.setResponse(mapper.convertValue(mediaType.getExample(), Map.class));
            } else if (mediaType.getExamples() != null) {
                final Map.Entry<String, Example> next = mediaType.getExamples().entrySet().iterator().next();
                configuration.setResponse(singletonMap(next.getKey(), next.getValue().getValue()));
            } else {
                final Schema responseSchema = mediaType.getSchema();
                if (responseSchema != null) {
                    if (responseSchema instanceof ArraySchema) {
                        final ArraySchema arraySchema = (ArraySchema) responseSchema;
                        processResponseSchema(oai, configuration, "array", arraySchema.getItems());
                    } else {
                        processResponseSchema(oai, configuration, responseSchema.getType() == null ? "object" :
                                responseSchema.getType(), responseSchema);
                    }
                }
            }
        }

        try {
            Policy policy = new Policy();
            policy.setName("mock");
            if (configuration.getResponse() != null) {
                configuration.setContent(mapper.writeValueAsString(configuration.isArray() ?
                        singletonList(configuration.getResponse()) : configuration.getResponse()));
            }
            policy.setConfiguration(mapper.writeValueAsString(configuration));
            return Optional.of(policy);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }

        return Optional.empty();
    }

    private void processResponseSchema(OpenAPI oai, Configuration configuration, String type, Schema responseSchema) {
        if (responseSchema.getProperties() == null) {
            configuration.setArray("array".equals(type));
            if (responseSchema.getAdditionalProperties() != null) {
                configuration.setResponse(Collections.singletonMap("additionalProperty", ((ObjectSchema) responseSchema.getAdditionalProperties()).getType()));
            } else if (responseSchema.get$ref() != null) {
                if (!"array".equals(type)) {
                    configuration.setArray(isRefArray(oai, responseSchema.get$ref()));
                }
                configuration.setResponse(getResponseFromSimpleRef(oai, responseSchema.get$ref()));
            } else {
                configuration.setResponse(singletonMap(responseSchema.getType(), getResponsePropertiesFromType(responseSchema.getType())));
            }
        } else {
            configuration.setResponse(getResponseProperties(oai, responseSchema.getProperties()));
        }
    }

    private boolean isRefArray(OpenAPI oai, final String ref) {
        final String simpleRef = ref.substring(ref.lastIndexOf('/') + 1);
        final Schema schema = oai.getComponents().getSchemas().get(simpleRef);
        return schema instanceof ArraySchema;
    }

    private Object getResponsePropertiesFromType(final String responseType) {
        if (responseType == null) {
            return null;
        }
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
            case "array":
                return singletonList(getResponsePropertiesFromType("string"));
            default:
                return emptyMap();
        }
    }

    private Object getResponseFromSimpleRef(OpenAPI oai, String ref) {
        if (ref == null){
            return null;
        }
        final String simpleRef = ref.substring(ref.lastIndexOf('/') + 1);
        final Schema schema = oai.getComponents().getSchemas().get(simpleRef);
        return getSchemaValue(oai, schema);
    }

    private Map<String, Object> getResponseProperties(final OpenAPI oai, final Map<String, Schema> properties) {
        if (properties == null) {
            return null;
        }
        return properties.entrySet()
                .stream()
                .collect(
                        toMap(
                                Map.Entry::getKey,
                                e -> this.getSchemaValue(oai, e.getValue())));
    }

    private Object getSchemaValue(final OpenAPI oai, Schema schema) {
        if (schema == null) {
            return null;
        }

        final Object example = schema.getExample();
        if (example != null) {
            return example;
        }

        final List enums = schema.getEnum();
        if (enums != null) {
            return enums.get(0);
        }

        if (schema instanceof ObjectSchema) {
            return getResponseProperties(oai, schema.getProperties());
        }

        if (schema instanceof ArraySchema) {
            Schema<?> items = ((ArraySchema) schema).getItems();
            Object sample = items.getExample();
            if (sample != null) {
                return singletonList(sample);
            }

            if (items.getEnum() != null) {
                return singletonList(items.getEnum().get(0));
            }

            if (items.get$ref() != null) {
                return getResponseFromSimpleRef(oai, items.get$ref());
            }

            return singleton(getResponsePropertiesFromType(items.getType()));
        }

        if (schema instanceof ComposedSchema) {
            final Map<String, Object> response = new HashMap<>();
            ((ComposedSchema) schema).getAllOf().forEach(composedSchema -> {
                if (composedSchema.get$ref() != null) {
                    Object responseFromSimpleRef = getResponseFromSimpleRef(oai, composedSchema.get$ref());
                    if (responseFromSimpleRef instanceof Map) {
                        response.putAll((Map) responseFromSimpleRef);
                    }
                }
                if (composedSchema.getProperties() != null) {
                    response.putAll(getResponseProperties(oai, composedSchema.getProperties()));
                }
            });
            return response;
        }

        if (schema.getProperties() != null) {
            return getResponseProperties(oai, schema.getProperties());
        }

        if (schema.get$ref() != null) {
            return getResponseFromSimpleRef(oai, schema.get$ref());
        }

        return getResponsePropertiesFromType(schema.getType());
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

        public boolean isArray() {
            return array;
        }

        public void setArray(boolean array) {
            this.array = array;
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
