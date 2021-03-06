/******************************************************************************* 
 * Copyright (c) 2017 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.openshift.cdk.server.core.internal.listeners;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.eclipse.core.runtime.Platform;
import org.eclipse.wst.server.core.IServer;
import org.jboss.ide.eclipse.as.core.JBossServerCorePlugin;
import org.jboss.tools.openshift.cdk.server.core.internal.CDKCoreActivator;
import org.jboss.tools.openshift.cdk.server.core.internal.MinishiftBinaryUtility;

public class MinishiftServiceManagerEnvironmentLoader extends ServiceManagerEnvironmentLoader {
	
	public MinishiftServiceManagerEnvironmentLoader() {
		super(TYPE_MINISHIFT);
	}

	@Override
	public ServiceManagerEnvironment loadServiceManagerEnvironment(IServer server) {
		// Load the docker env
		Map<String, String> adbEnv = loadDockerEnv(server);
		
		// Load the minishift console --machine-readable
		Properties props = loadOpenshiftConsoleDetails(server);
		String registry = getOpenshiftRegistry(server);
		if( registry != null )
			props.put(ServiceManagerEnvironment.IMAGE_REGISTRY_KEY, registry);

		// merge the two
		Map<String, String> merged = merge(adbEnv, props);
		
		String dotMinishift = System.getProperty("user.home") + File.separator + ".minishift";
		Properties dotCDK = CDKServerUtility.getDotCDK(dotMinishift, "cdk");
		merged = merge(merged, dotCDK);
		
		File ocLocation = findOCLocation();
		if( ocLocation != null ) {
			merged.put(OC_LOCATION_KEY, ocLocation.getAbsolutePath());
		}

		try {
			return new ServiceManagerEnvironment(merged);
		} catch (URISyntaxException urise) {
			CDKCoreActivator.pluginLog()
					.logError("Environment variable DOCKER_HOST is not a valid uri:  " + merged.get("DOCKER_HOST"), urise);
			return null;
		}
		
		
	}
	
	private File findOCLocation() {
		String dotMinishift = System.getProperty("user.home") + File.separator + ".minishift";
		File root = new File(dotMinishift);
		if( root.exists()) {
			File cache = new File(root, "cache");
			File oc = new File(cache, "oc");
			if( oc.exists()) {
				String[] names = oc.list();
				if( names != null && names.length > 0) {
					Arrays.sort(names);
					String latest = names[names.length-1];
					File latestF = new File(oc, latest);
					String platformDep = (Platform.getOS().equals(Platform.OS_WIN32) ? "oc.exe" : "oc");
					File ocBin = new File(latestF, platformDep);
					if( ocBin.exists())
						return ocBin;
				}
			}
		}
		return null;
	}
	

	protected String getOpenshiftRegistry(IServer server) {
		Map<String, String> env = CDKLaunchEnvironmentUtil.createEnvironment(server);
		String cmdLoc = MinishiftBinaryUtility.getMinishiftLocation(server);
		String[] args = new String[] { "openshift", "registry" };
		File wd = JBossServerCorePlugin.getServerStateLocation(server).toFile();
		try {
			String[] lines = callAndGetLines(env, args, cmdLoc, wd);
			if( lines != null && lines.length > 0) {
				return lines[0];
			}
		} catch(IOException ioe) {
			CDKCoreActivator.pluginLog().logError(
					"Unable to successfully complete a call to minishift docker-env ",ioe);
		}
		return null;
	}

	protected Map<String, String> loadDockerEnv(IServer server) {
		Map<String, String> env = CDKLaunchEnvironmentUtil.createEnvironment(server);
		String cmdLoc = MinishiftBinaryUtility.getMinishiftLocation(server);
		String[] args = new String[] { "docker-env" };
		File wd = JBossServerCorePlugin.getServerStateLocation(server).toFile();
		try {
			HashMap<String, String> adbEnv = callAndParseEnvVar(env, args, cmdLoc, wd);
			return adbEnv;
		} catch (IOException ioe) {
			CDKCoreActivator.pluginLog().logError("Unable to successfully complete a call to minishift docker-env ",
					ioe);
		}
		return new HashMap<String, String>();
	}

	protected Properties loadOpenshiftConsoleDetails(IServer server) {
		Map<String, String> env = CDKLaunchEnvironmentUtil.createEnvironment(server);
		String[] args = new String[] { "console", "--machine-readable" };
		File wd = JBossServerCorePlugin.getServerStateLocation(server).toFile();
		String cmdLoc = MinishiftBinaryUtility.getMinishiftLocation(server);
		try {
			Properties ret = callAndParseProperties(env, args, cmdLoc, wd);
			return ret;
		} catch (IOException ce) {
			CDKCoreActivator.pluginLog()
					.logError("Unable to successfully complete a call to minishift console --machine-readable. ", ce);
		}
		return new Properties();
	}

}
