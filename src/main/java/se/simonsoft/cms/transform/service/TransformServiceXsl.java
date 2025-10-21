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
import java.io.PushbackInputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.transform.stream.StreamSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.slf4j.helpers.MessageFormatter;
import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemKind;
import se.simonsoft.cms.item.CmsItemLock;
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
import se.simonsoft.cms.item.naming.CmsItemNamePattern;
import se.simonsoft.cms.item.naming.CmsItemNaming;
import se.simonsoft.cms.item.naming.CmsItemNamingShard1K;
import se.simonsoft.cms.item.properties.CmsItemProperties;
import se.simonsoft.cms.item.properties.CmsItemPropertiesMap;
import se.simonsoft.cms.item.structure.CmsItemClassificationXml;
import se.simonsoft.cms.reporting.CmsItemLookupReporting;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;
import se.simonsoft.cms.transform.lookup.CmsItemLookupTransform;
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
	private final CmsItemLookupReporting itemLookupReporting;
	private final CmsItemLookup itemLookupTransform;
	private final TransformerServiceFactory transformerServiceFactory;
	private final CmsRepositoryLookup repoLookup;
	private final XmlSourceReaderS9api sourceReader;
	private final TransformerService transformerOutput;
	
	private static final String TRANSFORM_LOCK_COMMENT = "Locked for transform";
	private static final String TRANSFORM_BASE_PROP_KEY = "abx:TransformBase";
	private static final String TRANSFORM_NAME_PROP_KEY = "abx:TransformName";
	private static final int HISTORY_MSG_MAX_SIZE = 2000;
	private static final String OUTPUT_TRANSFORM = "se/simonsoft/cms/transform/output.xsl";

	private static final int HTTP_URL_CONNECTION_READ_TIMEOUT = 60000;  	// 60 seconds
	private static final int HTTP_URL_CONNECTION_CONNECT_TIMEOUT = 30000;  	// 30 seconds
	private static final String HTTP_URL_CONNECTION_USER_AGENT = "cms-transform/1.0";

	private static final String CMS_CLASS_SHARDPARENT = "shardparent";
	private static final String PROPNAME_CONFIG_ITEMNAMEPATTERN = "cmsconfig:ItemNamePattern";

	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			CmsItemLookupReporting itemLookupReporting,
			CmsRepositoryLookup lookupRepo,
			TransformerServiceFactory transfromerServiceFactory,
			XmlSourceReaderS9api sourceReader
			) {
		
		this.commit = commit;
		this.itemLookup = itemLookup;
		this.itemLookupReporting = itemLookupReporting;
		this.repoLookup = lookupRepo;
		this.transformerServiceFactory = transfromerServiceFactory;
		this.transformerOutput = transfromerServiceFactory.buildTransformerService(new StreamSource(this.getClass().getClassLoader().getResourceAsStream(OUTPUT_TRANSFORM)));
		this.sourceReader = sourceReader;
		
		this.itemLookupTransform = new CmsItemLookupTransform(itemLookup, itemLookupReporting);
	}

	@Override
	public void transform(CmsItemId itemId, TransformConfig config) {
		if (config == null || config.getOptions() == null) {
			throw new IllegalArgumentException("TransformServiceXsl needs a valid TransformConfig object.");
		}
		
		final CmsItemId baseItemId = itemId;
		final CmsRepository repository = baseItemId.getRepository();
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		
		// Prevent repository root, could be supported but requires some refactoring below.
		if (baseItemId.getRelPath() == null) {
			throw new IllegalArgumentException("TransformServiceXsl does not support repository root.");
		}
		
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
		
		CmsItem item = itemLookup.getItem(baseItemId);
		
		final TransformerService transformerService = getTransformerService(baseItemId, stylesheet);
		
		// CmsItemLookupTransform will capture items with specific class, normal items will resolve via normal CmsItemLookup.
		transformerService.setItemLookup(itemLookupTransform);
		
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		TransformOptions transformOptions = new TransformOptions();
		
		Set<CmsItemId> items = new LinkedHashSet<>();
		if (item.getKind() == CmsItemKind.Folder) {
			Set<CmsItemId> files = itemLookup.getImmediateFiles(baseItemId);
			// Filtering based on CmsItemClassificationXml in combination with tikahtml cms:class.
			// Workaround for backend returning itemIds with p=-1, remove when fixed in backend. 
			// Never transforms non-head anyway.
			files.stream().filter(fileId -> isTransformable(fileId)).forEach(fileId -> items.add(fileId.withPegRev(null)));
			logger.info("Transforming {} of {} items in folder: {}", items.size(), files.size(), baseItemId);
		} else {
			items.add(baseItemId);
		}
		
		// Locked items can be any items in the repository (any number), not just the input items.
		final Set<CmsItemLock> locked = new HashSet<>();
		try {
			for (CmsItemId id: items) {
				locked.addAll(transformItem(id, config, transformerService, transformOptions, patchset));
			}
		} catch (RuntimeException e) {
			logger.warn("Failed to transform / lock items: {}", e.getMessage(), e);
			// Release all locks taken by previous iterations of the loop.
			unlockItemsFailure(locked);
			throw e;
		}
		
		List<String> messages = transformOptions.getMessageListener().getMessages();
		String completeMessage = getCompleteMessageString(config.getOptions().getParams().get("comment"), messages);
		if (completeMessage != null && !completeMessage.trim().isEmpty()) {
			patchset.setHistoryMessage(completeMessage);
		}
		
		RepoRevision r = commit.run(patchset);
		logger.debug("Transform complete, commited with rev: {}", r.getNumber());
	}

	@Override
	public Set<CmsItemId> importItem(CmsItemId item, TransformImportOptions config) {
		Set<CmsItemId> response = new HashSet<>();

		if (config == null) {
			throw new IllegalArgumentException("Import requires a valid TransformImportOptions object.");
		}

		CmsItemPath relPath = item.getRelPath();
		CmsItemPath parentFolder = relPath.getParent();

		final String url = config.getUrl();
		final String content = config.getContent();
		final CmsRepository repository = item.getRepository();
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		final CmsItemPropertiesMap properties = config.getItemPropertiesMap();

		final boolean overwrite = Boolean.parseBoolean(config.getParams().get("overwrite"));
		if (overwrite) {
			throw new IllegalArgumentException("The overwrite option is currently not supported.");
		}

		final String pathext = config.getParams().get("pathext");
		if (pathext == null || pathext.isEmpty()) {
			throw new IllegalArgumentException("No pathext parameter was supplied.");
		}

		try {
			if (!itemLookup.getItem(item).getKind().isFolder()) {
				throw new IllegalArgumentException("Item must be an existing folder: " + item);
			}
		} catch (CmsItemNotFoundException e) {
			throw new IllegalArgumentException("Item must be an existing folder: " + item, e);
		}

		final String pathnamebase = config.getParams().get("pathnamebase");
		final CmsItem location = itemLookup.getItem(repository.getItemId(relPath, null));
		final CmsItemProperties locationProps = location.getProperties();

		final boolean isShardParent = isCmsClass(locationProps, CMS_CLASS_SHARDPARENT);
		final boolean hasNamePattern = locationProps.containsProperty(PROPNAME_CONFIG_ITEMNAMEPATTERN);
		final boolean hasPathnamebase = pathnamebase != null && !pathnamebase.isEmpty();

		if (isShardParent) {
			if (!hasNamePattern) throw new IllegalArgumentException(MessageFormatter.format("Location does not define a name pattern: {}", parentFolder).getMessage());
			if (hasPathnamebase) throw new IllegalStateException("The pathnamebase is not allowed when the folder is configured a shardparent with a name pattern.");
			CmsItemNaming itemNaming = new CmsItemNamingShard1K(repository, itemLookup);
			CmsItemNamePattern namePattern = new CmsItemNamePattern(locationProps.getString(PROPNAME_CONFIG_ITEMNAMEPATTERN));
			relPath = itemNaming.getItemPath(relPath, namePattern, pathext);
		} else if (hasPathnamebase) {
			relPath = relPath.append(String.format("%s.%s", pathnamebase, pathext));
		} else {
			throw new IllegalStateException("Either the folder must be configured a shardparent with a name pattern or a pathnamebase parameter must be supplied.");
		}

		final Set<CmsItemLock> locked = new HashSet<>();
		try {
			InputStream stream;
			if (url != null && !url.trim().isEmpty()) {
				// The file content is to be downloaded from the provided URL
				stream = download(url);
			} else if (content != null && !content.isEmpty()) {
				// The file contents are already provided
				stream = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
			} else {
				throw new IllegalArgumentException("Import requires either a valid URL or content.");
			}
			CmsItemLock lock = addToPatchset(patchset, relPath, stream, overwrite, properties);
			if (lock != null) locked.add(lock);
			String comment = config.getParams().get("comment");
			if (comment != null && !comment.trim().isEmpty()) patchset.setHistoryMessage(comment);
			RepoRevision r = commit.run(patchset);
			if (url != null && !url.trim().isEmpty()) {
				logger.info("Import URL complete: {} -> {}, committed with rev: {}", url, relPath, r.getNumber());
			} else {
				logger.info("Importing content complete: {}, committed with rev: {}", relPath, r.getNumber());
			}
			response.add(new CmsItemIdArg(repository, relPath).withPegRev(r.getNumber()));
		} catch (IOException | URISyntaxException | InterruptedException e) {
			logger.error("Failed to download content from URL: {}", url, e);
			unlockItemsFailure(locked);
			throw new RuntimeException("Failed to download content from URL: " + url, e);
		} catch (RuntimeException e) {
			logger.warn("Failed to import item: {}", e.getMessage(), e);
			unlockItemsFailure(locked);
			throw e;
		}

		return response;
	}

	private InputStream download(String url) throws IOException, URISyntaxException, InterruptedException {
		HttpClient client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofMillis(HTTP_URL_CONNECTION_CONNECT_TIMEOUT))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();

		HttpRequest request = HttpRequest.newBuilder()
				.uri(URI.create(url))
				.timeout(Duration.ofMillis(HTTP_URL_CONNECTION_READ_TIMEOUT))
		// Use the default User-Agent for now. Consider injecting a good universal value in CMS 6.0.
		//		.header("User-Agent", HTTP_URL_CONNECTION_USER_AGENT)
				.GET()
				.build();

		HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

		if (response.statusCode() == 200) {
			return response.body();
		} else {
			throw new IOException("HTTP request failed with response code: " + response.statusCode() + " for URL: " + url);
		}
	}


	private boolean isTransformable(CmsItemId itemId) {
		
		CmsItemClassificationXml classification = new CmsItemClassificationXml();
		if (classification.isXml(itemId)) {
			return true;
		}
		CmsItem item = this.itemLookup.getItem(itemId);
		// TODO: Use cms-item method when available.
        return isCmsClass(item.getProperties(), "tikahtml");
    }
	
	private Set<CmsItemLock> transformItem(CmsItemId baseItemId, TransformConfig config, TransformerService transformerService, TransformOptions transformOptions, CmsPatchset patchset) {
		
		logger.debug("Transforming itemid: {}", baseItemId);
		final CmsItemPropertiesMap props = getProperties(baseItemId, config);
		final CmsItemPath outputPath = getOutputPath(baseItemId, config.getOptions().getParams().get("output"));
		final boolean overwrite = Boolean.valueOf(config.getOptions().getParams().get("overwrite"));
		final Set<CmsItemLock> locked = new HashSet<>();
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		try {
			
			TransformStreamProvider baseStreamProvider = transformerService.getTransformStreamProvider(baseItemId, transformOptions);
			locked.add(addToPatchset(patchset, outputPath.append(baseItemId.getRelPath().getName()), baseStreamProvider.get(), overwrite, props));
			
			Set<String> resultDocsHrefs = outputURIResolver.getResultDocumentHrefs();
			for (String href: resultDocsHrefs) {
				if (href.startsWith("/")) {
					throw new IllegalArgumentException("Relative href must not start with slash: " + href);
				}
				
				XmlSourceDocumentS9api resultDocument = outputURIResolver.getResultDocument(href);
				TransformStreamProvider streamProvider = transformerOutput.getTransformStreamProvider(resultDocument, null);
				
				String decodedHref = decodeHref(href); // Items will be commited with decoded hrefs.
				CmsItemPath path = outputPath.append(Arrays.asList(decodedHref.split("/")));
				locked.add(addToPatchset(patchset, path, streamProvider.get(), overwrite, props));
			}
		} catch (RuntimeException e) {
			// Unlock locks taken in this invocation of transformItem.
			unlockItemsFailure(locked);
			throw e;
		}
		return locked;
	}
	
	private void unlockItemsFailure(Set<CmsItemLock> locked) {
		logger.info("Transform failed, unlocking {} items.", locked.size());
		try {
			this.commit.unlock(locked.toArray(new CmsItemLock[0]));
		} catch (Exception e) {
			logger.warn("Failed to unlock items after transform / locking failed: {}", e.getMessage(), e);
		}
	}
	
	private String getCompleteMessageString(String comment, List<String> messages) {
		StringBuilder sb = new StringBuilder();
		sb.append(comment);
		
		if (!messages.isEmpty()) {
			sb.append("\n");
		}
		
		Iterator<String> iterator = messages.iterator();
		boolean addMoreMessages = iterator.hasNext();
		while (addMoreMessages) {
			String message = iterator.next();
			if (message != null && !message.trim().isEmpty()) {
				if ((sb.length() + message.length()) < HISTORY_MSG_MAX_SIZE) {
					sb.append("\n");
					sb.append(message);
					addMoreMessages = iterator.hasNext();
				} else {
					logger.info("Max history message size ({}) reached truncating", HISTORY_MSG_MAX_SIZE);
					sb.append("\n");
					sb.append("...");
					addMoreMessages = false;
				}
			}
		}
		return sb.toString();
	}


	private CmsItemLock addToPatchset(CmsPatchset patchset, CmsItemPath relPath, InputStream stream, boolean overwrite, CmsItemPropertiesMap properties) {
		CmsItemLock lock = null;
		try {
			final InputStream inputStream = getInputStreamNotEmpty(stream);
			boolean pathExists = pathExists(patchset.getRepository(), relPath);
			if (!pathExists) {
				addFolderExists(patchset, relPath.getParent());
				logger.debug("No file at path: '{}' will add new file.", relPath);
				FileAdd fileAdd = new FileAdd(relPath, inputStream);
				fileAdd.setPropertyChange(properties);
				patchset.add(fileAdd);
			} else if (overwrite){
				logger.debug("Overwrite is allowed, existing file at path '{}' will be modified.", relPath.getPath());
				CmsItemId itemId = patchset.getRepository().getItemId().withRelPath(relPath);
				CmsItemLockCollection locks = commit.lock(TRANSFORM_LOCK_COMMENT, patchset.getBaseRevision(), itemId.getRelPath());
				if (locks != null && locks.getSingle() == null) {
					throw new IllegalStateException("Unable to retrieve the lock token after locking " + itemId);
				}
				lock = locks.getSingle();
				patchset.addLock(lock);
				FileModificationLocked fileMod = new FileModificationLocked(relPath, inputStream);
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
		return lock;
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
			logger.debug("Using stylesheet from repo: {}", styleSheetItemId.getLogicalId());
			CmsItem styleSheetItem;
			try {
				 styleSheetItem = itemLookup.getItem(styleSheetItemId);
				 logger.debug("Using stylesheet from repo: {} {}", styleSheetItemId.getLogicalId(), styleSheetItem.getRevisionChanged());
			} catch (CmsItemNotFoundException e) {
				throw new IllegalArgumentException("Specified stylesheet does not exist at path: " + stylesheet, e);
			}
			
			// #1367 Now using a stylesheet factory method that provides caching without reading the content.
			resultService = transformerServiceFactory.buildTransformerService(styleSheetItem);
		} else {
			// TODO: Guard against very long string 'stylesheet'.
			// Use named built-in stylesheet.
			resultService = transformerServiceFactory.buildTransformerService(stylesheet);
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
		int maxRead = 200;
		PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream, maxRead);
		byte[] bytes = new byte[maxRead];
		int len = pushbackInputStream.read(bytes, 0, maxRead);
		String data = new String(bytes, StandardCharsets.UTF_8);
		
		if (len == -1 || emptyExceptDeclaration(data)) { 
			throw new EmptyStreamException("Transform is empty");
		}
		
		byte[] copyOf = Arrays.copyOf(bytes, len); // Do not want to pushback 200 bytes if content is less.	
		pushbackInputStream.unread(copyOf);
		return pushbackInputStream;
	}

	private boolean emptyExceptDeclaration(String data) {
		return data.substring(data.indexOf("?>") + 2).trim().isEmpty();
	}
	
	private CmsItemPropertiesMap getProperties(CmsItemId baseId, TransformConfig config) {
		
		CmsItemPropertiesMap m = new CmsItemPropertiesMap();
		final boolean propertiesSuppress = Boolean.valueOf(config.getOptions().getParams().get("PropertiesSuppress"));
		
		// TODO: Include rev if configured to do so.
		baseId = baseId.withPegRev(null); // Remove revision to avoid commit on items that have not changed.
		
		if (!propertiesSuppress) {
			m.put(TRANSFORM_BASE_PROP_KEY, baseId.getLogicalId());
			m.put(TRANSFORM_NAME_PROP_KEY, config.getName());
		}
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
	
	private String decodeHref(String href) {
        href = java.net.URLDecoder.decode(href, StandardCharsets.UTF_8);
        return href;
	}
	
	private static boolean isCmsClass(CmsItemProperties properties, String cmsClass) {

		if (properties == null) {
			return false;
		}

		String classes = properties.getString("cms:class");
		if (classes == null || classes.isEmpty()) {
			return false;
		}
		
		String[] a = classes.split(" ");
		return Arrays.asList(a).contains(cmsClass);
	}
	
	private class EmptyStreamException extends Exception {

		private static final long serialVersionUID = 1L;
		
		public EmptyStreamException(String message) {
			super(message);
		}
		
	}
}
