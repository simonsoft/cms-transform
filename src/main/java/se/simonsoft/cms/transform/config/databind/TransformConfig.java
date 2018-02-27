package se.simonsoft.cms.transform.config.databind;

public class TransformConfig {
	
	private boolean active;
	private TransformConfigOptions options;
	
	public boolean isActive() {
		return active;
	}
	
	public void setActive(boolean active) {
		this.active = active;
	}
	
	public TransformConfigOptions getOptions() {
		return options;
	}
	
	public void setOptions(TransformConfigOptions options) {
		this.options = options;
	}

	
}