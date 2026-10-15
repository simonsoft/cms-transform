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
package se.simonsoft.cms.transform.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXParseException;

import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.stream.ByteArrayInOutStream;
import se.simonsoft.cms.item.structure.CmsItemClassificationXml;
import se.simonsoft.cms.transform.schema.TransformSchemaService;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationError;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationException;

/**
 * Schema validation of the items in one transform, before they are added to the patchset.
 *
 * All items are validated and all issues logged, then {@link #complete()} fails if any item is invalid.
 */
class TransformSchemaValidation {

	static final String SCHEMA_VARIANT_DITABASE = "ditabase";
	static final String SCHEMA_VARIANT_DITAMAP = "ditamap";

	private static final Logger logger = LoggerFactory.getLogger(TransformSchemaValidation.class);

	private final TransformSchemaService schemaService;
	private final CmsRepository repository;
	private final Optional<String> qname;
	private final Optional<String> variant;
	private final CmsItemClassificationXml classification = new CmsItemClassificationXml();

	private int itemCount = 0;
	private int itemFailedCount = 0;
	private int issueCount = 0;

	/**
	 * @param qname schema application, empty for the built-in schema
	 * @param variant overrides the variant from the file extension
	 */
	TransformSchemaValidation(TransformSchemaService schemaService, CmsRepository repository, Optional<String> qname, Optional<String> variant) {
		this.schemaService = schemaService;
		this.repository = repository;
		this.qname = qname;
		this.variant = variant;
	}

	/**
	 * Validates XML items, other items are returned as is.
	 * @param content the content that will be committed, consumed
	 * @return the same content, to be added to the patchset
	 */
	InputStream validate(CmsItemPath path, InputStream content) {
		if (!classification.isXml(repository.getItemId().withRelPath(path))) {
			return content;
		}
		// Transform output is a ByteArrayInputStream (TransformStreamProvider), re-read with reset() without copying.
		final ByteArrayInputStream xml = content instanceof ByteArrayInputStream ? (ByteArrayInputStream) content : buffer(content);
		xml.mark(0);
		// Empty output is discarded when added to the patchset, same check.
		final byte[] start = new byte[200];
		final int len = Math.max(xml.read(start, 0, start.length), 0);
		xml.reset();
		if (len == 0 || TransformServiceXsl.emptyExceptDeclaration(new String(start, 0, len, StandardCharsets.UTF_8))) {
			return xml;
		}

		final String itemVariant = variant.orElse(getSchemaVariant(path));
		logger.debug("Schema validation of {} with schema {} {}", path, qname.orElse("(built-in)"), itemVariant);
		itemCount++;
		List<SchemaValidationError> errors = validate(itemVariant, xml);
		xml.reset(); // ByteArrayInputStream.close() has no effect.
		if (!errors.isEmpty()) {
			itemFailedCount++;
			issueCount += errors.size();
			for (SchemaValidationError error : errors) {
				logger.warn("Schema validation {} in {} (line {}, column {}): {}", error.getLevel(), path, error.getLineNumber(), error.getColumnNumber(), error.getMessage());
			}
		}
		return xml;
	}

	// Other streams, e.g. import from URL.
	private static ByteArrayInputStream buffer(InputStream content) {
		ByteArrayInOutStream buffer = new ByteArrayInOutStream();
		try (InputStream in = content) {
			in.transferTo(buffer);
		} catch (IOException e) {
			throw new UncheckedIOException("Failed to read content for schema validation.", e);
		}
		return buffer.getInputStream();
	}

	/**
	 * @throws IllegalStateException if one or more items failed validation
	 */
	void complete() {
		if (itemFailedCount > 0) {
			throw new IllegalStateException(String.format("Schema validation failed: %d of %d items, %d issues", itemFailedCount, itemCount, issueCount));
		}
		logger.info("Schema validation passed: {} items", itemCount);
	}

	private List<SchemaValidationError> validate(String itemVariant, InputStream xml) {
		try {
			schemaService.validate(qname, itemVariant, xml);
			return Collections.emptyList();
		} catch (SchemaValidationException e) {
			return e.getErrors();
		} catch (RuntimeException e) {
			// Not well-formed XML is a fatal parse error, report it like the schema issues.
			if (e.getCause() instanceof SAXParseException) {
				SAXParseException cause = (SAXParseException) e.getCause();
				return List.of(new SchemaValidationError(SchemaValidationError.Level.FATAL, cause.getMessage(), cause.getLineNumber(), cause.getColumnNumber()));
			}
			throw e;
		}
	}

	/**
	 * @return ditamap for the file extension .ditamap, otherwise ditabase (like ReleaseSchema in cms-release)
	 */
	static String getSchemaVariant(CmsItemPath path) {
		if (SCHEMA_VARIANT_DITAMAP.equals(path.getExtension())) {
			return SCHEMA_VARIANT_DITAMAP;
		}
		return SCHEMA_VARIANT_DITABASE;
	}
}
