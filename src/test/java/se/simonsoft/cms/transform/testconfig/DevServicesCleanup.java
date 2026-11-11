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

import org.junit.platform.launcher.LauncherSession;
import org.junit.platform.launcher.LauncherSessionListener;

import io.quarkus.devservices.crossclassloader.runtime.DevServiceOwner;
import io.quarkus.devservices.crossclassloader.runtime.RunningDevServicesRegistry;

/** Close this test JVM's Solr and SVN Dev Services at the end of the suite or on JVM shutdown. */
public final class DevServicesCleanup implements LauncherSessionListener {

	private final Thread shutdownHook = new Thread(this::stopDevServices, "stop-transform-test-containers");

	@Override
	public void launcherSessionOpened(LauncherSession session) {
		Runtime.getRuntime().addShutdownHook(shutdownHook);
	}

	@Override
	public void launcherSessionClosed(LauncherSession session) {
		stopDevServices();
		try {
			Runtime.getRuntime().removeShutdownHook(shutdownHook);
		} catch (IllegalStateException ignored) {
			// An interrupted fork may already be running the same cleanup.
		}
	}

	private synchronized void stopDevServices() {
		var registry = RunningDevServicesRegistry.INSTANCE;
		try {
			registry.closeAllRunningServices(new DevServiceOwner("solr", "TEST", null));
		} finally {
			registry.closeAllRunningServices(new DevServiceOwner("svn", "TEST", null));
		}
	}
}
