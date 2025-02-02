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
 *    http://www.apache.org/licenses/LICENSE-2.0
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

package org.opensearch.rest.action.admin.indices;

import org.opensearch.client.node.NodeClient;
import org.opensearch.rest.RestRequest;
import org.opensearch.test.rest.FakeRestRequest;
import org.opensearch.test.rest.RestActionTestCase;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import static org.opensearch.rest.BaseRestHandler.INCLUDE_TYPE_NAME_PARAMETER;
import static org.mockito.Mockito.mock;

public class RestGetIndicesActionTests extends RestActionTestCase {

    /**
     * Test that setting the "include_type_name" parameter raises a warning for the GET request
     */
    public void testIncludeTypeNamesWarning() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put(INCLUDE_TYPE_NAME_PARAMETER, randomFrom("true", "false"));
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.GET)
            .withPath("/some_index")
            .withParams(params)
            .build();

        RestGetIndicesAction handler = new RestGetIndicesAction();
        handler.prepareRequest(request, mock(NodeClient.class));
        assertWarnings(RestGetIndicesAction.TYPES_DEPRECATION_MESSAGE);

        // the same request without the parameter should pass without warning
        request = new FakeRestRequest.Builder(xContentRegistry())
                .withMethod(RestRequest.Method.GET)
                .withPath("/some_index")
                .build();
        handler.prepareRequest(request, mock(NodeClient.class));
    }

    /**
     * Test that setting the "include_type_name" parameter doesn't raises a warning if the HEAD method is used (indices.exists)
     */
    public void testIncludeTypeNamesWarningExists() throws IOException {
        Map<String, String> params = new HashMap<>();
        params.put(INCLUDE_TYPE_NAME_PARAMETER, randomFrom("true", "false"));
        RestRequest request = new FakeRestRequest.Builder(xContentRegistry())
            .withMethod(RestRequest.Method.HEAD)
            .withPath("/some_index")
            .withParams(params)
            .build();

        RestGetIndicesAction handler = new RestGetIndicesAction();
        handler.prepareRequest(request, mock(NodeClient.class));
    }
}
