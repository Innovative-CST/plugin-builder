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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import com.android.build.gradle.AppExtension;

public class ApplyPlugin implements Plugin<Project> {

	private static ArrayList<Variant> variants;

	@Override
	public void apply(Project project) {
		variants = new ArrayList<Variant>();

		project.getExtensions().create("blockIdlePlugin", BlockIdleSdkExtension.class);

		if (!project.getPlugins().hasPlugin("com.android.application")) {
			project.getPluginManager().apply("com.android.application");
		}

		AppExtension androidExt = project.getExtensions().getByType(AppExtension.class);

		project.getPluginManager().withPlugin(
				"com.android.application",
				p -> configure(project, androidExt));
	}

	private void configure(Project project, AppExtension androidExt) {
		BlockIdleSdkExtension ext = project.getExtensions().getByType(BlockIdleSdkExtension.class);

		Configuration sdkCfg = project.getConfigurations().maybeCreate("blockIdlePluginSdk");
		sdkCfg.setCanBeResolved(true);
		sdkCfg.setCanBeConsumed(false);

		project.afterEvaluate(t -> {
			if (!ext.getPluginName().isPresent()) {
				throw new GradleException("Please provide a plugin name");
			}
			if (!ext.getSdkVersion().isPresent()) {
				throw new GradleException("Please provide sdk version for building plugin for BlockIDLE");
			}

			String sdkVersion = ext.getSdkVersion().get();

			project.getDependencies().add(
					"blockIdlePluginSdk",
					"io.github.devvigilante:blockidle-plugin-sdk:" + sdkVersion);

			project.getDependencies().add(
					"compileOnly",
					"io.github.devvigilante:blockidle-plugin-sdk:" + sdkVersion);
		});

		project.getTasks().register("buildPlugin", task -> {
			task.setGroup("block-idle");
			task.setDescription("Build all debug plugin variants and generates ready to publish plugin");
		});

		project.getTasks().register("mergePluginMetadata", MergePluginMetadataTask.class, t -> {
			t.getInputDir().set(
					project.getLayout().getBuildDirectory().dir("outputs/plugin"));
			t.getOutputFile().set(
					project.getLayout().getBuildDirectory().file("outputs/plugin/plugin-metadata.json"));
		});
		project.getTasks().named("buildPlugin").configure(t -> t.dependsOn("mergePluginMetadata"));

		AndroidComponentsExtension<?, ?, ?> androidComponents = project.getExtensions()
				.getByType(AndroidComponentsExtension.class);

		androidComponents.onVariants(androidComponents.selector(), variant -> {

			Integer minSdk = variant.getMinSdk().getApiLevel();
			Integer targetSdk = variant.getTargetSdkVersion().getApiLevel();

			if (minSdk < 26) {
				throw new GradleException(
						"minSdk must be >= 26 for variant " + variant.getName());
			}

			// if (targetSdk != 28) {
			// throw new GradleException(
			// "targetSdk must be 28 for variant " + variant.getName()
			// );
			// }

			String taskName = "buildPlugin" + capitalize(variant.getName());

			project.getTasks().register(taskName, BuildPluginTask.class, task -> {

				// APK artifacts
				task.getApkFolder().set(variant.getArtifacts().get(SingleArtifact.APK.INSTANCE));
				task.getBuiltArtifactsLoader().set(variant.getArtifacts().getBuiltArtifactsLoader());

				// Variant metadata
				task.getVariantName().set(variant.getName());
				task.getBuildType().set(variant.getBuildType());

				Map<String, String> flavors = new HashMap<>();
				variant.getProductFlavors().forEach(pf -> flavors.put(pf.getSecond(), pf.getFirst()));

				task.getProductFlavors().set(flavors);
				task.getMinSdk().set(ext.getMinSdkVersion());
				task.getAppMinSdk().set(minSdk);
				task.getAppTargetSdk().set(targetSdk);
				task.getMetadataFile().set(
						project.getLayout().getBuildDirectory().file(
								"outputs/plugin/" + variant.getName() + "/plugin-metadata.json"));
				task.getPluginOutputDir().set(
						project.getLayout().getBuildDirectory().file(
								"outputs/plugin/" + variant.getName()));
			});
			project.getTasks().named("mergePluginMetadata").configure(t -> t.dependsOn(taskName));
		});
	}

	private String capitalize(String str) {
		return str.substring(0, 1).toUpperCase() + str.substring(1);
	}
}
