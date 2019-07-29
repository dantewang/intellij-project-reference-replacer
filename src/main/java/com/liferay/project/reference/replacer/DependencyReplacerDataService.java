package com.liferay.project.reference.replacer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.DependencyScope;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootModificationUtil;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * @author Dante Wang
 */
@Order(ExternalSystemConstants.UNORDERED)
public class DependencyReplacerDataService
	extends AbstractProjectDataService<LibraryDependencyData, Module> {

	@NotNull
	@Override
	public Key<LibraryDependencyData> getTargetDataKey() {
		return ProjectKeys.LIBRARY_DEPENDENCY;
	}

	@Override
	public void onSuccessImport(
		@NotNull Collection<DataNode<LibraryDependencyData>>
			libraryDependencyDataNodes,
		@Nullable ProjectData projectData, @NotNull Project project,
		@NotNull IdeModelsProvider ideModelsProvider) {

		if(projectData == null) {
			return;
		}

		for (DataNode<LibraryDependencyData> libraryDependencyDataNode :
			libraryDependencyDataNodes) {

			LibraryDependencyData libraryDependencyData =
				libraryDependencyDataNode.getData();

			String dependencyExternalName =
				libraryDependencyData.getExternalName();

			Module ownerModule = null;

			for (Map.Entry<String, String> artifactMapping :
				_artifactMappings.entrySet()) {

				if (!dependencyExternalName.startsWith(
					artifactMapping.getKey())) {

					continue;
				}

				Module targetModule = _artifacts.computeIfAbsent(
					artifactMapping.getValue(),
					ideModelsProvider::findIdeModule);

				if (targetModule == null) {
					continue;
				}

				if (ownerModule == null) {
					ownerModule = ideModelsProvider.findIdeModule(
						libraryDependencyData.getOwnerModule());
				}

				if (ownerModule == null) {
					_log.warn("libraryDependencyData owner not found");

					return;
				}

				LibraryOrderEntry libraryOrderEntry =
					(LibraryOrderEntry)ideModelsProvider.
						findIdeModuleOrderEntry(libraryDependencyData);

				ModuleRootModificationUtil.updateModel(
					ownerModule,
					ownerModuleModel -> {
						ownerModuleModel.removeOrderEntry(
							ownerModuleModel.findLibraryOrderEntry(
								libraryOrderEntry.getLibrary()));

						ModuleOrderEntry moduleOrderEntry =
							ownerModuleModel.addModuleOrderEntry(targetModule);

						moduleOrderEntry.setScope(DependencyScope.COMPILE);
						moduleOrderEntry.setExported(false);
					});
			}
		}
	}

	private static final String _GROUP_ID = "com.liferay.portal";

	private static final Map<String, String> _artifactMappings =
		new HashMap<String, String>() {
			{
				put(_GROUP_ID + ":com.liferay.portal.impl", "portal-impl");
				put(_GROUP_ID + ":com.liferay.portal.kernel", "portal-kernel");
			}
		};

	private static final Logger _log = Logger.getInstance(
		DependencyReplacerDataService.class);

	private final ConcurrentMap<String, Module> _artifacts =
		new ConcurrentHashMap<>();

}