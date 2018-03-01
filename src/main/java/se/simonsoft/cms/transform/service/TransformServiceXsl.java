package se.simonsoft.cms.transform.service;

import java.io.ByteArrayInputStream;
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
import se.simonsoft.cms.item.CmsItemPath;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.RepoRevision;
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.commit.CmsPatchset;
import se.simonsoft.cms.item.commit.FileAdd;
import se.simonsoft.cms.item.commit.FileModification;
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
		
		final TransformerService transformerService = transformerServiceFactory.buildTransformerService(stylesheetSource);
		transformerService.setItemLookup(itemLookup);
		
		SaxonOutputURIResolverXdm outputURIResolver = new SaxonOutputURIResolverXdm(sourceReader);
		
		TransformOptions transformOptions = new TransformOptions();
		transformOptions.setOutputURIResolver(outputURIResolver);
		
		// Getting the base revision before all item lookups. Hoping that will help catch concurrent operations (svn will refuse copyfrom higher than base?)
		RepoRevision baseRevision = repoLookup.getYoungest(repository);
		
		
		CmsItemPath outputFolderPath = getOutputPath(itemId, config.getOptions().getParams().get("output"));
		
		CmsPatchset patchset = new CmsPatchset(repository, baseRevision);
		patchset.setHistoryMessage(config.getOptions().getParams().get("comment"));
		
		Boolean overwrite = new Boolean(config.getOptions().getParams().get("overwrite"));
		CmsItemPath completePath = outputFolderPath.append(itemId.getRelPath().getName());
		logger.debug("complete path: {}", completePath);
		FileAdd fileAdd = new FileAdd(completePath, transformerService.getTransformStreamProvider(transformed, null));
		if (!overwrite) {
			patchset.add(fileAdd);
		} else {
			patchset.add(new FileReplace(fileAdd));
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

}
