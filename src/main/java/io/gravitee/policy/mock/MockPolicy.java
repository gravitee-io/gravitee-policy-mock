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
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.gravitee.policy.mock.utils.StringUtils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
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
        public ProxyConnection invoke(ExecutionContext executionContext, Request serverRequest, Handler<ProxyResponse> result) {
            final ProxyConnection proxyConnection = new MockProxyConnection(executionContext, new MockClientResponse(), result);

            serverRequest
                    .bodyHandler(proxyConnection::write)
                    .endHandler(endResult -> proxyConnection.end());

            return proxyConnection;
        }
    }

    class MockProxyConnection implements ProxyConnection {

        private final ExecutionContext executionContext;
        private final MockClientResponse clientResponse;
        private final Handler<ProxyResponse> proxyResponseHandler;

        MockProxyConnection(final ExecutionContext executionContext, final MockClientResponse clientResponse, final Handler<ProxyResponse> proxyResponseHandler) {
            this.executionContext = executionContext;
            this.clientResponse = clientResponse;
            this.proxyResponseHandler = proxyResponseHandler;
        }

        @Override
        public ProxyConnection write(Buffer chunk) {
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

            proxyResponseHandler.handle(clientResponse);

            if (hasContent && buffer != null) {
                clientResponse.bodyHandler.handle(buffer);
            }

            clientResponse.endHandler.handle(null);
        }
    }

    class MockClientResponse implements ProxyResponse {

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
        public ProxyResponse bodyHandler(Handler<Buffer> bodyHandler) {
            this.bodyHandler = bodyHandler;
            return this;
        }

        @Override
        public ProxyResponse endHandler(Handler<Void> endHandler) {
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
