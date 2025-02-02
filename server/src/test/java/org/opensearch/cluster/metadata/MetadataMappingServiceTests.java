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

package org.opensearch.cluster.metadata;

import org.opensearch.action.admin.indices.create.CreateIndexRequestBuilder;
import org.opensearch.action.admin.indices.mapping.put.PutMappingClusterStateUpdateRequest;
import org.opensearch.cluster.ClusterState;
import org.opensearch.cluster.ClusterStateTaskExecutor;
import org.opensearch.cluster.service.ClusterService;
import org.opensearch.common.Strings;
import org.opensearch.common.compress.CompressedXContent;
import org.opensearch.common.xcontent.XContentBuilder;
import org.opensearch.common.xcontent.XContentFactory;
import org.opensearch.index.Index;
import org.opensearch.index.IndexService;
import org.opensearch.index.mapper.MapperService;
import org.opensearch.plugins.Plugin;
import org.opensearch.test.OpenSearchSingleNodeTestCase;
import org.opensearch.test.InternalSettingsPlugin;
import org.opensearch.cluster.metadata.MappingMetadata;
import org.opensearch.cluster.metadata.MetadataMappingService;

import java.util.Collection;
import java.util.Collections;

import static org.hamcrest.CoreMatchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.not;

public class MetadataMappingServiceTests extends OpenSearchSingleNodeTestCase {

    @Override
    protected Collection<Class<? extends Plugin>> getPlugins() {
        return Collections.singleton(InternalSettingsPlugin.class);
    }

    public void testMappingClusterStateUpdateDoesntChangeExistingIndices() throws Exception {
        final IndexService indexService = createIndex("test", client().admin().indices().prepareCreate("test").addMapping("type"));
        final CompressedXContent currentMapping = indexService.mapperService().documentMapper("type").mappingSource();

        final MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        final ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        // TODO - it will be nice to get a random mapping generator
        final PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest().type("type");
        request.indices(new Index[] {indexService.index()});
        request.source("{ \"properties\": { \"field\": { \"type\": \"text\" }}}");
        final ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
                mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        // the task completed successfully
        assertThat(result.executionResults.size(), equalTo(1));
        assertTrue(result.executionResults.values().iterator().next().isSuccess());
        // the task really was a mapping update
        assertThat(
                indexService.mapperService().documentMapper("type").mappingSource(),
                not(equalTo(result.resultingState.metadata().index("test").getMappings().get("type").source())));
        // since we never committed the cluster state update, the in-memory state is unchanged
        assertThat(indexService.mapperService().documentMapper("type").mappingSource(), equalTo(currentMapping));
    }

    public void testClusterStateIsNotChangedWithIdenticalMappings() throws Exception {
        createIndex("test", client().admin().indices().prepareCreate("test").addMapping("type"));

        final MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        final ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        final PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest().type("type");
        request.source("{ \"properties\" { \"field\": { \"type\": \"text\" }}}");
        ClusterState result = mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request))
            .resultingState;

        assertFalse(result != clusterService.state());

        ClusterState result2 = mappingService.putMappingExecutor.execute(result, Collections.singletonList(request))
            .resultingState;

        assertSame(result, result2);
    }

    public void testMappingVersion() throws Exception {
        final IndexService indexService = createIndex("test", client().admin().indices().prepareCreate("test").addMapping("type"));
        final long previousVersion = indexService.getMetadata().getMappingVersion();
        final MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        final ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        final PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest().type("type");
        request.indices(new Index[] {indexService.index()});
        request.source("{ \"properties\": { \"field\": { \"type\": \"text\" }}}");
        final ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
                mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        assertThat(result.executionResults.size(), equalTo(1));
        assertTrue(result.executionResults.values().iterator().next().isSuccess());
        assertThat(result.resultingState.metadata().index("test").getMappingVersion(), equalTo(1 + previousVersion));
    }

    public void testMappingVersionUnchanged() throws Exception {
        final IndexService indexService = createIndex("test", client().admin().indices().prepareCreate("test").addMapping("type"));
        final long previousVersion = indexService.getMetadata().getMappingVersion();
        final MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        final ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        final PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest().type("type");
        request.indices(new Index[] {indexService.index()});
        request.source("{ \"properties\": {}}");
        final ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
                mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        assertThat(result.executionResults.size(), equalTo(1));
        assertTrue(result.executionResults.values().iterator().next().isSuccess());
        assertThat(result.resultingState.metadata().index("test").getMappingVersion(), equalTo(previousVersion));
    }

    public void testMappingUpdateAccepts_docAsType() throws Exception {
        final IndexService indexService = createIndex("test",
                client().admin().indices().prepareCreate("test").addMapping("my_type"));
        final MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        final ClusterService clusterService = getInstanceFromNode(ClusterService.class);
        final PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest()
                .type(MapperService.SINGLE_MAPPING_NAME);
        request.indices(new Index[] {indexService.index()});
        request.source("{ \"properties\": { \"foo\": { \"type\": \"keyword\" } }}");
        final ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
                mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        assertThat(result.executionResults.size(), equalTo(1));
        assertTrue(result.executionResults.values().iterator().next().isSuccess());
        MappingMetadata mappingMetadata = result.resultingState.metadata().index("test").mapping();
        assertEquals("my_type", mappingMetadata.type());
        assertEquals(Collections.singletonMap("properties",
                Collections.singletonMap("foo",
                        Collections.singletonMap("type", "keyword"))), mappingMetadata.sourceAsMap());
    }

    public void testForbidMultipleTypes() throws Exception {
        CreateIndexRequestBuilder createIndexRequest = client().admin().indices()
            .prepareCreate("test")
            .addMapping(MapperService.SINGLE_MAPPING_NAME);
        IndexService indexService = createIndex("test", createIndexRequest);

        MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        ClusterService clusterService = getInstanceFromNode(ClusterService.class);

        PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest()
            .type("other_type")
            .indices(new Index[] {indexService.index()})
            .source(Strings.toString(XContentFactory.jsonBuilder()
                .startObject()
                    .startObject("other_type").endObject()
                .endObject()));
        ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
            mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        assertThat(result.executionResults.size(), equalTo(1));

        ClusterStateTaskExecutor.TaskResult taskResult = result.executionResults.values().iterator().next();
        assertFalse(taskResult.isSuccess());
        assertThat(taskResult.getFailure().getMessage(), containsString(
            "Rejecting mapping update to [test] as the final mapping would have more than 1 type: "));
    }

    /**
     * This test checks that the multi-type validation is done before we do any other kind of validation
     * on the mapping that's added, see https://github.com/elastic/elasticsearch/issues/29313
     */
    public void testForbidMultipleTypesWithConflictingMappings() throws Exception {
        XContentBuilder mapping = XContentFactory.jsonBuilder().startObject()
            .startObject(MapperService.SINGLE_MAPPING_NAME)
                .startObject("properties")
                    .startObject("field1")
                        .field("type", "text")
                    .endObject()
                .endObject()
            .endObject()
        .endObject();

        CreateIndexRequestBuilder createIndexRequest = client().admin().indices()
            .prepareCreate("test")
            .addMapping(MapperService.SINGLE_MAPPING_NAME, mapping);
        IndexService indexService = createIndex("test", createIndexRequest);

        MetadataMappingService mappingService = getInstanceFromNode(MetadataMappingService.class);
        ClusterService clusterService = getInstanceFromNode(ClusterService.class);

        String conflictingMapping = Strings.toString(XContentFactory.jsonBuilder().startObject()
            .startObject("other_type")
                .startObject("properties")
                    .startObject("field1")
                        .field("type", "keyword")
                    .endObject()
                .endObject()
            .endObject()
        .endObject());

        PutMappingClusterStateUpdateRequest request = new PutMappingClusterStateUpdateRequest()
            .type("other_type")
            .indices(new Index[] {indexService.index()})
            .source(conflictingMapping);
        ClusterStateTaskExecutor.ClusterTasksResult<PutMappingClusterStateUpdateRequest> result =
            mappingService.putMappingExecutor.execute(clusterService.state(), Collections.singletonList(request));
        assertThat(result.executionResults.size(), equalTo(1));

        ClusterStateTaskExecutor.TaskResult taskResult = result.executionResults.values().iterator().next();
        assertFalse(taskResult.isSuccess());
        assertThat(taskResult.getFailure().getMessage(), containsString(
            "Rejecting mapping update to [test] as the final mapping would have more than 1 type: "));
    }
}
