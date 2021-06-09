package com.liferay.project.reference.replacer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.util.Pair;
import org.gradle.tooling.model.idea.IdeaModule;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.InternalIdeaSingleEntryLibraryDependency;

import java.util.Map;

/**
 * @author Dante Wang
 */
public class GradleDependencyReplacerExt
	extends AbstractProjectResolverExtension {

	@Override
	public void populateModuleDependencies(
		@NotNull IdeaModule gradleModule,
		@NotNull DataNode<ModuleData> ideModule,
		@NotNull DataNode<ProjectData> ideProject) {

		super.populateModuleDependencies(gradleModule, ideModule, ideProject);

		if (gradleModule.getName().equals("blogs-web")) {

			Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
				ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS);

			for (Pair<DataNode<GradleSourceSetData>, ExternalSourceSet> pair : sourceSetMap.values()) {
				_log.info(pair.getFirst().getData().toString());
			}
		}
	}

	private static final Logger _log = Logger.getInstance(
		GradleDependencyReplacerExt.class);

}