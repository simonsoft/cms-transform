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
package se.simonsoft.cms.transform.schema;

import java.io.InputStream;
import java.util.Optional;

import se.simonsoft.cms.xmlsource.validation.SchemaValidationException;

/**
 * Schema validation of transform results.
 *
 * Schemas are looked up by schema application, which cms-transform can not do on its own.
 * Bound by the webapp, typically delegating to SchemaService in cms-release (same signature).
 */
public interface TransformSchemaService {

	/**
	 * Validate XML against schema.
	 * @param qname schema application identifier (fallback to default schema)
	 * @param variant within the schema, e.g. ditabase or ditamap
	 * @param xml input XML stream
	 * @throws SchemaValidationException with all errors if validation fails
	 */
	void validate(Optional<String> qname, String variant, InputStream xml) throws SchemaValidationException;
}
