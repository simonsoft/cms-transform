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
