package com.liferay.project.reference.replacer;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.application.WriteAction;
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
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.ModuleOrderEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.impl.ModifiableModelCommitter;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.util.Consumer;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

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

		_log.info(" ### Scan and replace dependencies ### ");

		for (DataNode<LibraryDependencyData> libraryDependencyDataNode :
			libraryDependencyDataNodes) {

			LibraryDependencyData libraryDependencyData =
				libraryDependencyDataNode.getData();

			String dependencyExternalName =
				libraryDependencyData.getExternalName();

			int index = dependencyExternalName.lastIndexOf(':');

			if (index <= 0) {
				continue;
			}

			String dependencyName = dependencyExternalName.substring(0, index);

			String mappedName = _artifactMappings.get(dependencyName);

			if (mappedName == null) {
				_log.debug("No mapping for " + dependencyName);

				continue;
			}

			LibraryOrderEntry originalDependency =
				(LibraryOrderEntry)ideModelsProvider.
					findIdeModuleOrderEntry(libraryDependencyData);

			if (originalDependency == null) {
				continue;
			}

			Module replacementDependency = _artifacts.computeIfAbsent(
				mappedName, ideModelsProvider::findIdeModule);

			if (replacementDependency == null) {
				continue;
			}

			Module owner = ideModelsProvider.findIdeModule(
				libraryDependencyData.getOwnerModule());

			if (owner == null) {
				continue;
			}

			DependencyMapping dependencyMapping =
				_dependencyMappings.computeIfAbsent(
					owner.getName(),
					ownerName -> new DependencyMapping(owner));

			dependencyMapping._dependencyMappingItems.add(
				new DependencyMappingItem(
					originalDependency, replacementDependency));
		}

		_updateModel(
			project, new ArrayList<>(_dependencyMappings.values()),
			dependencyMapping -> {
				ModifiableRootModel ownerModel = dependencyMapping._ownerModel;

				for (DependencyMappingItem dependencyMappingItem :
						dependencyMapping._dependencyMappingItems) {

					Library library =
						dependencyMappingItem._originalDependency.getLibrary();

					if (library == null) {
						continue;
					}

					LibraryOrderEntry libraryOrderEntry =
						ownerModel.findLibraryOrderEntry(library);

					if (libraryOrderEntry == null) {
						continue;
					}

					ownerModel.removeOrderEntry(libraryOrderEntry);

					ModuleOrderEntry moduleOrderEntry =
						ownerModel.addModuleOrderEntry(
							dependencyMappingItem._replacementDependency);

					moduleOrderEntry.setScope(
						dependencyMappingItem._originalDependency.getScope());
					moduleOrderEntry.setExported(false);

					_log.info(
						" ### Modified " + ownerModel.getModule().getName() +
							" for " +
								dependencyMappingItem._replacementDependency.
									getName());
				}
			});
	}

	private void _updateModel(
		@NotNull Project project,
		@NotNull List<DependencyMapping> dependencyMappings,
		@NotNull Consumer<DependencyMapping> task) {

		List<DependencyMapping> populatedMappings = ReadAction.compute(
			() -> dependencyMappings.stream(
				).map(
					dependencyMapping -> {
						ModuleRootManager moduleRootManager =
							ModuleRootManager.getInstance(
								dependencyMapping._owner);

						dependencyMapping._ownerModel =
							moduleRootManager.getModifiableModel();

						return dependencyMapping;
					}
				).collect(
					Collectors.toList()
				));

		try {
			populatedMappings.forEach(task::consume);

			ApplicationManager.getApplication().invokeAndWait(
				() -> WriteAction.run(
					() -> {
						ModuleManager projectModuleManager =
							ModuleManager.getInstance(project);

						ModifiableModuleModel projectModel =
							projectModuleManager.getModifiableModel();

						ModifiableModelCommitter.multiCommit(
							populatedMappings.stream(
							).map(
								dependencyMapping ->
									dependencyMapping._ownerModel
							).collect(
								Collectors.toList()
							),
							projectModel);
					}));
		}
		finally {
			for (DependencyMapping mapping : populatedMappings) {
				if (!mapping._ownerModel.isDisposed()) {
					mapping._ownerModel.dispose();
				}
			}
		}
	}

	private class DependencyMapping {

		private DependencyMapping(@NotNull Module owner) {
			_owner = owner;
		}

		private final List<DependencyMappingItem> _dependencyMappingItems =
			new ArrayList<>();
		private final Module _owner;
		private ModifiableRootModel _ownerModel;

	}

	private class DependencyMappingItem {

		private DependencyMappingItem(
			@NotNull LibraryOrderEntry originalDependency,
			@NotNull Module replacementDependency) {

			_originalDependency = originalDependency;
			_replacementDependency = replacementDependency;
		}

		private final LibraryOrderEntry _originalDependency;
		private final Module _replacementDependency;

	}

	private static final String _GROUP_ID = "com.liferay.portal";

	private static final Map<String, String> _artifactMappings =
		new HashMap<String, String>() {
			{
				put(_GROUP_ID + ":com.liferay.portal.impl", "portal-impl");
				put(_GROUP_ID + ":com.liferay.portal.kernel", "portal-kernel");
				put(_GROUP_ID + ":com.liferay.portal.test", "portal-test");
				put(
					_GROUP_ID + ":com.liferay.portal.test.integration",
					"portal-test-integration");
			}
		};

	private static final Logger _log = Logger.getInstance(
		DependencyReplacerDataService.class);

	private final ConcurrentMap<String, Module> _artifacts =
		new ConcurrentHashMap<>();
	private final ConcurrentMap<String, DependencyMapping> _dependencyMappings =
		new ConcurrentHashMap<>();

}