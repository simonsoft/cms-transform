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

import java.util.UUID;

import jakarta.annotation.Priority;
import jakarta.enterprise.context.RequestScoped;
import jakarta.enterprise.inject.Alternative;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.tmatesoft.svn.core.auth.BasicAuthenticationManager;
import org.tmatesoft.svn.core.auth.ISVNAuthenticationManager;
import se.simonsoft.cms.backend.svnkit.config.CmsRepositoryName;
import se.simonsoft.svn.runtime.RepoId;

/** The test application's repository/identity selection, initialized before resolving backend services. */
@RequestScoped
public class TestTransformContext {

	private String repositoryId = "test-" + UUID.randomUUID();
	private String repositoryName = "repo1";
	private String username;
	private String password;

	@Inject
	public TestTransformContext(@ConfigProperty(name = "quarkus.svn.username") String username,
			@ConfigProperty(name = "quarkus.svn.password") String password) {
		this.username = username;
		this.password = password;
	}

	public void select(String repositoryId, String repositoryName, String username, String password) {
		this.repositoryId = repositoryId;
		this.repositoryName = repositoryName;
		this.username = username;
		this.password = password;
	}

	@Produces @RepoId
	public String repositoryId() {
		return repositoryId;
	}

	@Produces @RequestScoped @Alternative @Priority(10)
	public CmsRepositoryName repositoryName() {
		return new CmsRepositoryName(repositoryName);
	}

	@Produces @RequestScoped @Alternative @Priority(10)
	public ISVNAuthenticationManager authentication() {
		return BasicAuthenticationManager.newInstance(username, password.toCharArray());
	}
}
