package com.liferay.project.reference.replacer;

import com.intellij.openapi.externalSystem.model.DataNode;
import com.intellij.openapi.externalSystem.model.project.ModuleData;
import com.intellij.openapi.externalSystem.model.project.ProjectData;
import com.intellij.openapi.externalSystem.util.Order;

import org.gradle.plugins.ide.internal.tooling.idea.DefaultIdeaModuleDependency;
import org.gradle.tooling.model.DomainObjectSet;
import org.gradle.tooling.model.GradleModuleVersion;
import org.gradle.tooling.model.idea.IdeaDependency;
import org.gradle.tooling.model.idea.IdeaDependencyScope;
import org.gradle.tooling.model.idea.IdeaModule;

import org.gradle.tooling.model.idea.IdeaModuleDependency;
import org.gradle.tooling.model.idea.IdeaSingleEntryLibraryDependency;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.service.project.AbstractProjectResolverExtension;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Dante Wang
 */
@Order(Integer.MAX_VALUE - 1)
public class GradleDependencyReplacer extends AbstractProjectResolverExtension {

    @Override
    public void populateModuleDependencies(
        @NotNull IdeaModule gradleModule, @NotNull DataNode<ModuleData> ideModule,
        @NotNull DataNode<ProjectData> ideProject) {

        DomainObjectSet<IdeaDependency> dependencies = (DomainObjectSet<IdeaDependency>)gradleModule.getDependencies();

        Set<IdeaDependency> elements;

        try {
            elements = (Set<IdeaDependency>)getDeclaredFieldValue(dependencies.getClass(), dependencies, "elements");
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }

        Set<IdeaModuleDependency> moduleDependencies = new HashSet<>();

        Iterator<IdeaDependency> itr = dependencies.iterator();

        while (itr.hasNext()) {
            IdeaDependency ideaDependency = itr.next();

            if (!(ideaDependency instanceof IdeaSingleEntryLibraryDependency)) {
                continue;
            }

            IdeaSingleEntryLibraryDependency ideaSingleEntryLibraryDependency =
                (IdeaSingleEntryLibraryDependency)ideaDependency;

            GradleModuleVersion gradleModuleVersion = ideaSingleEntryLibraryDependency.getGradleModuleVersion();

            String groupName = gradleModuleVersion.getGroup();

            if (!_GROUP_NAME.equals(groupName)) {
                continue;
            }

            String artifactName = gradleModuleVersion.getName();

            String moduleName = _artifactMappings.get(artifactName);

            if (moduleName == null) {
                continue;
            }

            itr.remove();

            moduleDependencies.add(
                new IdeaModuleDependency() {

                    @Override
                    public String getTargetModuleName() {
                        return moduleName;
                    }

                    @Override
                    public IdeaModule getDependencyModule() {
                        return null;
                    }

                    @Override
                    public IdeaDependencyScope getScope() {
                        return ideaSingleEntryLibraryDependency.getScope();
                    }

                    @Override
                    public boolean getExported() {
                        return ideaSingleEntryLibraryDependency.getExported();
                    }
                });
        }

        elements.addAll(moduleDependencies);

        nextResolver.populateModuleDependencies(gradleModule, ideModule, ideProject);
    }

    public static Object getDeclaredFieldValue(Class<?> clazz, Object object, String name)
        throws Exception {

        Field field = clazz.getDeclaredField(name);

        field.setAccessible(true);

        return field.get(object);
    }

    private static final String _GROUP_NAME = "com.liferay.portal";

    private static final Map<String, String> _artifactMappings = new HashMap<String, String>() {
        {
            put("com.liferay.portal.impl", "portal-impl");
            put("com.liferay.portal.kernel", "portal-kernel");
        }
    };

}