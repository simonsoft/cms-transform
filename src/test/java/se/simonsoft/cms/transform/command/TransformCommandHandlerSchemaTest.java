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
import static org.junit.Assert.assertThrows;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.command.CommandRuntimeException;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformConfigOptions;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationException;
import se.simonsoft.cms.transform.schema.TransformSchemaValidationIssue;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationError;

/**
 * Schema validation failure as command failure, e.g. the Step Functions task failure (error and cause).
 */
public class TransformCommandHandlerSchemaTest {

	private final CmsItemIdArg itemId = new CmsItemIdArg("x-svn:///svn/repo1/doc");
	private final ObjectMapper mapper = new ObjectMapper();

	private final TransformService transformServiceInvalid = new TransformService() {
		@Override
		public void transform(CmsItemId item, TransformConfig config) {
			throw getException();
		}

		@Override
		public Set<CmsItemId> importItem(CmsItemId itemId, TransformImportOptions config) {
			throw getException();
		}

		@Override
		public CmsItem importItemValidate(CmsItemId itemId, TransformImportOptions config) {
			throw new UnsupportedOperationException();
		}
	};

	private static TransformSchemaValidationException getException() {
		SchemaValidationError error = new SchemaValidationError(SchemaValidationError.Level.ERROR, "element \"para\" not allowed anywhere", 33, 16);
		return new TransformSchemaValidationException(2, 1, List.of(new TransformSchemaValidationIssue(new CmsItemPath("/doc/transform-test.xml"), error)));
	}

	private void assertFailure(CommandRuntimeException e) throws Exception {
		assertEquals("SchemaValidationFailed", e.getErrorName());
		JsonNode cause = mapper.readTree(e.getMessage());
		assertEquals("Schema validation failed: 1 of 2 items, 1 issues", cause.get("message").asText());
		assertEquals(1, cause.get("issues").size());
		assertEquals("/doc/transform-test.xml", cause.get("issues").get(0).get("path").asText());
		assertEquals(33, cause.get("issues").get(0).get("line").asInt());
	}

	@Test
	public void testTransform() throws Exception {
		TransformCommandHandler handler = new TransformCommandHandler(Map.of(itemId.getRepository(), transformServiceInvalid), mapper.writer());
		TransformConfig config = new TransformConfig();
		TransformConfigOptions options = new TransformConfigOptions();
		options.setType("xsl");
		config.setOptions(options);

		assertFailure(assertThrows(CommandRuntimeException.class, () -> handler.handleExternalCommand(itemId, config)));
	}

	@Test
	public void testImport() throws Exception {
		TransformImportCommandHandler handler = new TransformImportCommandHandler(Map.of(itemId.getRepository(), transformServiceInvalid), mapper.writer());

		assertFailure(assertThrows(CommandRuntimeException.class, () -> handler.handleExternalCommand(itemId, new TransformImportOptions())));
	}
}
