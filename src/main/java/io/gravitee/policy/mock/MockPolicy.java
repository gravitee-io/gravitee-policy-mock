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
import io.gravitee.common.http.MediaType;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.policy.api.ChainScope;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.Category;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.api.annotations.Policy;
import io.gravitee.policy.api.annotations.Scope;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.gravitee.policy.mock.utils.StringUtils;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
@Policy(
        category = @Category(io.gravitee.policy.api.Category.OTHERS),
        scope = @Scope(ChainScope.API)
)
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
        public Request invoke(ExecutionContext executionContext, Request serverRequest, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
            final ProxyConnection proxyConnection = new MockProxyConnection(executionContext);

            // Return connection to backend
            connectionHandler.handle(proxyConnection);

            // Plug underlying stream to connection stream
            stream
                    .bodyHandler(proxyConnection::write)
                    .endHandler(aVoid -> proxyConnection.end());

            // Resume the incoming request to handle content and end
            serverRequest.resume();

            return serverRequest;
        }
    }

    class MockProxyConnection implements ProxyConnection {

        private final MockClientResponse clientResponse;
        private Handler<ProxyResponse> proxyResponseHandler;

        MockProxyConnection(final ExecutionContext executionContext) {
            this.clientResponse = new MockClientResponse(executionContext);
        }

        @Override
        public ProxyConnection write(Buffer chunk) {
            return this;
        }

        @Override
        public void end() {
            proxyResponseHandler.handle(clientResponse);
        }

        @Override
        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
            this.proxyResponseHandler = responseHandler;
            return this;
        }
    }

    class MockClientResponse implements ProxyResponse {

        private final HttpHeaders headers = new HttpHeaders();
        private final ExecutionContext executionContext;

        private Handler<Buffer> bodyHandler;
        private Handler<Void> endHandler;

        private Buffer buffer;

        MockClientResponse(final ExecutionContext executionContext) {
            this.executionContext = executionContext;
            this.init();
        }

        private void init() {
            if (mockPolicyConfiguration.getHeaders() != null) {
                mockPolicyConfiguration.getHeaders()
                        .stream()
                        .filter(header -> header.getName() != null && !header.getName().trim().isEmpty())
                        .forEach(header -> headers.add(header.getName(), header.getValue()));
            }

            String content = mockPolicyConfiguration.getContent();
            boolean hasContent = (content != null && content.length() > 0);

            if (hasContent) {
                buffer = Buffer.buffer(executionContext.getTemplateEngine().convert(content));
                headers.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(buffer.length()));
                // Trying to discover content type
                if (! headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                    headers.set(HttpHeaders.CONTENT_TYPE, getContentType(content));
                }
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

        @Override
        public ReadStream<Buffer> resume() {
            if (buffer != null) {
                bodyHandler.handle(buffer);
            }

            endHandler.handle(null);
            return this;
        }
    }

    private static String getContentType(String content) {
        if (StringUtils.isJSON(content)) {
            return MediaType.APPLICATION_JSON;
        } else if (StringUtils.isXML(content)) {
            return MediaType.TEXT_XML;
        } else {
            return MediaType.TEXT_PLAIN;
        }
    }
}