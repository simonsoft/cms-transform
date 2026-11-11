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

import java.util.Map;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.ObjectReader;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.smallrye.common.annotation.Identifier;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import net.sf.saxon.s9api.Processor;
import org.apache.solr.client.solrj.SolrClient;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.indexing.IdStrategy;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.reporting.ReportingConfiguration;
import se.simonsoft.cms.reporting.SearchCore;
import se.simonsoft.cms.reporting.solrj.SearchCoreRepositemSolrj;
import se.simonsoft.cms.xmlsource.SaxonConfiguration;
import se.simonsoft.cms.xmlsource.transform.TransformStylesheetSource;
import se.simonsoft.cms.xmlsource.transform.TransformStylesheetSourceConfig;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;
import se.simonsoft.cms.xmlsource.transform.function.GetChecksum;
import se.simonsoft.cms.xmlsource.transform.function.GetLogicalId;
import se.simonsoft.cms.xmlsource.transform.function.GetPegRev;
import se.simonsoft.cms.xmlsource.transform.function.WithPegRev;
import se.simonsoft.cms.transform.schema.TransformSchemaService;
import se.simonsoft.cms.transform.service.TransformService;

/** Application-owned dependencies, as supplied by webapp, using the real SVN/Solr services. */
public class TestTransformProducer {

	@Produces @Singleton
	Processor processor() {
		return new SaxonConfiguration(Set.of(new GetChecksum(), new GetPegRev(),
				new WithPegRev(), new GetLogicalId())).get();
	}

	@Produces @Singleton
	TransformStylesheetSource stylesheets() {
		Map<String, String> stylesheets = TransformerServiceFactory.getStylesheetsForTestingMap();
		stylesheets.put("transform-multiple-output.xsl", "se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-multiple-output.xsl");
		stylesheets.put("transform-single-output.xsl", "se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-single-output.xsl");
		return new TransformStylesheetSourceConfig(stylesheets);
	}

	@Produces @Singleton
	ObjectReader objectReader() {
		return new ObjectMapper().reader();
	}

	@Produces @Singleton
	ObjectWriter objectWriter() {
		return new ObjectMapper().writer();
	}

	@Produces @RequestScoped
	TransformSchemaService schemas(Processor processor) {
		return new TestTransformSchemaService(processor);
	}

	@Produces @Singleton @Named("global") @Identifier("global")
	CmsRepositoryLookup globalLookup(CmsRepositoryLookup lookup) {
		return lookup;
	}

	@Produces @RequestScoped
	Map<CmsRepository, TransformService> transforms(CmsRepository repository, TransformService service) {
		return Map.of(repository, service);
	}

	@Produces @Singleton @Named("config:se.simonsoft.cms.hostname")
	String hostname(@ConfigProperty(name = "cms.hostname") String hostname) {
		return hostname;
	}

	@Produces @Singleton @Named("config:se.simonsoft.cms.repoparent")
	String repositoryParent(@ConfigProperty(name = "cms.repoparent") String parent) {
		return parent;
	}

	@Produces @Singleton @Identifier("repositem")
	SearchCore searchCore(@Named("repositem") SolrClient client, IdStrategy idStrategy) {
		return new SearchCoreRepositemSolrj(client, idStrategy);
	}

	@Produces @RequestScoped
	Map<CmsRepository, CmsItemLookup> lookups(CmsRepository repository, CmsItemLookup lookup) {
		return Map.of(repository, lookup);
	}

	@Produces @RequestScoped
	Map<CmsRepository, ReportingConfiguration> reportingConfigurations(CmsRepository repository, ReportingConfiguration config) {
		return Map.of(repository, config);
	}
}
