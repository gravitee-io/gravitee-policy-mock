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

import io.gravitee.policy.mock.json.JSONArray;
import io.gravitee.policy.mock.json.JSONException;
import io.gravitee.policy.mock.json.JSONObject;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;

/**
 * @author David BRASSELY (brasseld at gmail.com)
 * @author GraviteeSource Team
 */
public class StringUtils {

    public static boolean isJSON(String content) {
        try {
            new JSONObject(content);
        } catch (JSONException ex) {
            try {
                new JSONArray(content);
            } catch (JSONException ex1) {
                return false;
            }
        }

        return true;
    }

    public static boolean isXML(String content) {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setValidating(false);
        factory.setNamespaceAware(true);

        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(content);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }
}
