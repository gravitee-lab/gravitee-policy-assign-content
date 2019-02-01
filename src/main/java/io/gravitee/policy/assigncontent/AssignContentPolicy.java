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
package io.gravitee.policy.assigncontent;

import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateExceptionHandler;
import io.gravitee.gateway.api.ExecutionContext;
import io.gravitee.gateway.api.Request;
import io.gravitee.gateway.api.Response;
import io.gravitee.gateway.api.buffer.Buffer;
import io.gravitee.gateway.api.http.stream.TransformableRequestStreamBuilder;
import io.gravitee.gateway.api.http.stream.TransformableResponseStreamBuilder;
import io.gravitee.gateway.api.stream.ReadWriteStream;
import io.gravitee.gateway.api.stream.exception.TransformationException;
import io.gravitee.policy.api.PolicyChain;
import io.gravitee.policy.api.annotations.OnRequestContent;
import io.gravitee.policy.api.annotations.OnResponseContent;
import io.gravitee.policy.assigncontent.configuration.AssignContentPolicyConfiguration;
import io.gravitee.policy.assigncontent.configuration.PolicyScope;
import io.gravitee.policy.assigncontent.utils.AttributesBasedExecutionContext;
import io.gravitee.policy.assigncontent.utils.ContentAwareRequest;
import io.gravitee.policy.assigncontent.utils.ContentAwareResponse;
import io.gravitee.policy.assigncontent.utils.Sha1;

import java.io.IOException;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.HashMap;
import java.util.Map;

/**
 * @author David BRASSELY (david.brassely at graviteesource.com)
 * @author GraviteeSource Team
 */
public class AssignContentPolicy {

    private static Configuration templateConfiguration = loadConfiguration();

    /**
     * SOAP transformer templateConfiguration
     */
    private final AssignContentPolicyConfiguration configuration;

    public AssignContentPolicy(AssignContentPolicyConfiguration configuration) {
        this.configuration = configuration;
    }

    @OnRequestContent
    public ReadWriteStream onRequestContent(Request request, ExecutionContext executionContext, PolicyChain policyChain) {
        if (configuration.getScope() == PolicyScope.REQUEST) {
            return TransformableRequestStreamBuilder
                    .on(request)
                    .chain(policyChain)
                    .transform(
                            buffer -> {
                                try {
                                    Template template = getTemplate(configuration.getBody());
                                    StringWriter writer = new StringWriter();
                                    Map<String, Object> model = new HashMap<>();
                                    model.put("request", new ContentAwareRequest(request, buffer.toString()));
                                    model.put("context", new AttributesBasedExecutionContext(executionContext));
                                    template.process(model, writer);
                                    return Buffer.buffer(writer.toString());
                                } catch (Exception ioe) {
                                    throw new TransformationException("Unable to assign body content: " + ioe.getMessage(), ioe);
                                }
                            }
                    ).build();
        }

        return null;
    }

    @OnResponseContent
    public ReadWriteStream onResponseContent(Response response, ExecutionContext executionContext, PolicyChain policyChain) {
        if (configuration.getScope() == PolicyScope.RESPONSE) {
        return TransformableResponseStreamBuilder
                .on(response)
                .chain(policyChain)
                .transform(
                        buffer -> {
                            try {
                                Template template = getTemplate(configuration.getBody());
                                StringWriter writer = new StringWriter();
                                Map<String, Object> model = new HashMap<>();
                                model.put("response", new ContentAwareResponse(response, buffer.toString()));
                                model.put("context", new AttributesBasedExecutionContext(executionContext));
                                template.process(model, writer);
                                return Buffer.buffer(writer.toString());
                            } catch (Exception ioe) {
                                throw new TransformationException("Unable to assign body content: " + ioe.getMessage(), ioe);
                            }
                        }
                ).build();
        }

        return null;
    }

    private static Configuration loadConfiguration() {
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_27);
        configuration.setDefaultEncoding(Charset.defaultCharset().name());
        configuration.setTemplateExceptionHandler(TemplateExceptionHandler.RETHROW_HANDLER);
        configuration.setTemplateLoader(new StringTemplateLoader());
        return configuration;
    }

    private Template getTemplate(String template) throws IOException {
        StringTemplateLoader loader = (StringTemplateLoader) templateConfiguration.getTemplateLoader();
        String hash = Sha1.sha1(template);
        Object source = loader.findTemplateSource(hash);
        if (source == null) {
            loader.putTemplate(hash, template);
        }

        return templateConfiguration.getTemplate(hash);
    }
}
