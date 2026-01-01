/*
 *  This file is part of Block IDLE.
 *
 *  Block IDLE is free software: you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation, either version 3 of the License, or
 *  (at your option) any later version.
 *
 *  Block IDLE is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *   along with Block IDLE.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.icst.plugin.builder;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.gradle.api.DefaultTask;
import org.gradle.api.GradleException;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.OutputFile;
import org.gradle.api.tasks.TaskAction;

import com.android.build.api.variant.BuiltArtifacts;
import com.android.build.api.variant.BuiltArtifactsLoader;
import com.google.gson.GsonBuilder;

public abstract class BuildPluginTask extends DefaultTask {

	@InputFiles
	public abstract DirectoryProperty getApkFolder();

	@Internal
	public abstract Property<BuiltArtifactsLoader> getBuiltArtifactsLoader();

	// ===== Variant metadata =====

	@Input
	public abstract Property<String> getVariantName();

	@Input
	public abstract Property<String> getBuildType();

	@Input
	public abstract MapProperty<String, String> getProductFlavors();

	@Input
	public abstract Property<Integer> getMinSdk();

	@Input
	public abstract Property<Integer> getTargetSdk();

	@OutputFile
	public abstract RegularFileProperty getMetadataFile();

	@TaskAction
	public void execute() {
		BuiltArtifacts builtArtifacts = getBuiltArtifactsLoader().get().load(getApkFolder().get());

		if (builtArtifacts == null) {
			throw new RuntimeException("No APKs found!");
		}

		getLogger().lifecycle("Variant      : " + getVariantName().get());
		getLogger().lifecycle("BuildType    : " + getBuildType().get());
		getLogger().lifecycle("Flavors      : " + getProductFlavors().get());
		getLogger().lifecycle("minSdk       : " + getMinSdk().get());
		getLogger().lifecycle("targetSdk    : " + getTargetSdk().get());

		builtArtifacts.getElements().forEach(artifact -> {
			getLogger().lifecycle("---- APK OUTPUT ----");
			getLogger().lifecycle("APK path      : " + artifact.getOutputFile());
			getLogger().lifecycle("VersionName   : " + artifact.getVersionName());
			getLogger().lifecycle("Filters       : " + artifact.getFilters());
		});

		Map<String, Object> root = new LinkedHashMap<>();
		root.put("variant", getVariantName().get());
		root.put("buildType", getBuildType().get());
		root.put("flavors", getProductFlavors().get());
		root.put("minSdk", getMinSdk().get());
		root.put("targetSdk", getTargetSdk().get());

		List<Map<String, Object>> outputs = new ArrayList<>();

		builtArtifacts.getElements().forEach(artifact -> {
			Map<String, Object> apk = new LinkedHashMap<>();
			apk.put("apkPath", artifact.getOutputFile());
			apk.put("versionName", artifact.getVersionName());
			apk.put("filters", artifact.getFilters());
			outputs.add(apk);
		});

		root.put("outputs", outputs);

		try {
			writeJson(getMetadataFile().get().getAsFile(), root);
		} catch (IOException e) {
			throw new GradleException(e.getMessage());
		}
	}

	private void writeJson(File file, Map<String, Object> data) throws IOException {
		file.getParentFile().mkdirs();

		try (Writer writer = new OutputStreamWriter(
				new FileOutputStream(file), StandardCharsets.UTF_8)) {

			writer.write(new GsonBuilder()
					.setPrettyPrinting()
					.create()
					.toJson(data));
		}
	}
}
