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

import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.repos.indexing.standalone.config.IndexingHandlersModuleXml;
import se.repos.testing.indexing.ReposTestIndexing;
import se.repos.testing.indexing.TestIndexOptions;
import se.simonsoft.cms.backend.filexml.CmsRepositoryFilexml;
import se.simonsoft.cms.backend.filexml.FilexmlCommit;
import se.simonsoft.cms.backend.filexml.FilexmlRepositoryReadonly;
import se.simonsoft.cms.backend.filexml.FilexmlSourceClasspath;
import se.simonsoft.cms.backend.filexml.commit.FilexmlCommitMemory;
import se.simonsoft.cms.backend.filexml.testing.ReposTestBackendFilexml;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.transform.service.TransformService;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

import com.google.inject.Module;

public class TestFileXmlSetUp {

	private FilexmlSourceClasspath repoSource = null;
	private ReposTestIndexing indexing = null;

	private FilexmlCommit commit;

	public String hostname;
	public final CmsRepository repo;

	private static final Logger logger = LoggerFactory.getLogger(TestFileXmlSetUp.class);

	public void setUpIndexing() {
		TestIndexOptions indexOptions = new TestIndexOptions().itemDefaultServices()
				.addCore("reposxml", "se/simonsoft/cms/indexing/xml/solr/reposxml/**")
				.addModule(new IndexingHandlersModuleXml());
		indexing = ReposTestIndexing.getInstance(indexOptions);
	}
	
	public TestFileXmlSetUp(CmsRepository repository, FilexmlSourceClasspath sourceClasspath) {
		this(repository, sourceClasspath, new HashMap<String, String>());
	}

	public TestFileXmlSetUp(CmsRepository repository, FilexmlSourceClasspath sourceClasspath, Map<String, String> startup) {

		setUpIndexing();

		repo = repository;
		repoSource = sourceClasspath;
		hostname = repo.getHost();

		CmsRepositoryFilexml repoFilexml = new CmsRepositoryFilexml(repo.getUrl(), repoSource);

		FilexmlRepositoryReadonly filexml = new FilexmlRepositoryReadonly(repoFilexml);
		commit = new FilexmlCommitMemory(filexml);

		// Run indexing, install hook
		try {
			indexing.enable(new ReposTestBackendFilexml(filexml, commit)); // No need for indexing in Transform tests, but fails with injection issues otherwise.
		} catch (RuntimeException e) {
			// Log when test framework gets out-of-sync and requires tearDown.
			logger.warn(e.getMessage(), e);
			throw e;
		}
	}

	public FilexmlSourceClasspath getRepoSource() {

		if (this.repoSource == null) throw new IllegalStateException("FilexmlSourceClasspath is null, you have to run STestFilexmlSetUp.setUp()");

		return this.repoSource;
	}

	public ReposTestIndexing getIndexing() {

		if (this.indexing == null) throw new IllegalStateException("FilexmlSourceClasspath is null, you have to run STestFilexmlSetUp.setUp()");

		return this.indexing;
	}

	public CmsRepository getRepo() {
		return this.repo;
	}

	public FilexmlCommit getCommit() {
		return this.commit;
	}
	
	 // This is required with our custom DI context to get access to per-repo services
    public static interface TestServiceGlobalEmptyInterface {
    }

    public static interface TestServicePerRepoEmptyInterface {
    }

    // Defines all services that this test class needs
    public static class TestServiceGlobal implements TestServiceGlobalEmptyInterface {
        @Inject
        Map<CmsRepository, TestServicePerRepoEmptyInterface> perRepo = null;
    }

    public static class TestServicePerRepo implements TestServicePerRepoEmptyInterface {
        @Inject
        @Named("config:se.simonsoft.abxconfig.ReleasePath")
        String releasePath; // for the setUp self-test
        @Inject
        CmsItemLookup cmsItemLookup;
//        @Inject
//        CmsItemLookupRepositem cmsItemLookupRepositem;
        @Inject
        TransformerServiceFactory transformerServiceFactory;
        @Inject
        XmlSourceReaderS9api sourceReader;
        @Inject
        TransformService transformService;


        public CmsItemLookup getCmsItemLookup() {
            return this.cmsItemLookup;
        }
        
//        public CmsItemLookupRepositem getCmsItemLookupRepositem() {
//            return this.cmsItemLookupRepositem;
//        }

        public TransformerServiceFactory getTransformerServiceFactory() {
        	return transformerServiceFactory;
        }
        
        public XmlSourceReaderS9api getSourceReader() {
        	return sourceReader;
        }
        public TransformService getTransformService() {
        	return transformService;
        }
    }

    public void tearDown() {
    	if (indexing != null) {
    		indexing.tearDown();
    	}
    }
}
