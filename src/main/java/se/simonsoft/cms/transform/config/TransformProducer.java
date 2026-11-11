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
package se.simonsoft.cms.transform.config;

import java.util.Map;

import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.naming.CmsItemNameFactory;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.transform.command.TransformCommandHandler;
import se.simonsoft.cms.transform.command.TransformImportCommandHandler;
import se.simonsoft.cms.transform.schema.TransformSchemaService;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.transform.service.TransformServiceXsl;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

/**
 * Repository-bound services live in the caller's request context, also for background jobs.
 * The application supplies repository selection, global reads, JSON and schema validation.
 * Operation results and validation state are created inside each transform/import invocation.
 */
public class TransformProducer {

	@Produces
	@RequestScoped
	public TransformService createService(CmsCommit commit, CmsItemLookup lookup, CmsItemLookupReporting reporting,
			CmsRepositoryLookup repositoryLookup, TransformerServiceFactory transformers,
			XmlSourceReaderS9api reader, CmsItemNameFactory names, TransformSchemaService schemas) {
		return new TransformServiceXsl(commit, lookup, reporting, repositoryLookup, transformers, reader, names, schemas);
	}

	@Produces
	@RequestScoped
	public TransformConfiguration createConfiguration(@Named("global") CmsRepositoryLookup lookup, ObjectReader reader) {
		return new TransformConfigurationDefault(lookup, reader);
	}

	@Produces
	@RequestScoped
	public TransformCommandHandler createTransformCommand(Map<CmsRepository, TransformService> services, ObjectWriter writer) {
		return new TransformCommandHandler(services, writer);
	}

	@Produces
	@RequestScoped
	public TransformImportCommandHandler createImportCommand(Map<CmsRepository, TransformService> services, ObjectWriter writer) {
		return new TransformImportCommandHandler(services, writer);
	}
}
