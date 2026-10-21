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

import io.quarkus.test.junit.QuarkusMock;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.inject.Provider;
import org.apache.solr.client.solrj.SolrClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.tmatesoft.svn.core.io.SVNRepository;
import se.simonsoft.cms.backend.svnkit.config.CmsRepositoryName;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.naming.CmsItemNameFactory;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

/** The extensions own the servers; each test gets a fresh SVN repository and an empty Solr core. */
public abstract class SvnDatasetTest {

	@Inject protected CmsRepository repo;
	@Inject protected CmsCommit commit;
	@Inject protected CmsItemLookup lookup;
	@Inject protected CmsRepositoryLookup repoLookup;
	@Inject protected CmsItemLookupReporting lookupReporting;
	@Inject protected CmsItemNameFactory nameFactory;
	@Inject protected TransformerServiceFactory transformerServiceFactory;
	@Inject protected XmlSourceReaderS9api sourceReader;
	@Inject protected Provider<SVNRepository> svn;
	@Inject @Named("repositem") protected SolrClient solr;

	@BeforeEach
	public void loadDataset() throws Exception {
		QuarkusMock.installMockForType(new CmsRepositoryName("repo1"), CmsRepositoryName.class);
		svn.get().getLatestRevision(); // Resolve the session and load the unchanged repository.xml fixture.
	}

	protected String revisionProperty(RepoRevision revision, String name) throws Exception {
		return svn.get().getRevisionProperties(revision.getNumber(), null).getStringValue(name);
	}

	@AfterEach
	public void clearIndex() throws Exception {
		solr.deleteByQuery("*:*");
		solr.commit();
	}
}
