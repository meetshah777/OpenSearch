/*
 * SPDX-License-Identifier: Apache-2.0
 *
 * The OpenSearch Contributors require contributions made to
 * this file be licensed under the Apache-2.0 license or a
 * compatible open source license.
 */

/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
/*
 * Modifications Copyright OpenSearch Contributors. See
 * GitHub history for details.
 */

package org.opensearch.test.rest.yaml.restspec;

import org.opensearch.common.ParsingException;
import org.opensearch.common.xcontent.XContentParser;
import org.opensearch.common.xcontent.yaml.YamlXContent;
import org.opensearch.test.OpenSearchTestCase;

import static org.hamcrest.Matchers.containsString;

/**
 * These tests are not part of {@link ClientYamlSuiteRestApiParserTests} because the tested failures don't allow to consume the whole yaml
 * stream
 */
public class ClientYamlSuiteRestApiParserFailingTests extends OpenSearchTestCase {

    public void testDuplicateMethods() throws Exception {
       parseAndExpectParsingException("{\n" +
               "  \"ping\": {" +
               "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
               "    \"stability\": \"stable\",\n" +
               "    \"url\": {" +
               "      \"paths\": [{\"path\":\"/\", \"parts\": {}, \"methods\": [\"PUT\", \"PUT\"]}]," +
               "      \"params\": {" +
               "        \"type\" : \"boolean\",\n" +
               "        \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"" +
               "      }" +
               "    }," +
               "    \"body\": null" +
               "  }" +
               "}", "ping.json", "ping API: found duplicate method [PUT]");
    }

    public void testDuplicatePaths() throws Exception {
        parseAndExpectIllegalArgumentException("{\n" +
                "  \"ping\": {" +
                "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
                "    \"stability\": \"stable\",\n" +
                "    \"url\": {" +
                "      \"paths\": [" +
                "         {\"path\":\"/pingtwo\", \"methods\": [\"PUT\"]}, " + "{\"path\":\"/pingtwo\", \"methods\": [\"PUT\"]}]," +
                "      \"params\": {" +
                "        \"type\" : \"boolean\",\n" +
                "        \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"" +
                "      }" +
                "    }," +
                "    \"body\": null" +
                "  }" +
                "}", "ping.json", "ping API: found duplicate path [/pingtwo]");
    }

    public void testBrokenSpecShouldThrowUsefulExceptionWhenParsingFailsOnParams() throws Exception {
        parseAndExpectParsingException(BROKEN_SPEC_PARAMS, "ping.json",
            "ping API: expected [params] field in rest api definition to contain an object");
    }

    public void testBrokenSpecShouldThrowUsefulExceptionWhenParsingFailsOnParts() throws Exception {
        parseAndExpectParsingException(BROKEN_SPEC_PARTS, "ping.json",
            "ping API: expected [parts] field in rest api definition to contain an object");
    }

    public void testSpecNameMatchesFilename() throws Exception {
        parseAndExpectIllegalArgumentException("{\"ping\":{}}", "not_matching.json", "API [ping] should have " +
            "the same name as its file [not_matching.json]");
    }

    private void parseAndExpectParsingException(String brokenJson, String location, String expectedErrorMessage) throws Exception {
        XContentParser parser = createParser(YamlXContent.yamlXContent, brokenJson);
        ClientYamlSuiteRestApiParser restApiParser = new ClientYamlSuiteRestApiParser();

        ParsingException e = expectThrows(ParsingException.class, () -> restApiParser.parse(location, parser));
        assertThat(e.getMessage(), containsString(expectedErrorMessage));
    }

    private void parseAndExpectIllegalArgumentException(String brokenJson, String location, String expectedErrorMessage) throws Exception {
        XContentParser parser = createParser(YamlXContent.yamlXContent, brokenJson);
        ClientYamlSuiteRestApiParser restApiParser = new ClientYamlSuiteRestApiParser();

        IllegalArgumentException e = expectThrows(IllegalArgumentException.class, () -> restApiParser.parse(location, parser));
        assertThat(e.getMessage(), containsString(expectedErrorMessage));
    }

    // see params section is broken, an inside param is missing
    private static final String BROKEN_SPEC_PARAMS = "{\n" +
            "  \"ping\": {" +
            "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
            "    \"stability\": \"stable\",\n" +
            "    \"url\": {" +
            "      \"paths\": [{\"path\": \"path\", \"methods\": [\"HEAD\"]}]" +
            "    }," +
            "    \"params\": {" +
            "      \"type\" : \"boolean\",\n" +
            "      \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"\n" +
            "    }," +
            "    \"body\": null" +
            "  }" +
            "}";

    // see parts section is broken, an inside param is missing
    private static final String BROKEN_SPEC_PARTS = "{\n" +
            "  \"ping\": {" +
            "    \"documentation\": \"http://www.elasticsearch.org/guide/\"," +
            "    \"stability\": \"stable\",\n" +
            "    \"url\": {" +
            "      \"paths\": [{ \"path\":\"/\", \"parts\": { \"type\":\"boolean\",}}]," +
            "      \"params\": {\n" +
            "        \"ignore_unavailable\": {\n" +
            "          \"type\" : \"boolean\",\n" +
            "          \"description\" : \"Whether specified concrete indices should be ignored when unavailable (missing or closed)\"\n" +
            "        } \n" +
            "    }," +
            "    \"body\": null" +
            "  }" +
            "}";
}
