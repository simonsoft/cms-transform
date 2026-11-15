/**
 * Copyright (C) 2009-2017 Simonsoft Nordic AB
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
package se.simonsoft.cms.transform.command;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.service.TransformResult;
import se.simonsoft.cms.transform.service.TransformService;

/**
 * The dry run result as command response, e.g. the Step Functions task output.
 */
public class TransformCommandHandlerDryRunTest {

	private final CmsItemIdArg itemId = new CmsItemIdArg("x-svn:///svn/repo1/doc");
	private final ObjectMapper mapper = new ObjectMapper();

	private TransformCommandHandler getHandler(TransformResult result) {
		TransformService transformService = new TransformService() {
			@Override
			public TransformResult transform(CmsItemId item, TransformConfig config) {
				return result;
			}

			@Override
			public Set<CmsItemId> importItem(CmsItemId itemId, TransformImportOptions config) {
				throw new UnsupportedOperationException();
			}

			@Override
			public CmsItem importItemValidate(CmsItemId itemId, TransformImportOptions config) {
				throw new UnsupportedOperationException();
			}
		};
		return new TransformCommandHandler(Map.of(itemId.getRepository(), transformService), mapper.writer());
	}

	private TransformConfig getConfig() {
		TransformConfigOptions options = new TransformConfigOptions();
		options.setType("xsl");
		TransformConfig config = new TransformConfig();
		config.setOptions(options);
		return config;
	}

	@Test
	public void testDryRunResponse() throws Exception {
		String response = getHandler(new TransformResult(true, new RepoRevision(12, null))).handleExternalCommand(itemId, getConfig());
		JsonNode json = mapper.readTree(response);
		assertTrue(json.get("dryRun").asBoolean());
		assertEquals(12, json.get("baseRevision").asLong());
		assertEquals(0, json.get("lockedCount").asInt());
	}

	@Test
	public void testCommitNoResponse() throws Exception {
		// Unchanged response when committed.
		assertNull(getHandler(new TransformResult(false, new RepoRevision(12, null))).handleExternalCommand(itemId, getConfig()));
	}
}
