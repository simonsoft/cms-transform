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
package se.simonsoft.cms.transform.testconfig;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import net.sf.saxon.s9api.Processor;
import se.simonsoft.cms.transform.schema.TransformSchemaService;
import se.simonsoft.cms.xmlsource.validation.SchemaValidationException;
import se.simonsoft.cms.xmlsource.validation.SchemaValidator;

/**
 * Schemas from the classpath, similar to SchemaService in cms-release:
 * built-in schema without qname, otherwise the schema application.
 */
public class TestTransformSchemaService implements TransformSchemaService {

	private static final String SCHEMA_FOLDER = "se/simonsoft/cms/transform/schema/";

	private final Processor processor;
	private final Map<String, SchemaValidator> validators = new HashMap<>();

	public TestTransformSchemaService(Processor processor) {
		this.processor = processor;
	}

	@Override
	public void validate(Optional<String> qname, String variant, InputStream xml) throws SchemaValidationException {
		getValidator(qname.orElse("builtin") + "/" + variant + ".s.rng").validate(xml);
	}

	private SchemaValidator getValidator(String path) {
		return validators.computeIfAbsent(path, key -> {
			InputStream rng = this.getClass().getClassLoader().getResourceAsStream(SCHEMA_FOLDER + key);
			if (rng == null) {
				throw new IllegalArgumentException("Schema file is missing: " + key);
			}
			try (InputStream in = rng) {
				return new SchemaValidator(processor, in);
			} catch (IOException e) {
				throw new UncheckedIOException(e);
			}
		});
	}
}
