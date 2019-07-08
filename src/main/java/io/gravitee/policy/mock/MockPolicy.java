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
import io.gravitee.common.http.HttpStatusCode;
import io.gravitee.common.http.MediaType;
import io.gravitee.el.exceptions.ELNullEvaluationException;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Invoker;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.handler.Handler;
import io.gravitee.gateway.api.proxy.ProxyConnection;
import io.gravitee.gateway.api.proxy.ProxyResponse;
import io.gravitee.gateway.api.stream.ReadStream;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequest;
import io.gravitee.policy.mock.configuration.HttpHeader;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.gravitee.policy.mock.el.EvaluableRequest;
import io.gravitee.policy.mock.utils.StringUtils;

import java.util.function.Consumer;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class MockPolicy {

    private final static String REQUEST_VARIABLE = "request";

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
        public void invoke(ExecutionContext context, ReadStream<Buffer> stream, Handler<ProxyConnection> connectionHandler) {
            final ProxyConnection proxyConnection = new MockProxyConnection(context);

            // Return connection to backend
            connectionHandler.handle(proxyConnection);

            // Plug underlying stream to connection stream
            stream
                    .bodyHandler(proxyConnection::write)
                    .endHandler(aVoid -> proxyConnection.end());

            // Resume the incoming request to handle content and end
            context.request().resume();
        }
    }

    class MockProxyConnection implements ProxyConnection {

        private Handler<ProxyResponse> proxyResponseHandler;
        private final ExecutionContext executionContext;
        private Buffer content;

        MockProxyConnection(final ExecutionContext executionContext) {
            this.executionContext = executionContext;
        }

        @Override
        public ProxyConnection write(Buffer chunk) {
            if (content == null) {
                content = Buffer.buffer();
            }
            content.appendBuffer(chunk);
            return this;
        }

        @Override
        public void end() {
            proxyResponseHandler.handle(
                    new MockClientResponse(executionContext,
                            new EvaluableRequest(executionContext.request(), (content != null) ? content.toString() : null)));
        }

        @Override
        public ProxyConnection responseHandler(Handler<ProxyResponse> responseHandler) {
            this.proxyResponseHandler = responseHandler;
            return this;
        }
    }

    class MockClientResponse implements ProxyResponse {

        private final HttpHeaders headers = new HttpHeaders();

        private Handler<Buffer> bodyHandler;
        private Handler<Void> endHandler;
        private int status;

        private Buffer buffer;

        MockClientResponse(final ExecutionContext executionContext, final EvaluableRequest request) {
            this.init(executionContext, request);
        }

        private void init(ExecutionContext executionContext, EvaluableRequest request) {
            status = mockPolicyConfiguration.getStatus();
            if (mockPolicyConfiguration.getHeaders() != null) {
                mockPolicyConfiguration.getHeaders()
                        .stream()
                        .filter(header -> header.getName() != null && !header.getName().trim().isEmpty())
                        .forEach(new Consumer<HttpHeader>() {
                            @Override
                            public void accept(HttpHeader header) {
                                try {
                                    String extValue = (header.getValue() != null) ?
                                            executionContext.getTemplateEngine().getValue(header.getValue(), String.class) : null;
                                    if (extValue != null) {
                                        headers.set(header.getName(), extValue);
                                    }
                                } catch (Exception ex) {
                                    // Do nothing
                                    ex.printStackTrace();
                                }
                            }
                        });
            }

            String content = mockPolicyConfiguration.getContent();
            boolean hasContent = (content != null && content.length() > 0);

            if (hasContent) {
                executionContext.getTemplateEngine().getTemplateContext()
                        .setVariable(REQUEST_VARIABLE, request);

                String evaluatedContent = executionContext.getTemplateEngine().getValue(content, String.class);
                if (evaluatedContent == null) {
                    status = HttpStatusCode.INTERNAL_SERVER_ERROR_500;
                    evaluatedContent = new ELNullEvaluationException(content).getMessage();
                }

                buffer = Buffer.buffer(evaluatedContent);
                headers.set(HttpHeaders.CONTENT_LENGTH, Integer.toString(buffer.length()));
                // Trying to discover content type
                if (! headers.containsKey(HttpHeaders.CONTENT_TYPE)) {
                    headers.set(HttpHeaders.CONTENT_TYPE, getContentType(content));
                }
            }
        }

        @Override
        public int status() {
            return status;
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