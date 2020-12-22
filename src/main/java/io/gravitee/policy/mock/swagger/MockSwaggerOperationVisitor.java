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
import io.gravitee.policy.api.swagger.v2.SwaggerOperationVisitor;
import io.gravitee.policy.mock.configuration.HttpHeader;
import io.swagger.models.*;
import io.swagger.models.properties.ObjectProperty;
import io.swagger.models.properties.Property;
import io.swagger.models.properties.RefProperty;

import java.io.IOException;
import java.util.*;

import static java.util.Collections.*;
import static java.util.stream.Collectors.toMap;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MockSwaggerOperationVisitor implements SwaggerOperationVisitor {

    private final ObjectMapper mapper = new ObjectMapper();

    {
        mapper.configure(JsonGenerator.Feature.WRITE_NUMBERS_AS_STRINGS, true);
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public Optional<Policy> visit(Swagger swagger, Operation operation) {
        Configuration configuration = new Configuration();

        final Map.Entry<String, Response> responseEntry = operation.getResponses().entrySet().iterator().next();

        // Set response status
        try {
            configuration.setStatus(Integer.parseInt(responseEntry.getKey()));
        } catch (NumberFormatException nfe) {
            // Fallback to 2xx
            configuration.setStatus(HttpStatusCode.OK_200);
        }

        // Set default headers
        configuration.setHeaders(Collections.singletonList(new HttpHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON)));

        final Model responseSchema = responseEntry.getValue().getResponseSchema();

        if (responseSchema != null) {
            if (responseSchema instanceof ArrayModel) {
                final ArrayModel arrayModel = (ArrayModel) responseSchema;
                configuration.setArray(true);
                if (arrayModel.getItems() instanceof RefProperty) {
                    final String simpleRef = ((RefProperty) arrayModel.getItems()).getSimpleRef();
                    configuration.setResponse(getResponseFromSimpleRef(swagger, simpleRef));
                } else if (arrayModel.getItems() instanceof ObjectProperty) {
                    configuration.setResponse(getResponseProperties(swagger, ((ObjectProperty) arrayModel.getItems()).getProperties()));
                }
            } else if (responseSchema instanceof RefModel) {
                final String simpleRef = ((RefModel) responseSchema).getSimpleRef();
                configuration.setResponse(getResponseFromSimpleRef(swagger, simpleRef));
            } else if (responseSchema instanceof ModelImpl) {
                final ModelImpl model = (ModelImpl) responseSchema;
                if ("array".equals(model.getType())) {
                    configuration.setArray(true);
                } else if ("object".equals(model.getType())) {
                    if (model.getProperties() != null) {
                        configuration.setResponse(getResponseProperties(swagger, model.getProperties()));
                    } else if (model.getAdditionalProperties() != null) {
                        configuration.setResponse(Collections.singletonMap("additionalProperty", model.getAdditionalProperties().getType()));
                    }
                }
            }
        } else {
            Map<String, Object> examples = responseEntry.getValue().getExamples();
            if (examples != null) {
                Object jsonExample = examples.get(MediaType.APPLICATION_JSON);
                if (jsonExample != null) {
                    try {
                        configuration.setResponse(mapper.readValue(jsonExample.toString(), Map.class));
                    } catch (IOException e) {
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

    private Map<String, Object> getResponseFromSimpleRef(Swagger swagger, String simpleRef) {
        Model model = swagger.getDefinitions().get(simpleRef);
        final Map<String, Property> properties;
        // allOf case
        if (model instanceof ComposedModel) {
            return  ((ComposedModel) model).getAllOf().stream().map((model1 -> {
                if (model1 instanceof RefModel) {
                    return getResponseFromSimpleRef(swagger, ((RefModel) model1).getSimpleRef());
                } else {
                    return getResponseProperties(swagger, model1.getProperties());
                }
            }))
                .flatMap(m -> m.entrySet().stream())
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        } else {
            properties = model.getProperties();
        }
        if (properties == null) {
            return emptyMap();
        }
        return getResponseProperties(swagger, properties);
    }

    private Map<String, Object> getResponseProperties(Swagger swagger, Map<String, Property> properties) {
        return properties.entrySet()
            .stream()
            .collect(toMap(Map.Entry::getKey, e -> {
                final Property property = e.getValue();
                if (property instanceof RefProperty) {
                    return this.getResponseFromSimpleRef(swagger, ((RefProperty) property).getSimpleRef());
                }
                return this.getResponsePropertiesFromType(property.getType());
            }));
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
