package se.simonsoft.cms.transform.config.databind;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

public class TransformImportConfig {
	
	private boolean active;
	private TransformImportConfigOptions options;
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean active) {
		this.active = active;
	}
	
	public TransformImportConfigOptions getOptions() {
		return options;
	}
	
	public void setOptions(TransformImportConfigOptions options) {
		this.options = options;
	}

	
}