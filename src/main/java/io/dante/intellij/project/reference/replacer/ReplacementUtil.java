package io.dante.intellij.project.reference.replacer;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.externalSystem.model.project.LibraryData;
import com.intellij.openapi.externalSystem.model.project.LibraryDependencyData;
import com.intellij.openapi.externalSystem.model.project.LibraryPathType;
import com.intellij.openapi.util.text.StringUtil;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

public class ReplacementUtil {

	public static String findReplacementModuleName(LibraryDependencyData libraryDependencyData) {
		String externalName = libraryDependencyData.getExternalName();

		if (StringUtil.isNotEmpty(externalName)) {
			int index = externalName.lastIndexOf(':');

			if (index <= 0) {
				return null;
			}

			return findReplacementModuleName(externalName.substring(0, index));
		}

		LibraryData libraryData = libraryDependencyData.getTarget();

		Set<String> paths = libraryData.getPaths(LibraryPathType.BINARY);

		if (paths.size() != 1) {
			_log.info("Library with multiple BINARY paths are not supported: " + libraryData);

			return null;
		}

		String path = paths.stream().findFirst().orElse("");

		for (Map.Entry<String, String> entry : _binaryLibMappings.entrySet()) {
			String key = entry.getKey();

			if (path.endsWith(entry.getKey())) {
				return entry.getValue();
			}
		}

		return null;
	}

	public static String findReplacementModuleName(String artifactKey) {
		return _artifactMappings.get(artifactKey);
	}

	private static final Logger _log = Logger.getInstance(ReplacementUtil.class);

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

	private static final Map<String, String> _binaryLibMappings =
		new HashMap<>() {
			{
				put("portal-kernel.jar", "portal-kernel");
			}
		};

}