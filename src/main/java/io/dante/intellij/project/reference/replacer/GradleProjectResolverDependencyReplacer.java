package io.dante.intellij.project.reference.replacer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.IExternalSystemSourceType;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ModuleDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.ExternalSystemApiUtil;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.util.Pair;

import org.gradle.tooling.model.idea.IdeaModule;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.ExternalDependency;
import org.jetbrains.plugins.gradle.model.ExternalProject;
import org.jetbrains.plugins.gradle.model.ExternalSourceDirectorySet;
import org.jetbrains.plugins.gradle.model.ExternalSourceSet;
import org.jetbrains.plugins.gradle.model.data.GradleSourceSetData;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolver;
import org.jetbrains.plugins.gradle.service.project.GradleProjectResolverUtil;
import org.jetbrains.plugins.gradle.service.project.ProjectResolverContext;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/**
 * @author Dante Wang
 */
public class GradleProjectResolverDependencyReplacer extends AbstractProjectResolverExtension {

	@Override
	public void populateModuleDependencies(
		@NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule,
		@NotNull DataNode<ProjectData> ideProject) {

		if (!resolverCtx.isResolveModulePerSourceSet()) {
			return;
		}

		ExternalProject externalProject = Objects.requireNonNull(_getExternalProject(gradleModule, resolverCtx));

		Project intelliJProject = Objects.requireNonNull(_getIntelliJProject(ideProject.getData()));

		ModuleManager moduleManager = ModuleManager.getInstance(intelliJProject);

		Map<String, String> artifactsMap = Objects.requireNonNull(
			ideProject.getUserData(GradleProjectResolver.CONFIGURATION_ARTIFACTS));

		Map<String, Pair<DataNode<GradleSourceSetData>, ExternalSourceSet>> sourceSetMap =
			Objects.requireNonNull(ideProject.getUserData(GradleProjectResolver.RESOLVED_SOURCE_SETS));

		_processSourceSets(
			resolverCtx, gradleModule, externalProject, ideModule,
			(dataNode, sourceSet) -> {
				Collection<ExternalDependency> dependencies = sourceSet.getDependencies();

				Map<String, ExternalDependency> toProcess = new LinkedHashMap<>();

				Iterator<ExternalDependency> iterator = dependencies.iterator();

				while (iterator.hasNext()) {
					ExternalDependency externalDependency = iterator.next();

					String artifactMapped = ReplacementUtil.findReplacementModuleName(
						externalDependency.getGroup() + ":" + externalDependency.getName());

					if (artifactMapped == null) {
						continue;
					}

					iterator.remove();

					toProcess.putIfAbsent(artifactMapped, externalDependency);
				}

				GradleProjectResolverUtil.buildDependencies(
					resolverCtx, sourceSetMap, artifactsMap, dataNode, dependencies, ideProject);

				for (Map.Entry<String, ExternalDependency> entry : toProcess.entrySet()) {
					Module module = moduleManager.findModuleByName(entry.getKey());

					ExternalDependency externalDependency = entry.getValue();

					/*
						TODO: ProjectResolverExtension fails due to ProjectSystemId.IDE
						Using ProjectSystemId.IDE will cause exception in later processing in
						ModuleDependencyDataService, because "IDE" is not recognized as a valid "external" system ID.
					 */
					ModuleData moduleData = new ModuleData(
						module.getName(), ProjectSystemId.IDE, ModuleTypeId.JAVA_MODULE, module.getName(),
						Objects.requireNonNull(
							Objects.requireNonNull(ProjectUtil.guessModuleDir(module)).getCanonicalPath()),
						"");

					ModuleDependencyData moduleDependencyData = new ModuleDependencyData(
						dataNode.getData(), moduleData);

					moduleDependencyData.setOrder(externalDependency.getClasspathOrder());
					moduleDependencyData.setScope(DependencyScope.valueOf(externalDependency.getScope()));

					dataNode.createChild(ProjectKeys.MODULE_DEPENDENCY, moduleDependencyData);
				}
			});
	}

	@Nullable
	private ExternalProject _getExternalProject(
		@NotNull IdeaModule ideaModule, @NotNull ProjectResolverContext resolverCtx) {

		ExternalProject externalProject = resolverCtx.getExtraProject(ideaModule, ExternalProject.class);

		if ((externalProject == null) && resolverCtx.isResolveModulePerSourceSet()) {
			_logger.error("External Project model is missing for module-per-sourceSet import mode.");
		}

		return externalProject;
	}

	@Nullable
	private Project _getIntelliJProject(ProjectData gradleProjectData) {
		ProjectManager projectManager = ProjectManager.getInstance();

		Project[] openIntelliJProjects = projectManager.getOpenProjects();

		for (Project openIntelliJProject : openIntelliJProjects) {
			String basePath = openIntelliJProject.getBasePath();

			_logger.info("Project base path " + basePath);

			String ideProjectFileDirectoryPath = gradleProjectData.getIdeProjectFileDirectoryPath();

			_logger.info("Gradle project ide file directory path" + ideProjectFileDirectoryPath);

			String linkedExternalProjectPath = gradleProjectData.getLinkedExternalProjectPath();

			_logger.info("Gradle project linked external project path" + linkedExternalProjectPath);

			if (ideProjectFileDirectoryPath.startsWith(basePath) || linkedExternalProjectPath.startsWith(basePath)) {
				return openIntelliJProject;
			}
		}

		return null;
	}

	private void _processSourceSets(
		@NotNull ProjectResolverContext resolverCtx, @NotNull IdeaModule gradleModule,
		@NotNull ExternalProject externalProject, @NotNull DataNode<ModuleData> ideModule,
		@NotNull SourceSetsProcessor processor) {

		Map<String, DataNode<GradleSourceSetData>> sourceSetsMap = new HashMap<>();

		for (DataNode<GradleSourceSetData> dataNode :
			ExternalSystemApiUtil.findAll(ideModule, GradleSourceSetData.KEY)) {

			GradleSourceSetData gradleSourceSetData = dataNode.getData();

			sourceSetsMap.put(gradleSourceSetData.getId(), dataNode);
		}

		Map<String, ? extends ExternalSourceSet> externalSourceSetsMap = externalProject.getSourceSets();

		for (ExternalSourceSet sourceSet : externalSourceSetsMap.values()) {
			Map<? extends IExternalSystemSourceType, ? extends ExternalSourceDirectorySet> sources =
				sourceSet.getSources();

			if (sources.isEmpty()) {
				continue;
			}

			final DataNode<? extends ModuleData> moduleDataNode = sourceSetsMap.isEmpty() ? ideModule :
				sourceSetsMap.get(GradleProjectResolverUtil.getModuleId(resolverCtx, gradleModule, sourceSet));

			if (moduleDataNode == null) {
				continue;
			}

			processor.process(moduleDataNode, sourceSet);
		}
	}

	private static final Logger _logger = Logger.getInstance(GradleProjectResolverDependencyReplacer.class);

	private interface SourceSetsProcessor {

		void process(@NotNull DataNode<? extends ModuleData> dataNode, @NotNull ExternalSourceSet sourceSet);

	}

}