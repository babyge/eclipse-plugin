/*
 * Copyright (c) 2019 Intel Corporation
 *
 * SPDX-License-Identifier: EPL-2.0
 */

package org.zephyrproject.ide.eclipse.core.launch;

import org.eclipse.cdt.debug.gdbjtag.core.GDBJtagDSFLaunchConfigurationDelegate;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunch;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.launchbar.core.target.ILaunchTarget;
import org.eclipse.launchbar.core.target.launch.ITargetedLaunch;
import org.zephyrproject.ide.eclipse.core.build.ZephyrApplicationBuildConfiguration;
import org.zephyrproject.ide.eclipse.core.build.ZephyrApplicationToolChain;
import org.zephyrproject.ide.eclipse.core.build.CMakeConstants;
import org.zephyrproject.ide.eclipse.core.internal.ZephyrHelpers;

public class ZephyrApplicationHardwareDebugLaunchConfigDelegate
		extends GDBJtagDSFLaunchConfigurationDelegate {

	private static final String CMD_DEBUGSERVER = "debugserver"; //$NON-NLS-1$

	private static final String CMD_FLASH = "flash"; //$NON-NLS-1$

	@Override
	public void launch(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		if (ILaunchManager.DEBUG_MODE.equals(mode)) {
			/* Flash hardware target */
			flashTarget(configuration, mode, launch, monitor);

			/* Start debugserver */
			launchDebugServer(configuration, mode, launch, monitor);

			super.launch(configuration, mode, launch, monitor);
		} else {
			throw new CoreException(ZephyrHelpers.errorStatus(
					"Project is not correctly configured.", //$NON-NLS-1$
					new RuntimeException(
							String.format("Unknown mode: %s", mode)))); // $NON-NLS-1$
		}
	}

	private void flashTarget(ILaunchConfiguration configuration, String mode,
			ILaunch launch, IProgressMonitor monitor) throws CoreException {
		IProject project = configuration.getMappedResources()[0].getProject();
		ILaunchTarget target = ((ITargetedLaunch) launch).getLaunchTarget();

		ZephyrApplicationBuildConfiguration appBuildCfg = ZephyrHelpers.Launch
				.getBuildConfiguration(project, mode, target, monitor);
		ZephyrApplicationToolChain toolChain =
				(ZephyrApplicationToolChain) appBuildCfg.getToolChain();

		String cmakeGenerator = ZephyrHelpers.getCMakeGenerator(project);
		String makeProgram = toolChain.getMakeProgram();
		if (makeProgram == null) {
			throw new CoreException(ZephyrHelpers.errorStatus(
					"Project is not correctly configured.", //$NON-NLS-1$
					new RuntimeException("Cannot get CMake generator used."))); //$NON-NLS-1$
		}

		/* Figure out whether to do run, flash or custom command */
		String commandSelection = configuration.getAttribute(
				ZephyrLaunchConstants.ATTR_FLASH_CMD_SEL,
				ZephyrLaunchConstants.FLASH_CMD_SEL_NONE);
		String cmdTarget = null;
		Process process = null;
		if (commandSelection
				.equals(ZephyrLaunchConstants.FLASH_CMD_SEL_CUSTOM_CMD)) {
			/* Need to run custom command */
			process = ZephyrHelpers.Launch.doCustomCommand(project, appBuildCfg,
					launch, configuration,
					ZephyrLaunchConstants.ATTR_FLASH_CUSTOM_COMMAND);
		} else {
			if (commandSelection
					.equals(ZephyrLaunchConstants.FLASH_CMD_SEL_DFLT)) {
				cmdTarget = CMD_FLASH;
			} else if (commandSelection
					.equals(ZephyrLaunchConstants.FLASH_CMD_SEL_NONE)) {
				/* Instructed not to flash hardware target */
				return;
			}

			if (cmdTarget == null) {
				throw new CoreException(ZephyrHelpers.errorStatus(
						"Project is not correctly configured.", //$NON-NLS-1$
						new RuntimeException("Unknown Command to Run"))); //$NON-NLS-1$
			}

			if (cmakeGenerator
					.equals(CMakeConstants.CMAKE_GENERATOR_MAKEFILE)) {
				process = ZephyrHelpers.Launch.doMakefile(project, appBuildCfg,
						launch, makeProgram, cmdTarget);
			} else if (cmakeGenerator
					.equals(CMakeConstants.CMAKE_GENERATOR_NINJA)) {
				process = ZephyrHelpers.Launch.doNinja(project, appBuildCfg,
						launch, makeProgram, cmdTarget);
			} else {
				throw new CoreException(ZephyrHelpers.errorStatus(
						"Project is not correctly configured.", //$NON-NLS-1$
						new RuntimeException("Unknown CMake Generator."))); //$NON-NLS-1$
			}
		}

		if (process != null) {
			try {
				int ret = process.waitFor();

				if (ret != 0) {
					throw new CoreException(ZephyrHelpers.errorStatus(
							"Flashing process is unsuccessful.", //$NON-NLS-1$
							new RuntimeException(
									String.format("Return code %s", ret)))); // $NON-NLS-1$
				}

			} catch (InterruptedException e) {
				throw new CoreException(ZephyrHelpers.errorStatus(
						"Flashing process is unintentionally interrupted.", //$NON-NLS-1$
						new RuntimeException())); // $NON-NLS-1$
			}
		}
	}

	private void launchDebugServer(ILaunchConfiguration configuration,
			String mode, ILaunch launch, IProgressMonitor monitor)
			throws CoreException {
		IProject project = configuration.getMappedResources()[0].getProject();
		ILaunchTarget target = ((ITargetedLaunch) launch).getLaunchTarget();

		ZephyrApplicationBuildConfiguration appBuildCfg = ZephyrHelpers.Launch
				.getBuildConfiguration(project, mode, target, monitor);
		ZephyrApplicationToolChain toolChain =
				(ZephyrApplicationToolChain) appBuildCfg.getToolChain();

		String cmakeGenerator = ZephyrHelpers.getCMakeGenerator(project);
		String makeProgram = toolChain.getMakeProgram();
		if (makeProgram == null) {
			throw new CoreException(ZephyrHelpers.errorStatus(
					"Project is not correctly configured.", //$NON-NLS-1$
					new RuntimeException("Cannot get CMake generator used."))); //$NON-NLS-1$
		}

		/* Figure out whether to do run, flash or custom command */
		String commandSelection = configuration.getAttribute(
				ZephyrLaunchConstants.ATTR_DBGSERVER_CMD_SEL,
				ZephyrLaunchConstants.DBGSERVER_CMD_SEL_NONE);
		String cmdTarget = null;
		if (commandSelection.equals(
				ZephyrLaunchConstants.DBGSERVER_CMD_SEL_CUSTOM_COMMAND)) {
			/* Need to run custom command */
			ZephyrHelpers.Launch.doCustomCommand(project, appBuildCfg, launch,
					configuration,
					ZephyrLaunchConstants.ATTR_DBGSERVER_CUSTOM_COMMAND);
			return;
		} else if (commandSelection
				.equals(ZephyrLaunchConstants.DBGSERVER_CMD_SEL_DEFAULT)) {
			cmdTarget = CMD_DEBUGSERVER;
		} else if (commandSelection
				.equals(ZephyrLaunchConstants.DBGSERVER_CMD_SEL_NONE)) {
			/* Instructed not to launch debugserver */
			return;
		}

		if (cmdTarget == null) {
			throw new CoreException(ZephyrHelpers.errorStatus(
					"Project is not correctly configured.", //$NON-NLS-1$
					new RuntimeException("Unknown Command to Run"))); //$NON-NLS-1$
		}

		if (cmakeGenerator.equals(CMakeConstants.CMAKE_GENERATOR_MAKEFILE)) {
			ZephyrHelpers.Launch.doMakefile(project, appBuildCfg, launch,
					makeProgram, cmdTarget);
		} else if (cmakeGenerator
				.equals(CMakeConstants.CMAKE_GENERATOR_NINJA)) {
			ZephyrHelpers.Launch.doNinja(project, appBuildCfg, launch,
					makeProgram, cmdTarget);
		} else {
			throw new CoreException(ZephyrHelpers.errorStatus(
					"Project is not correctly configured.", //$NON-NLS-1$
					new RuntimeException("Unknown CMake Generator."))); //$NON-NLS-1$
		}
	}

	@Override
	protected void setDefaultProcessFactory(ILaunchConfiguration config)
			throws CoreException {
		if (!config.hasAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID)) {
			ILaunchConfigurationWorkingCopy wc = config.getWorkingCopy();
			wc.setAttribute(DebugPlugin.ATTR_PROCESS_FACTORY_ID,
					ZephyrProcessFactory.ID);
			wc.doSave();
		}
	}

}
