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

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.IOUtils;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLockCollection;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.commit.CmsPatchItem;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.commit.FileAdd;
import se.simonsoft.cms.item.commit.FileModificationLocked;
import se.simonsoft.cms.item.commit.FolderExist;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.item.properties.CmsItemPropertiesMap;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.xmlsource.handler.XmlSourceReader;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceDocumentS9api;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.SaxonOutputURIResolverXdm;
import se.simonsoft.cms.xmlsource.transform.TransformOptions;
import se.simonsoft.cms.xmlsource.transform.TransformStreamProvider;
import se.simonsoft.cms.xmlsource.transform.TransformerService;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXsl implements TransformService {
	
	private final CmsCommit commit;
	private final CmsItemLookup itemLookup;
	private final TransformerServiceFactory transformerServiceFactory;
	private final CmsRepositoryLookup repoLookup;
	private final Map<String, TransformerService> stylesheets;
	private final XmlSourceReaderS9api sourceReader;
	private final TransformerService transformerIdentity;
	
	private static final String TRANSFORM_LOCK_COMMENT = "Locked for transform";
	private static final String TRANSFORM_BASE_PROP_KEY = "abx:TransformBase";
	private static final String TRANSFORM_NAME_PROP_KEY = "abx:TransformName";
	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			CmsRepositoryLookup lookupRepo,
			TransformerServiceFactory transfromerServiceFactory,
			Map<String, TransformerService> stylesheets,
			XmlSourceReader sourceReader
			) {
		
		this.commit = commit;
		this.itemLookup = itemLookup;
		this.repoLookup = lookupRepo;
		this.transformerServiceFactory = transfromerServiceFactory;
		this.transformerIdentity = transfromerServiceFactory.buildTransformerService(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/xmlsource/transform/identity.xsl")));
		this.stylesheets = stylesheets;
		if (sourceReader instanceof XmlSourceReaderS9api) {
			this.sourceReader = (XmlSourceReaderS9api) sourceReader;
		} else {
			throw new IllegalArgumentException("TransformServiceXsl requires Saxon S9API impl of XmlSourceReader, got: " + sourceReader.getClass());
		}
	}

	@Override
	public void transform(CmsItem item, TransformConfig config) {
		if (config == null || config.getOptions() == null) {
			throw new IllegalArgumentException("TransformServiceXsl needs a valid TransformConfig object.");
		}
		
		final CmsItem base = item;
		final CmsItemId baseItemId = base.getId();
		final CmsRepository repository = baseItemId.getRepository();
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		final Boolean overwrite = new Boolean(config.getOptions().getParams().get("overwrite"));
		
		if (!config.getOptions().getType().equals("xsl")) {
			throw new IllegalArgumentException("TransformServiceXsl can only handle xsl transforms but was given: " + config.getOptions().getType());
		}
		
		final String stylesheet = config.getOptions().getParams().get("stylesheet");
		if (stylesheet == null || stylesheet.trim().isEmpty()) {
			throw new IllegalArgumentException("Requires a valid stylesheet path or stylesheet name.");
		}
		
		final CmsItemPath outputPath = getOutputPath(baseItemId ,config.getOptions().getParams().get("output"));
		if (!pathExists(repository, outputPath)) {
			throw new IllegalArgumentException("Specified output must be an existing folder: " + outputPath.getPath());
		}
		
		final TransformerService transformerService = getTransformerService(baseItemId, stylesheet);
		if (transformerService == null) {
			throw new IllegalArgumentException("Could not create transformerService with stylesheet: " + stylesheet);
		}
		
		transformerService.setItemLookup(itemLookup);
		
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		patchset.setHistoryMessage(config.getOptions().getParams().get("comment"));
		
		final CmsItemPropertiesMap props = getProperties(base, config);
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		TransformOptions transformOptions = new TransformOptions();
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		TransformStreamProvider baseStreamProvider = transformerService.getTransformStreamProvider(baseItemId, transformOptions);
		addToPatchset(patchset, outputPath.append(baseItemId.getRelPath().getName()), baseStreamProvider, overwrite, props);
		
		Set<String> resultDocsHrefs = outputURIResolver.getResultDocumentHrefs();
		for (String relpath: resultDocsHrefs) {
			if (relpath.startsWith("/")) {
				throw new IllegalArgumentException("Relative href must not start with slash: " + relpath);
			}
			
			XmlSourceDocumentS9api resultDocument = outputURIResolver.getResultDocument(relpath);
			TransformStreamProvider streamProvider = transformerIdentity.getTransformStreamProvider(resultDocument, null);
			
			CmsItemPath path = outputPath.append(Arrays.asList(relpath.split("/")));
			addToPatchset(patchset, path, streamProvider, overwrite, props);
		}
		
		commit.run(patchset);
	}
	
	private void addToPatchset(CmsPatchset patchset, CmsItemPath relPath, TransformStreamProvider streamProvider, boolean overwrite, CmsItemPropertiesMap properties) {
		try {
			
			final InputStream transformStream = getInputStreamNotEmpty(streamProvider.get());
			boolean pathExists = pathExists(patchset.getRepository(), relPath);
			if (!pathExists) {
				logger.debug("No file at path: '{}' will add new file.", relPath);
				FileAdd fileAdd = new FileAdd(relPath, transformStream);
				fileAdd.setPropertyChange(properties);
				patchset.add(fileAdd);
				addFolderExists(patchset, relPath.getParent());
			} else if (overwrite){
				logger.debug("Overwrite is allowed, existing file at path '{}' will be modified.", relPath.getPath());
				CmsItemId itemId = patchset.getRepository().getItemId().withRelPath(relPath);
				CmsItemLockCollection locks = commit.lock(TRANSFORM_LOCK_COMMENT, patchset.getBaseRevision(), itemId.getRelPath());
				if (locks != null && locks.getSingle() == null) {
					throw new IllegalStateException("Unable to retrieve the lock token after locking " + itemId);
				}
				patchset.addLock(locks.getSingle());
				FileModificationLocked fileMod = new FileModificationLocked(relPath, transformStream);
				fileMod.setPropertyChange(properties);
				patchset.add(fileMod);
			} else {
				throw new IllegalStateException("Item already exists, config prohibiting overwrite of existing items.");
			}
			
		} catch (IOException e) {
			throw new RuntimeException("Failed to read stream from transform.", e);
		} catch (EmptyStreamException e) {
			logger.warn("Transform of item at path: '{}'  resulted in empty document, will be discarded.", relPath);
		}
	}
	
	private CmsItemPath getOutputPath(CmsItemId itemId, String output) {
		
		CmsItemPath pathResult;
		
		if (output == null || output.trim().isEmpty()) {
			pathResult = itemId.getRepository().getItemId().withRelPath(itemId.getRelPath().getParent()).getRelPath();
			logger.debug("Output folder is not specified will default to items parent folder.");
		} else {
			pathResult = itemId.getRepository().getItemId().withRelPath(new CmsItemPath(output)).getRelPath();
			logger.debug("Output folder is specified: {}", output);
		}
		
		return pathResult;
	}

	private TransformerService getTransformerService(CmsItemId itemId, String stylesheet) {
		
		TransformerService resultService;
		
		if (stylesheet.startsWith("/")) {
			CmsItemId styleSheetItemId = itemId.getRepository().getItemId().withRelPath(new CmsItemPath(stylesheet));
			logger.debug("Using stylesheet from CMS: {}", styleSheetItemId.getLogicalId());
			CmsItem styleSheetItem = itemLookup.getItem(styleSheetItemId);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			styleSheetItem.getContents(baos);
			resultService = transformerServiceFactory.buildTransformerService(new StreamSource(IOUtils.toInputStream(baos.toString(StandardCharsets.UTF_8), StandardCharsets.UTF_8)));
		} else {
			logger.debug("Using CMS built in stylesheet: {}", stylesheet);
			resultService = stylesheets.get(stylesheet); 
		}
		
		return resultService;
	}
	
	private boolean pathExists(CmsRepository repo, CmsItemPath path) {
		
		CmsItemId itemIdOutput = repo.getItemId().withRelPath(path);
		boolean result = false;
		
		try {
			itemLookup.getItem(itemIdOutput);
			result = true;
		} catch (CmsItemNotFoundException e) {
			result = false;
		}
		
		return result;
	}
	
	private InputStream getInputStreamNotEmpty(InputStream inputStream) throws IOException, EmptyStreamException {
		PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
		int b;
		b = pushbackInputStream.read();
		if ( b == -1 ) {
			throw new EmptyStreamException("Transform is empty");
		}
		pushbackInputStream.unread(b);
		return pushbackInputStream;
	}
	
	private CmsItemPropertiesMap getProperties(CmsItem baseItem, TransformConfig config) {
		
		CmsItemPropertiesMap m = new CmsItemPropertiesMap();
		m.and(TRANSFORM_BASE_PROP_KEY, baseItem.getId().getLogicalId())
		.and(TRANSFORM_NAME_PROP_KEY, config.getName());
		return m;
	}
	
	private void addFolderExists(CmsPatchset patchset, CmsItemPath parentPath) {
		Iterator<CmsPatchItem> iterator = patchset.iterator();
		boolean addFolderExist = true;
		while (iterator.hasNext() && addFolderExist) {
			CmsPatchItem next = iterator.next();
			if (next instanceof FolderExist && next.getPath().compareTo(parentPath) == 0) {
				addFolderExist = false;
			}
		}

		if (addFolderExist) {
			patchset.add(new FolderExist(parentPath));

		}
	}
	
	private class EmptyStreamException extends Exception {

		private static final long serialVersionUID = 1L;
		
		public EmptyStreamException(String message) {
			super(message);
		}
		
	}

}
