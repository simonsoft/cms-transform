package se.simonsoft.cms.transform.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Map;
import java.util.Set;

import javax.inject.Inject;
import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsItemLock;
import se.simonsoft.cms.item.CmsItemLockCollection;
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.commit.FileAdd;
import se.simonsoft.cms.item.commit.FileModificationLocked;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
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
	private final Map<String, Source> stylesheets;
	private final XmlSourceReaderS9api sourceReader;
	private final TransformerService transformerIdentity;
	
	private static final String TRANSFORM_LOCK_COMMENT = "Locked for transform";
	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			CmsRepositoryLookup lookupRepo,
			TransformerServiceFactory transfromerServiceFactory,
			Map<String, Source> stylesheets,
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
	public RepoRevision transform(CmsItem item, TransformConfig config) {
		
		final CmsItem base = item;
		final CmsItemId itemId = base.getId();
		final CmsRepository repository = itemId.getRepository();
		
		if (!config.getOptions().getType().equals("xsl")) {
			throw new IllegalArgumentException("TransformServiceXsl can only handle xsl transforms but was given: " + config.getOptions().getType());
		}
		
		final String stylesheet = config.getOptions().getParams().get("stylesheet");
		if (stylesheet == null || stylesheet.trim().isEmpty()) {
			throw new IllegalArgumentException("Requires a valid stylesheet path or stylesheet name.");
		}
		
		final CmsItemPath outputPath = getOutputPath(itemId ,config.getOptions().getParams().get("output"));
		if (!pathExists(repository, outputPath)) {
			throw new IllegalArgumentException("Specified output must be an existing folder: " + outputPath.getPath());
		}
		
		final Source stylesheetSource = getStylesheetSource(itemId, stylesheet);
		if (stylesheetSource == null) {
			throw new IllegalArgumentException("Could not find specified stylesheet: " + stylesheet);
		}
		
		final RepoRevision baseRevision = repoLookup.getYoungest(repository);
		final Boolean overwrite = new Boolean(config.getOptions().getParams().get("overwrite"));
		
		final TransformerService transformerService = transformerServiceFactory.buildTransformerService(stylesheetSource);
		transformerService.setItemLookup(itemLookup);
		
		final CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		patchset.setHistoryMessage(config.getOptions().getParams().get("comment"));
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		TransformOptions transformOptions = new TransformOptions();
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		TransformStreamProvider baseStreamProvider = transformerService.getTransformStreamProvider(itemId, transformOptions);
		addToPatchset(patchset, outputPath.append(itemId.getRelPath().getName()), baseStreamProvider, overwrite);
		
		Set<String> resultDocsHrefs = outputURIResolver.getResultDocumentHrefs();
		for (String relpath: resultDocsHrefs) {
			XmlSourceDocumentS9api resultDocument = outputURIResolver.getResultDocument(relpath);
			TransformStreamProvider streamProvider = transformerIdentity.getTransformStreamProvider(resultDocument, null);
			
			CmsItemPath path = new CmsItemPath(outputPath.getPath().concat("/").concat(relpath));
			addToPatchset(patchset, path, streamProvider, overwrite);
		}
		
		commit.run(patchset);
		
		for (CmsItemLock l: patchset.getLocks()) {	
			commit.unlock(l);
		}

		return null;
	}
	
	private void addToPatchset(CmsPatchset patchset, CmsItemPath path, TransformStreamProvider streamProvider, boolean overwrite) {

		try {
			
			InputStream transformStream = checkStreamIsNotEmpty(streamProvider.get());
			
			boolean pathExists = pathExists(patchset.getRepository(), path);
			if (!pathExists) {
				logger.debug("No file at path: '{}' will add new file.", path);
				FileAdd fileAdd = new FileAdd(path, transformStream);
				patchset.add(fileAdd);
			} else if (overwrite){
				logger.debug("Overwrite is allowed, existing file at path '{}' will be modified.", path.getPath());
				CmsItemId itemId = new CmsItemIdArg(patchset.getRepository(), path);
				CmsItemLockCollection locks = commit.lock(TRANSFORM_LOCK_COMMENT, patchset.getBaseRevision(), itemId.getRelPath());
				if (locks != null && locks.getSingle() == null) {
					throw new IllegalStateException("Unable to retrieve the lock token after locking " + itemId);
				}
				patchset.addLock(locks.getSingle());
				patchset.add(new FileModificationLocked(path, transformStream));
			} else {
				throw new IllegalStateException("Item already exists, config prohibiting overwrite of existing items.");
			}

		} catch (IOException e) {
			throw new RuntimeException("Failed to read stream from transform.", e);
		} catch (EmptyStreamException e) {
			logger.warn("Transform of item at path: '{}'  resulted in empty document, will be discarded.", path);
		}

	}

	private CmsItemPath getOutputPath(CmsItemId itemId, String output) {
		
		CmsItemPath pathResult;
		
		if (output == null || output.trim().isEmpty()) {
			pathResult = new CmsItemIdArg(itemId.getRepository(), itemId.getRelPath().getParent()).getRelPath();
			logger.debug("Output folder is not specified will default to items parent folder.");
		} else {
			pathResult = new CmsItemIdArg(itemId.getRepository(), new CmsItemPath(output)).getRelPath();
			logger.debug("Output folder is specified: {}", output);
		}
		
		return pathResult;
	}

	private Source getStylesheetSource(CmsItemId itemId, String stylesheet) {
		
		Source resultSource;
		
		if (stylesheet.startsWith("/")) {
			CmsItemId styleSheetItemId = new CmsItemIdArg(itemId.getRepository(), new CmsItemPath(stylesheet));
			logger.debug("Using stylesheet from CMS: {}", styleSheetItemId.getLogicalId());
			CmsItem styleSheetItem = itemLookup.getItem(styleSheetItemId);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream(); //safe to load the styleSheet in to memory?
			styleSheetItem.getContents(baos);
			resultSource = new StreamSource(baos.toInputStream());
		} else {
			logger.debug("Using CMS built in stylesheet: {}", stylesheet);
			resultSource = stylesheets.get(stylesheet); 
		}
		
		return resultSource;
	}
	
	private boolean pathExists(CmsRepository repo, CmsItemPath path) {
		
		CmsItemId itemIdOutput = new CmsItemIdArg(repo, path);
		boolean result = false;
		
		try {
			itemLookup.getItem(itemIdOutput);
			result = true;
		} catch (CmsItemNotFoundException e) {
			result = false;
		}
		
		return result;
	}
	
	private InputStream checkStreamIsNotEmpty(InputStream inputStream) throws IOException, EmptyStreamException {
		PushbackInputStream pushbackInputStream = new PushbackInputStream(inputStream);
		int b;
		b = pushbackInputStream.read();
		if ( b == -1 ) {
			throw new EmptyStreamException("Transform is empty");
		}
		pushbackInputStream.unread(b);
		return pushbackInputStream;
	}
	
	private class EmptyStreamException extends Exception {

		private static final long serialVersionUID = 1L;
		
		public EmptyStreamException(String message) {
			super(message);
		}
		
	}

}
