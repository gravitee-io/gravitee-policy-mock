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
package io.gravitee.policy.mock;

import io.gravitee.common.http.HttpHeaders;
import io.gravitee.gateway.api.*;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.gravitee.policy.mock.utils.StringUtils;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
public class MockPolicy {

    /**
     * Mock policy configuration
     */
    private final MockPolicyConfiguration mockPolicyConfiguration;

    public MockPolicy(MockPolicyConfiguration mockPolicyConfiguration) {
        this.mockPolicyConfiguration = mockPolicyConfiguration;
    }

    @OnRequest
    public void onRequest(Request request, Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        // Dynamically set the default invoker and provide a custom implementation
        // to returns data from mock.
        executionContext.setAttribute(ExecutionContext.ATTR_INVOKER, new MockInvoker());

        policyChain.doNext(request, response);
    }

    class MockInvoker implements Invoker {

        @Override
        public ClientRequest invoke(ExecutionContext executionContext, Request serverRequest, Handler<ClientResponse> result) {
            final ClientRequest clientRequest = new MockClientRequest(executionContext, new MockClientResponse(), result);

            serverRequest
                    .bodyHandler(clientRequest::write)
                    .endHandler(endResult -> clientRequest.end());

            return clientRequest;
        }
    }

    class MockClientRequest implements ClientRequest {

        private final ExecutionContext executionContext;
        private final MockClientResponse clientResponse;
        private final Handler<ClientResponse> clientResponseHandler;

        MockClientRequest(final ExecutionContext executionContext, final MockClientResponse clientResponse, final Handler<ClientResponse> clientResponseHandler) {
            this.executionContext = executionContext;
            this.clientResponse = clientResponse;
            this.clientResponseHandler = clientResponseHandler;
        }

        @Override
        public ClientRequest connectTimeoutHandler(Handler<Throwable> timeoutHandler) {
            return this;
        }

        @Override
        public ClientRequest write(Buffer chunk) {
            return this;
        }

        @Override
        public void end() {
            String content = mockPolicyConfiguration.getContent();
            boolean hasContent = (content != null && content.length() > 0);

            Buffer buffer = null;
            if (hasContent) {
                buffer = Buffer.buffer(executionContext.getTemplateEngine().convert(content));
                clientResponse.headers.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(buffer.length()));
                // Trying to discover content type
                if (! clientResponse.headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                    clientResponse.headers.set(HttpHeaders.CONTENT_TYPE, getContentType(content));
                }
            }

            clientResponseHandler.handle(clientResponse);

            if (hasContent && buffer != null) {
                clientResponse.bodyHandler.handle(buffer);
            }

            clientResponse.endHandler.handle(null);
        }
    }

    class MockClientResponse implements ClientResponse {

        private final HttpHeaders headers = new HttpHeaders();

        private Handler<Buffer> bodyHandler;
        private Handler<Void> endHandler;

        MockClientResponse() {
            this.init();
        }

        private void init() {
            if (mockPolicyConfiguration.getHeaders() != null) {
                mockPolicyConfiguration.getHeaders()
                        .stream()
                        .filter(header -> header.getName() != null && !header.getName().trim().isEmpty())
                        .forEach(header -> headers.add(header.getName(), header.getValue()));
            }
        }

        @Override
        public int status() {
            return mockPolicyConfiguration.getStatus();
        }

        @Override
        public HttpHeaders headers() {
            return headers;
        }

        @Override
        public ClientResponse bodyHandler(Handler<Buffer> bodyHandler) {
            this.bodyHandler = bodyHandler;
            return this;
        }

        @Override
        public ClientResponse endHandler(Handler<Void> endHandler) {
            this.endHandler = endHandler;
            return this;
        }
    }

    private static String getContentType(String content) {
        if (StringUtils.isJSON(content)) {
            return "application/json";
        } else if (StringUtils.isXML(content)) {
            return "text/xml";
        } else {
            return "text/plain";
        }
    }
}
