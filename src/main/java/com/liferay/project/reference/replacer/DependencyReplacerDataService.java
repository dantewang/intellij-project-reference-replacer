package com.liferay.project.reference.replacer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * @author Dante Wang
 */
@Order(ExternalSystemConstants.UNORDERED)
public class DependencyReplacerDataService extends AbstractProjectDataService<LibraryDependencyData, Module> {

    @NotNull
    @Override
    public Key<LibraryDependencyData> getTargetDataKey() {
        return ProjectKeys.LIBRARY_DEPENDENCY;
    }

    @Override
    public void onSuccessImport(
        @NotNull Collection<DataNode<LibraryDependencyData>> libraryDependencyDataNodes,
        @Nullable ProjectData projectData, @NotNull Project project,
        @NotNull IdeModelsProvider modelsProvider) {

        ModuleManager moduleManager = ModuleManager.getInstance(project);

        if(projectData == null) {
            return;
        }

        for (DataNode<LibraryDependencyData> libraryDependencyDataNode : libraryDependencyDataNodes) {
            LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();

            String dependencyExternalName = libraryDependencyData.getExternalName();

            for (Map.Entry<String, String> artifactMapping : _artifactMappings.entrySet()) {
                if (!dependencyExternalName.startsWith(artifactMapping.getKey())) {
                    continue;
                }

                _doReplaceDependencyWithModuleReference(
                    moduleManager, dependencyExternalName, libraryDependencyData.getOwnerModule(),
                    artifactMapping.getValue());
            }
        }
    }

    private void _doReplaceDependencyWithModuleReference(
        @NotNull ModuleManager moduleManager, @NotNull String dependencyExternalName,
        @NotNull ModuleData ownerModuleData, @NotNull String targetModuleName) {

        Module targetModule = moduleManager.findModuleByName(targetModuleName);

        if (targetModule == null) {
            _log.debug(
                "Target Module {0} for dependency {1} is not found from this project.", targetModuleName,
                dependencyExternalName);

            return;
        }

        Module ownerModule = moduleManager.findModuleByName(ownerModuleData.getInternalName());

        if (targetModule == null) {
            return;
        }

        ModuleRootModificationUtil.addDependency(ownerModule, targetModule);
    }

    private static final String _GROUP_ID = "com.liferay.portal";

    private static final Map<String, String> _artifactMappings = new HashMap<String, String>() {
        {
            put(_GROUP_ID + ":com.liferay.portal.impl", "portal-impl");
            put(_GROUP_ID + ":com.liferay.portal.kernel", "portal-kernel");
        }
    };

    private static final Logger _log = Logger.getInstance(DependencyReplacerDataService.class);

}