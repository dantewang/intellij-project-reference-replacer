package io.dante.intellij.project.reference.replacer;

import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;

import java.util.HashMap;
import java.util.Map;

public class ReplacementUtil {

	public static String findReplacementModuleName(LibraryDependencyData libraryDependencyData) {
		String externalName = libraryDependencyData.getExternalName();

		int index = externalName.lastIndexOf(':');

		if (index <= 0) {
			return null;
		}

		return findReplacementModuleName(externalName.substring(0, index));
	}

	public static String findReplacementModuleName(String artifactKey) {
		return _artifactMappings.get(artifactKey);
	}

	private static final String _GROUP_ID = "com.liferay.portal";

	private static final Map<String, String> _artifactMappings =
		new HashMap<>() {
			{
				put(_GROUP_ID + ":com.liferay.portal.impl", "portal-impl");
				put(_GROUP_ID + ":com.liferay.portal.kernel", "portal-kernel");
				put(_GROUP_ID + ":com.liferay.portal.test", "portal-test");
				put(_GROUP_ID + ":com.liferay.util.bridges", "util-bridges");
				put(_GROUP_ID + ":com.liferay.util.java", "util-java");
				put(_GROUP_ID + ":com.liferay.util.slf4j", "util-slf4j");
				put(_GROUP_ID + ":com.liferay.util.taglib", "util-taglib");
				put(
					_GROUP_ID + ":com.liferay.support.tomcat",
					"support-tomcat");
				put(
					_GROUP_ID + ":com.liferay.portal.test.integration",
					"portal-test-integration");
			}
		};

}