package se.simonsoft.cms.transform.service;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

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
import se.simonsoft.cms.item.commit.FileReplace;
import se.simonsoft.cms.item.commit.FolderAdd;
import se.simonsoft.cms.item.commit.FolderExist;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.info.CmsItemNotFoundException;
import se.simonsoft.cms.item.info.CmsRepositoryLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceDocumentS9api;
import se.simonsoft.cms.xmlsource.transform.TransformOptions;
import se.simonsoft.cms.xmlsource.transform.TransformerService;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXsl implements TransformService {
	
	private final CmsCommit commit;
	private final CmsItemLookup itemLookup;
	private final TransformerServiceFactory transformerServiceFactory;
	private final CmsRepositoryLookup repoLookup;
	private final Map<String, Source> stylesheets;
	
	
	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			CmsItemLookup itemLookup,
			CmsRepositoryLookup lookupRepo,
			TransformerServiceFactory transfromerServiceFactory,
			Map<String, Source> stylesheets
			) {
		
		this.commit = commit;
		this.itemLookup = itemLookup;
		this.transformerServiceFactory = transfromerServiceFactory;
		this.stylesheets = stylesheets;
		this.repoLookup = lookupRepo;
	}

	@Override
	public RepoRevision transform(CmsItem item, TransformConfig config) {
		
		if (!config.getOptions().getType().equals("xsl")) {
			throw new IllegalArgumentException("TransformServiceXsl can only handle xsl transforms but was given: " + config.getOptions().getType());
		}
		//TODO: Should the file be locked? what happens if there is concurrent modifications happening?
		logger.debug("Starting transform of item: {}", item.getId());
		
		final CmsItemId itemId = item.getId();
		final CmsRepository repository = itemId.getRepository();
		final Source stylesheetSource = getStylesheetSource(itemId, config);
		
		final TransformerService transformerService = transformerServiceFactory.buildTransformerService(stylesheetSource);
		transformerService.setItemLookup(itemLookup);
		
		// Getting the base revision before all item lookups. Hoping that will help catch concurrent operations (svn will refuse copyfrom higher than base?)
		RepoRevision baseRevision = repoLookup.getYoungest(repository);  
		
		XmlSourceDocumentS9api transformed = transformerService.transform(itemId, null);
		//TODO: Check if empty? should be omitted if it is. how?
		
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
		
		RepoRevision rev = commit.run(patchset);
		logger.debug("Rev after commit: {}", rev.getNumber());
		
		return rev;
	}
	
	private Source getStylesheetSource(CmsItemId itemId, TransformConfig config) {
		final String stylesheetPath = config.getOptions().getParams().get("stylesheet");
		Source resultSource;
		
		if (stylesheetPath == null || stylesheetPath.trim().isEmpty()) {
			throw new IllegalArgumentException("Requires a valid stylesheet path or stylesheet name.");
		}
		
		if (stylesheetPath.startsWith("/")) {
			CmsItemId styleSheetItemId = new CmsItemIdArg(itemId.getRepository(), new CmsItemPath(stylesheetPath));
			logger.debug("Using stylesheet from CMS: {}", styleSheetItemId.getLogicalId());
			CmsItem styleSheetItem = itemLookup.getItem(styleSheetItemId);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			styleSheetItem.getContents(baos);
			resultSource = new StreamSource(baos.toInputStream());
		} else {
			logger.debug("Using CMS built in stylesheet: {}", stylesheetPath);
			resultSource = stylesheets.get(stylesheetPath); 
		}
		
		return resultSource;
	}
	
	private CmsItemPath getOutputPath(CmsItemId itemId, String output) {
		
		final CmsRepository repo = itemId.getRepository();
		CmsItemId itemIdOutput;
		
		if (output != null && !output.trim().isEmpty()) {
			itemIdOutput = new CmsItemIdArg(repo, new CmsItemPath(output));
			logger.debug("Output folder is specified: {}", output);
		} else {
			itemIdOutput = new CmsItemIdArg(repo, itemId.getRelPath().getParent());
			logger.debug("Output folder is not specified will default to items parent folder.");
		}
		
		//Is there any other way to check if the folder exists, with CmsCommit?
		try {
			itemLookup.getItem(itemIdOutput);
		} catch (CmsItemNotFoundException e) {
			throw new IllegalArgumentException("Output folder '" + output + "' does not exist in repo '" + repo.getName() + "'.");
		}
		
		return itemIdOutput.getRelPath();
	}

}
