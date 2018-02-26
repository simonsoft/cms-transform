package se.simonsoft.cms.transform.config.databind;

import java.util.HashMap;
import java.util.Map;

public class TransformImportConfigOptions {
	
	private String type;
	private Map <String, String> params = new HashMap<>();
	
	public String getType() {
		return type;
	}
	
	public void setType(String type) {
		this.type = type;
	}
	
	public Map <String, String> getParams() {
		return params;
	}
	
	public void setParams(Map <String, String> params) {
		this.params = params;
	}
	
}
