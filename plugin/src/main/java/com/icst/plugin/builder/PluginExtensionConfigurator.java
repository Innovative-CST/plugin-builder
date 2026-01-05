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

import org.gradle.api.GradleException;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;

public class PluginExtensionConfigurator {

	public static void configure(Project project) {
		project.getExtensions().create("blockIdlePlugin", BlockIdleSdkExtension.class);

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
	}

}
