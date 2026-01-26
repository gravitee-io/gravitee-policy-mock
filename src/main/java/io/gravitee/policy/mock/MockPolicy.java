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

import io.gravitee.el.exceptions.ELNullEvaluationException;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.HttpHeaderNames;
import io.gravitee.gateway.reactive.api.ExecutionFailure;
import io.gravitee.gateway.reactive.api.context.InternalContextAttributes;
import io.gravitee.gateway.reactive.api.context.http.HttpExecutionContext;
import io.gravitee.gateway.reactive.api.context.http.HttpPlainExecutionContext;
import io.gravitee.gateway.reactive.api.invoker.HttpInvoker;
import io.gravitee.gateway.reactive.api.policy.http.HttpPolicy;
import io.gravitee.policy.mock.configuration.MockPolicyConfiguration;
import io.reactivex.rxjava3.core.Completable;
import io.reactivex.rxjava3.core.Flowable;
import io.reactivex.rxjava3.core.Maybe;

public class MockPolicy extends MockPolicyV3 implements HttpPolicy {

    private static final String REQUEST_VARIABLE = "request";

    public MockPolicy(MockPolicyConfiguration mockPolicyConfiguration) {
        super(mockPolicyConfiguration);
    }

    @Override
    public String id() {
        return "policy-mock";
    }

    @Override
    public Completable onRequest(HttpPlainExecutionContext ctx) {
        return Completable.defer(() -> {
            ctx.setInternalAttribute(InternalContextAttributes.ATTR_INTERNAL_INVOKER, new MockHttpInvoker(super.mockPolicyConfiguration));
            return Completable.complete();
        });
    }

    static class MockHttpInvoker implements HttpInvoker {

        private final MockPolicyConfiguration mockPolicyConfiguration;

        public MockHttpInvoker(MockPolicyConfiguration mockPolicyConfiguration) {
            this.mockPolicyConfiguration = mockPolicyConfiguration;
        }

        @Override
        public String getId() {
            return "policy-mock-invoker";
        }

        @Override
        public Completable invoke(HttpExecutionContext ctx) {
            return ctx
                .request()
                .chunks()
                .ignoreElements()
                .andThen(
                    Completable.defer(() ->
                        setResponseBodyWithContentHeadersAndStatus(ctx)
                            .andThen(setResponseHeaders(ctx))
                            .onErrorResumeNext(throwable ->
                                ctx.interruptWith(new ExecutionFailure(500).message(throwable.getMessage()).cause(throwable))
                            )
                    )
                );
        }

        private Completable setResponseBodyWithContentHeadersAndStatus(HttpExecutionContext ctx) {
            final String content = mockPolicyConfiguration.getContent();
            if (content != null && !content.isBlank()) {
                ctx.getTemplateEngine().getTemplateContext().setVariable(REQUEST_VARIABLE, ctx.request());
                return ctx
                    .getTemplateEngine()
                    .eval(mockPolicyConfiguration.getContent(), String.class)
                    .switchIfEmpty(Maybe.error(new ELNullEvaluationException("Unable to evaluate body")))
                    .doOnSuccess(evaluatedContent -> {
                        Buffer contentBuffer = Buffer.buffer(evaluatedContent);
                        ctx.response().body(contentBuffer);
                        ctx.response().contentLength(contentBuffer.length());
                        if (!ctx.response().headers().contains(HttpHeaderNames.CONTENT_TYPE)) {
                            ctx.response().headers().set(HttpHeaderNames.CONTENT_TYPE, getContentType(evaluatedContent));
                        }
                        ctx.response().status(mockPolicyConfiguration.getStatus());
                    })
                    .ignoreElement();
            } else {
                ctx.response().body(Buffer.buffer());
                ctx.response().status(mockPolicyConfiguration.getStatus());
                return Completable.complete();
            }
        }

        private Completable setResponseHeaders(HttpExecutionContext ctx) {
            if (mockPolicyConfiguration.getHeaders() == null || mockPolicyConfiguration.getHeaders().isEmpty()) {
                return Completable.complete();
            }
            return Flowable.fromIterable(mockPolicyConfiguration.getHeaders())
                .filter(httpHeader -> httpHeader.getName() != null && !httpHeader.getName().isBlank())
                .flatMapCompletable(httpHeader ->
                    ctx
                        .getTemplateEngine()
                        .eval(httpHeader.getValue(), String.class)
                        .doOnSuccess(evaluatedHeaderValue -> ctx.response().headers().set(httpHeader.getName(), evaluatedHeaderValue))
                        .ignoreElement()
                        .onErrorComplete()
                );
        }
    }
}
