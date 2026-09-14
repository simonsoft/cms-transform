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
package se.simonsoft.cms.transform.config.databind;

import java.util.HashMap;
import java.util.Map;

public class TransformConfigOptions {

	private String type;
	private Map <String, String> params = new HashMap<>();
	private Map <String, String> revprops = new HashMap<>(); // SVN revision properties to set on the transform's commit. Keys must be 'prefix:name'.

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

	public Map <String, String> getRevprops() {
		return revprops;
	}

	public void setRevprops(Map <String, String> revprops) {
		this.revprops = revprops;
	}

}
