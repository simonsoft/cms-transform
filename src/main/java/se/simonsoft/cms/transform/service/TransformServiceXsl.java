package se.simonsoft.cms.transform.service;

import java.io.ByteArrayInputStream;
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
import se.simonsoft.cms.item.commit.CmsCommit;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.transform.config.databind.TransformConfig;
import se.simonsoft.cms.xmlsource.transform.TransformOptions;
import se.simonsoft.cms.xmlsource.transform.TransformerService;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;

public class TransformServiceXsl implements TransformService {
	
	private final CmsCommit commit;
	private final Map<CmsRepository,CmsItemLookup> itemLookups;
	private final TransformerServiceFactory transformerServiceFactory;
	private final Map<String, Source> stylesheets;
	
	
	private static final Logger logger = LoggerFactory.getLogger(TransformServiceXsl.class);

	@Inject
	public TransformServiceXsl(
			CmsCommit commit,
			Map<CmsRepository,CmsItemLookup> itemLookup,
			TransformerServiceFactory transfromerServiceFactory,
			Map<String, Source> stylesheets
			) {
		
		this.commit = commit;
		this.itemLookups = itemLookup;
		this.transformerServiceFactory = transfromerServiceFactory;
		this.stylesheets = stylesheets;
	}

	@Override
	public void transform(CmsItem item, TransformConfig config) {
		
		if (!config.getOptions().getType().equals("xsl")) {
			throw new IllegalArgumentException("TransformServiceXsl can only handle xsl transforms but was given: " + config.getOptions().getType());
		}
		
		final CmsItemId itemId = item.getId();
		final CmsItemLookup lookup = itemLookups.get(itemId.getRepository());
		final Source stylesheetPath = getStylesheetSource(itemId, config);
		
		final TransformerService transformerService = transformerServiceFactory.buildTransformerService(stylesheetPath);
		transformerService.setItemLookup(lookup);
		
	}
	
	private Source getStylesheetSource(CmsItemId itemId, TransformConfig config) {
		final String stylesheetPath = config.getOptions().getParams().get("stylesheet");
		Source resultSource;
		
		if (stylesheetPath == null || stylesheetPath.trim().isEmpty()) {
			throw new IllegalArgumentException("Requires a valid stylesheet path or stylesheet name.");
		}
		
		if (stylesheetPath.startsWith("/")) {
			CmsItemId styleSheetItemId = new CmsItemIdArg(itemId.getRepository(), new CmsItemPath(stylesheetPath));
			CmsItemLookup lookupStylesheet = itemLookups.get(styleSheetItemId.getRepository());
			CmsItem styleSheetItem = lookupStylesheet.getItem(styleSheetItemId);
			
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			styleSheetItem.getContents(baos);
			resultSource = new StreamSource(new ByteArrayInputStream(baos.toByteArray()));
		} else {
			resultSource = stylesheets.get(stylesheetPath); 
		}
		
		return resultSource;
	}

}
