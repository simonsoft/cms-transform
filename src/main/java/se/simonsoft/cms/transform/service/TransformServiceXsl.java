package se.simonsoft.cms.transform.service;

import java.io.Reader;
import java.io.Writer;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceDocumentS9api;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceTransformContextS9api;
import se.simonsoft.cms.xmlsource.transform.TransformOptions;
import se.simonsoft.cms.xmlsource.transform.TransformStreamProvider;
import se.simonsoft.cms.xmlsource.transform.TransformerService;

public class TransformServiceXsl implements TransformerService {

	@Override
	public void setItemLookup(CmsItemLookup itemLookup) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void transform(Reader sourceReader, Writer resultWriter, TransformOptions options) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public TransformStreamProvider getTransformStreamProvider(CmsItemId itemId, TransformOptions options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void transform(CmsItemId itemId, Writer resultWriter, TransformOptions options) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public XmlSourceDocumentS9api transform(XmlSourceTransformContextS9api element, TransformOptions options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public void transform(XmlSourceTransformContextS9api element, Writer resultWriter, TransformOptions options) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public TransformStreamProvider getTransformStreamProvider(XmlSourceTransformContextS9api element, TransformOptions options) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public XmlSourceDocumentS9api transform(CmsItemId itemId, TransformOptions options) {
		// TODO Auto-generated method stub
		return null;
	}


}
