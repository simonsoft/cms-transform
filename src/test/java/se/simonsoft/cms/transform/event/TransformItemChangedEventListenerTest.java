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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import io.quarkus.test.junit.QuarkusTest;
import se.simonsoft.cms.transform.testconfig.SvnDatasetTest;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import jakarta.inject.Inject;
import io.quarkus.arc.ClientProxy;

import se.simonsoft.cms.item.CmsItem;
import se.simonsoft.cms.item.CmsItemId;
import se.simonsoft.cms.item.CmsRepository;
import se.simonsoft.cms.item.impl.CmsItemIdArg;
import se.simonsoft.cms.item.info.CmsItemLookup;
import se.simonsoft.cms.item.workflow.WorkflowExecutionException;
import se.simonsoft.cms.item.workflow.WorkflowExecutor;
import se.simonsoft.cms.item.workflow.WorkflowItemInput;
import se.simonsoft.cms.transform.config.TransformConfiguration;
import se.simonsoft.cms.transform.config.databind.TransformConfig;

@QuarkusTest
public class TransformItemChangedEventListenerTest extends SvnDatasetTest {
	

	@Inject TransformConfiguration transformConfig;
	private Map<CmsRepository, CmsItemLookup> lookups;
	private String userId = null;

	static final String transformTestDoc = "x-svn:///svn/repo1/doc/transform-test.xml";

	@BeforeEach
	public void setup() {
		lookups = Map.of(repo, lookup);
	}

	@Test
	public void testEventListenerGetsConfigs() throws WorkflowExecutionException {
		
		TransformConfiguration spyTransformConfig = spy(ClientProxy.unwrap(transformConfig));
		WorkflowExecutor<WorkflowItemInput> spyWfExec = spy(WorkflowExecutor.class);
		
		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc).withPegRev(1L);
		CmsItem item = lookup.getItem(itemId);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, lookups, userId, spyWfExec);
		eventListener.onItemChange(item);
		
		ArgumentCaptor<CmsItemId> itemIdCaptor = ArgumentCaptor.forClass(CmsItemId.class);
		verify(spyTransformConfig, times(1)).getConfiguration(itemIdCaptor.capture());
		
		CmsItemId value = itemIdCaptor.getValue();
		assertEquals(itemId, value);
		
		ArgumentCaptor<WorkflowItemInput> argCaptorConfig = ArgumentCaptor.forClass(WorkflowItemInput.class);
		verify(spyWfExec, times(2)).startExecution(argCaptorConfig.capture());
		
		List<WorkflowItemInput> allConfigs = argCaptorConfig.getAllValues();
		Collections.sort(allConfigs, new WfInputComparator());
		assertEquals(2, allConfigs.size());
		assertEquals("multiple", ((TransformItemWorkflowInput) allConfigs.get(0)).getOptions().getName());
		assertEquals("single", ((TransformItemWorkflowInput) allConfigs.get(1)).getOptions().getName());
	}
	
	@Test
	public void testFailsIfNoPegrev() {
		
		TransformConfiguration spyTransformConfig = spy(ClientProxy.unwrap(transformConfig));
		WorkflowExecutor<WorkflowItemInput> spyWfExec = spy(WorkflowExecutor.class);

		
		CmsItemId itemId = new CmsItemIdArg(transformTestDoc);
		CmsItem item = lookup.getItem(itemId);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, lookups, userId, spyWfExec);
		try {
			eventListener.onItemChange(item);
			fail("Should fail");
		} catch (IllegalArgumentException e) {
			assertEquals("Item requires a revision to be transformed.", e.getMessage());
		}
	}
	
	
	@Test
	public void testFolderInputNoTransform() throws WorkflowExecutionException {
		TransformConfiguration spyTransformConfig = spy(ClientProxy.unwrap(transformConfig));
		WorkflowExecutor<WorkflowItemInput> spyWfExec = spy(WorkflowExecutor.class);
		
		CmsItemId itemIdFolder = new CmsItemIdArg("x-svn:///svn/repo1/doc").withPegRev(1L);
		CmsItem item = lookup.getItem(itemIdFolder);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, lookups, userId, spyWfExec);
		eventListener.onItemChange(item);
		
		verify(spyTransformConfig, times(0)).getConfiguration(Mockito.any(CmsItemId.class));
		verify(spyWfExec, times(0)).startExecution(Mockito.any(WorkflowItemInput.class));
	}
	
	@Test
	public void testItemNotWithinWhitelist() throws WorkflowExecutionException {
		//Repository config white lists /doc and /transformed/single
		
		TransformConfiguration spyTransformConfig = spy(ClientProxy.unwrap(transformConfig));
		WorkflowExecutor<WorkflowItemInput> spyWfExec = spy(WorkflowExecutor.class);
		
		CmsItemId itemIdFolder = new CmsItemIdArg("x-svn:///svn/repo1/transformed/multiple/existing/section1.xml").withPegRev(1L);
		CmsItem item = lookup.getItem(itemIdFolder);
		
		TransformItemChangedEventListener eventListener = new TransformItemChangedEventListener(spyTransformConfig, lookups, userId, spyWfExec);
		eventListener.onItemChange(item);
		
		verify(spyTransformConfig, times(0)).getConfiguration(Mockito.any(CmsItemId.class));
		verify(spyWfExec, times(0)).startExecution(Mockito.any(WorkflowItemInput.class));
		
	}
	
	
	public class ConfigComparator implements Comparator<TransformConfig> {

		@Override
		public int compare(TransformConfig o1, TransformConfig o2) {
			return o1.getName().compareTo(o2.getName());
		}

	}
	
	public class WfInputComparator implements Comparator<WorkflowItemInput> {

		@Override
		public int compare(WorkflowItemInput o1, WorkflowItemInput o2) {
			return ((TransformConfig) o1.getOptions()).getName().compareTo(((TransformConfig) o2.getOptions()).getName());
		}

	}


}
