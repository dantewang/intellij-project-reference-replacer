package io.dante.intellij.project.reference.replacer;

import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.OrderAware;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractDependencyDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.LibraryOrderEntry;
import com.intellij.openapi.roots.ModifiableRootModel;
import com.intellij.openapi.roots.OrderEntry;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

@Order(ExternalSystemConstants.UNORDERED)
public class LibraryDependencyReplacerDataService extends AbstractDependencyDataService<LibraryDependencyData, LibraryOrderEntry> {

	@Override
	public @NotNull Key<LibraryDependencyData> getTargetDataKey() {
		return ProjectKeys.LIBRARY_DEPENDENCY;
	}

	@Override
	protected @NotNull Class<LibraryOrderEntry> getOrderEntryType() {
		return LibraryOrderEntry.class;
	}

	@Override
	protected Map<OrderEntry, OrderAware> importData(
		@NotNull Collection<? extends DataNode<LibraryDependencyData>> nodesToImport, @NotNull Module module,
		@NotNull IdeModifiableModelsProvider modelsProvider) {

		Map<OrderEntry, OrderAware> orderEntryDataMap = new LinkedHashMap<>();

		for (DataNode<LibraryDependencyData> nodeToImport : nodesToImport) {
			LibraryDependencyData libraryDependencyData = nodeToImport.getData();

			String moduleName = ReplacementUtil.findReplacementModuleName(libraryDependencyData);

			if (moduleName == null) {
				continue;
			}

			Module replacementDependencyModule = modelsProvider.findIdeModule(moduleName);

			if (replacementDependencyModule == null) {
				continue;
			}

			try {
				ModifiableRootModel modifiableRootModel = modelsProvider.getModifiableRootModel(module);

				LibraryOrderEntry originalDependency = (LibraryOrderEntry) modelsProvider.findIdeModuleOrderEntry(
					libraryDependencyData);

				assert originalDependency != null;

				Library library = originalDependency.getLibrary();

				assert library != null;

				modifiableRootModel.removeOrderEntry(
					Objects.requireNonNull(modifiableRootModel.findLibraryOrderEntry(library)));

				OrderEntry orderEntry = ReadAction.compute(
					() -> modifiableRootModel.addModuleOrderEntry(replacementDependencyModule));

				orderEntryDataMap.put(orderEntry, libraryDependencyData);
			}
			catch (Exception exception) {
				_log.error("Failed to replace " + module.getName() + "/" + libraryDependencyData.getExternalName());

				if (_log.isDebugEnabled()) {
					_log.debug(exception);
				}
			}
		}

		return orderEntryDataMap;
	}

	private static final Logger _log = Logger.getInstance(LibraryDependencyReplacerDataService.class);

}