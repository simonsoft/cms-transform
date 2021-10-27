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

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.Multibinder;

import net.sf.saxon.lib.ExtensionFunctionDefinition;
import net.sf.saxon.s9api.Processor;
import se.repos.indexing.IndexingItemHandler;
import se.simonsoft.cms.xmlsource.SaxonConfiguration;
import se.simonsoft.cms.xmlsource.handler.XmlSourceReader;
import se.simonsoft.cms.xmlsource.handler.s9api.XmlSourceReaderS9api;
import se.simonsoft.cms.xmlsource.transform.TransformStylesheetSource;
import se.simonsoft.cms.xmlsource.transform.TransformStylesheetSourceConfig;
import se.simonsoft.cms.xmlsource.transform.TransformerServiceFactory;
import se.simonsoft.cms.xmlsource.transform.function.GetChecksum;
import se.simonsoft.cms.xmlsource.transform.function.GetLogicalId;
import se.simonsoft.cms.xmlsource.transform.function.GetPegRev;
import se.simonsoft.cms.xmlsource.transform.function.WithPegRev;

public class TestTransformModule extends AbstractModule{

	public TestTransformModule() {
	}

	@Override
	protected void configure() {
		bind(Processor.class).toProvider(SaxonConfiguration.class);
		Multibinder<ExtensionFunctionDefinition> transformerFunctions = Multibinder.newSetBinder(binder(), ExtensionFunctionDefinition.class);
		transformerFunctions.addBinding().to(GetChecksum.class);
		transformerFunctions.addBinding().to(GetPegRev.class);
		transformerFunctions.addBinding().to(WithPegRev.class);
		transformerFunctions.addBinding().to(GetLogicalId.class);
		bind(XmlSourceReader.class).to(XmlSourceReaderS9api.class);

		Map<String, String> stylesheets = TransformerServiceFactory.getStylesheetsForTestingMap();
		// Just for cms-transform testing.
		stylesheets.put("transform-multiple-output.xsl", "se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-multiple-output.xsl");
		stylesheets.put("transform-single-output.xsl", "se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-single-output.xsl");
		bind(TransformStylesheetSource.class).toInstance(new TransformStylesheetSourceConfig(stylesheets));
		
		Multibinder<IndexingItemHandler> handlers = Multibinder.newSetBinder(binder(), IndexingItemHandler.class);
	}

}
