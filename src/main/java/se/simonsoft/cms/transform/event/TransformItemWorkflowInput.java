package se.simonsoft.cms.transform.event;

import com.fasterxml.jackson.annotation.JsonGetter;

import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.workflow.WorkflowItemInputUserId;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

class TransformItemWorkflowInput implements WorkflowItemInputUserId {

	private final CmsItemId itemId;
	// Using TransformConfig instead of TransformConfigOptions is inconsistent but the service needs the "name" parameter.
	private final TransformConfig options;
	private String userId;

	public TransformItemWorkflowInput(CmsItemId itemId, TransformConfig options) {
		this.itemId = itemId;
		this.options = options;
	}

	@Override
	public String getAction() {
		return "transform-item";
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
	public void setUserId(String userId) {
		this.userId = userId;
	}

	@Override
	public TransformConfig getOptions() {
		return options;
	}

}
