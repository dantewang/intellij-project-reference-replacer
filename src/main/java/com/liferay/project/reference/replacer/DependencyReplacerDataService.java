package com.liferay.project.reference.replacer;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.service.project.IdeModifiableModelsProvider;
import com.intellij.openapi.externalSystem.service.project.manage.AbstractProjectDataService;
import com.intellij.openapi.externalSystem.util.ExternalSystemConstants;
import com.intellij.openapi.externalSystem.util.Order;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

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
    public void importData(
        @NotNull Collection<DataNode<LibraryDependencyData>> libraryDependencyDataNodes,
        @Nullable ProjectData projectData, @NotNull Project project,
        @NotNull IdeModifiableModelsProvider modelsProvider) {

        if(projectData == null) {
            return;
        }

        for (DataNode<LibraryDependencyData> libraryDependencyDataNode : libraryDependencyDataNodes) {
            LibraryDependencyData libraryDependencyData = libraryDependencyDataNode.getData();

            String externalName = libraryDependencyData.getExternalName();

            System.out.println(externalName);

            for (Map.Entry<String, String> artifactMapping : _artifactMappings.entrySet()) {
                if (!externalName.startsWith(artifactMapping.getKey())) {
                    continue;
                }

                _doReplaceDependencyWithModuleReference();
            }
        }
    }

    private void _doReplaceDependencyWithModuleReference() {
    }

    private static final String _GROUP_ID = "com.liferay.portal";

    private static final Map<String, String> _artifactMappings = new HashMap<String, String>() {
        {
            put(_GROUP_ID + ":com.liferay.portal.impl", "portal-impl");
            put(_GROUP_ID + ":com.liferay.portal.kernel", "portal-kernel");
        }
    };

}