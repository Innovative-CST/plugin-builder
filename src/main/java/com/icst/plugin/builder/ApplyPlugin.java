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

import com.android.build.api.artifact.SingleArtifact;
import com.android.build.api.variant.AndroidComponentsExtension;
import com.android.build.api.variant.Variant;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.GradleException;

import com.android.build.gradle.AppExtension;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

public class ApplyPlugin implements Plugin<Project> {

    private static final String PLUGIN_SDK_URL =
            "https://github.com/Innovative-CST/blockidle-plugin-api/releases/download/0.0.0/app-plugin-api-release.aar";

    private static final String PLUGIN_SDK_NAME = "plugin-sdk";
    private static ArrayList<Variant> variants;

    @Override
    public void apply(Project project) {
        variants = new ArrayList<Variant>();

        project.getExtensions().create("blockIdlePlugin", BlockIdleSdkExtension.class);

        BlockIdleSdkExtension ext = project.getExtensions().getByType(BlockIdleSdkExtension.class);

        if (!project.getPlugins().hasPlugin("com.android.application")) {
            project.getPluginManager().apply("com.android.application");
        }

        AppExtension androidExt = project.getExtensions().getByType(AppExtension.class);

        project.getPluginManager().withPlugin(
            "com.android.application",
            p -> configure(project, ext, androidExt)
        );
    }

    private void configure(Project project, BlockIdleSdkExtension ext, AppExtension androidExt) {
        project.afterEvaluate(p -> {
            if (!ext.getPluginName().isPresent()) {
                throw new GradleException("Please provide a plugin name");
            }
        });
        
        project.getTasks().register("buildPlugin", task -> {
            task.setGroup("block-idle");
            task.setDescription("Build all debug plugin variants and generate single metadata");
        });
        
        project.getTasks().register("mergePluginMetadata", MergePluginMetadataTask.class, t -> {
            t.getInputDir().set(
                project.getLayout().getBuildDirectory().dir("plugin-metadata/tmp")
            );
            t.getOutputFile().set(
                project.getLayout().getBuildDirectory().file(
                    "plugin-metadata/all-plugins.json"
                )
            );
        });
        project.getTasks().named("buildPlugin").configure(t ->
            t.dependsOn("mergePluginMetadata")
        );
        
        AndroidComponentsExtension<?, ?, ?> androidComponents = project.getExtensions().getByType(AndroidComponentsExtension.class);
        
        androidComponents.onVariants(androidComponents.selector(), variant -> {
            
            Integer minSdk = variant.getMinSdk().getApiLevel();
            Integer targetSdk = variant.getTargetSdkVersion().getApiLevel();
    
            if (minSdk < 26) {
                throw new GradleException(
                    "minSdk must be >= 26 for variant " + variant.getName()
                );
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
                variant.getProductFlavors().forEach(pf ->
                    flavors.put(pf.getSecond(), pf.getFirst())
                );
    
                task.getProductFlavors().set(flavors);
                task.getMinSdk().set(minSdk);
                task.getTargetSdk().set(targetSdk);
                task.getMetadataFile().set(
                    project.getLayout().getBuildDirectory().file(
                        "plugin-metadata/tmp/" + variant.getName() + "/apk-metadata.json"
                    )
                );
            });
            project.getTasks().named("mergePluginMetadata").configure(t ->
                t.dependsOn(taskName)
            );
        });

        downloadAndAttachSdk(project);
    }

    private String capitalize(String str) {
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    private void downloadAndAttachSdk(Project project) {

        File sdkDir = new File(project.getBuildDir(), "plugin-sdk");
        File sdkAar = new File(sdkDir, PLUGIN_SDK_NAME + ".aar");
        
        if (sdkAar.exists()) {
            project.getDependencies().add("compileOnly", project.files(sdkAar));
            return;
        }

        sdkDir.mkdirs();

        try (InputStream in = new URL(PLUGIN_SDK_URL).openStream();
                OutputStream out = Files.newOutputStream(sdkAar.toPath())) {

            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }

        } catch (Exception e) {
            throw new GradleException("Plugin SDK download failed", e);
        }
        
        project.getDependencies().add("compileOnly", project.files(sdkAar));
    }
}
