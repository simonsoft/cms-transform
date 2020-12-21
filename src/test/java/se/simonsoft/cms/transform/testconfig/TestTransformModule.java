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

import javax.xml.transform.Source;
import javax.xml.transform.stream.StreamSource;

import com.google.inject.AbstractModule;
import com.google.inject.multibindings.MapBinder;

public class TestTransformModule extends AbstractModule{

	public TestTransformModule() {
	}

	@Override
	protected void configure() {
		MapBinder<String, Source> sourceBinder = MapBinder.newMapBinder(binder(), String.class, Source.class);
		sourceBinder.addBinding("identity.xsl").toInstance(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/xmlsource/transform/identity.xsl")));
		// Just for cms-transform testing.
		sourceBinder.addBinding("transform-multiple-output.xsl").toInstance(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-multiple-output.xsl")));
		sourceBinder.addBinding("transform-single-output.xsl").toInstance(new StreamSource(this.getClass().getClassLoader().getResourceAsStream("se/simonsoft/cms/transform/datasets/repo1/stylesheet/transform-single-output.xsl")));
	}

}
