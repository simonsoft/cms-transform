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
package se.simonsoft.cms.transform.event;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonSetter;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.workflow.WorkflowItemInputUserId;
import se.simonsoft.cms.transform.config.databind.TransformImportOptions;

public class TransformImportWorkflowInput implements WorkflowItemInputUserId {

	private final CmsItemId itemId;
	private final TransformImportOptions options;
	private String userId;
	private String userRoles;

	public TransformImportWorkflowInput(CmsItemId itemId, TransformImportOptions options) {
		this.itemId = itemId;
		this.options = options;
	}

	@Override
	public String getAction() {
		return "transform-import";
	}

	@Override
	@JsonGetter("itemid") // Defined by the interface if the writer configure forType(WorkflowItemInput.class). 
	public CmsItemId getItemId() {
		return itemId;
	}

	@Override
	@JsonGetter("userid") // Defined by the interface if the writer configure forType(WorkflowItemInputUserId.class). 
	public String getUserId() {
		return this.userId;
	}

	@Override
	@JsonSetter("userid")
	public void setUserId(String userId) {
		this.userId = userId;
	}

	@Override
	public TransformImportOptions getOptions() {
		return options;
	}

	@Override
	@JsonGetter("userroles") // Defined by the interface if the writer configure forType(WorkflowItemInputUserId.class). 
	public String getUserRoles() {
		return this.userRoles;
	}

	@Override
	@JsonSetter("userroles")
	public void setUserRoles(String userRoles) {
		this.userRoles = userRoles;
	}

}
