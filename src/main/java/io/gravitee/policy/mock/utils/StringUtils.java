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
package io.gravitee.policy.mock.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.StringReader;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.xml.sax.InputSource;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
public class StringUtils {

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();
    private static final DocumentBuilderFactory XML_FACTORY = DocumentBuilderFactory.newInstance();

    public static boolean isJSON(String content) {
        try {
            JsonNode node = JSON_MAPPER.readTree(content);
            return node.isObject() || node.isArray();
        } catch (Exception e) {
            return false;
        }
    }

    public static boolean isXML(String content) {
        try {
            DocumentBuilder builder = XML_FACTORY.newDocumentBuilder();
            builder.parse(new InputSource(new StringReader(content)));
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
