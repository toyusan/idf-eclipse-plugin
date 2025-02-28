/*******************************************************************************
 * Copyright 2018-2019 Espressif Systems (Shanghai) PTE LTD. All rights reserved.
 * Use is subject to license terms.
 *******************************************************************************/
package com.espressif.idf.sdk.config.core.server;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.ui.console.MessageConsoleStream;
import org.json.simple.parser.ParseException;

import com.espressif.idf.core.IDFConstants;
import com.espressif.idf.core.IDFEnvironmentVariables;
import com.espressif.idf.core.logging.Logger;
import com.espressif.idf.core.util.IDFUtil;
import com.espressif.idf.core.util.StringUtil;
import com.espressif.idf.sdk.config.core.SDKConfigCorePlugin;

/**
 * @author Kondal Kolipaka <kondal.kolipaka@espressif.com>
 *
 */
public class JsonConfigServer implements IMessagesHandlerNotifier
{

	protected MessageConsoleStream console;
	private List<IMessageHandlerListener> listeners;
	private IProject project;
	private JsonConfigServerRunnable runnable;
	private JsonConfigOutput configOutput;

	public JsonConfigServer(IProject project)
	{
		this.project = project;
		listeners = new ArrayList<IMessageHandlerListener>();
		configOutput = new JsonConfigOutput();
	}

	@Override
	public void addListener(IMessageHandlerListener listener)
	{
		listeners.add(listener);
	}

	public void destroy()
	{
		if (runnable != null)
		{
			runnable.destory();
		}
	}

	public void execute(String command, CommandType type)
	{
		if (runnable != null)
		{
			runnable.executeCommand(command, type);
		}
	}

	@Override
	public void notifyHandler(String message, CommandType type)
	{
		for (IMessageHandlerListener listener : listeners)
		{
			listener.notifyRequestServed(message, type);
		}
	}

	@Override
	public void removeListener(IMessageHandlerListener listener)
	{
		listeners.remove(listener);
	}

	public void start() throws IOException
	{
		IPath workingDir = project.getLocation();
		Map<String, String> idfEnvMap = new IDFEnvironmentVariables().getSystemEnvMap();

		// Disable buffering of output
		idfEnvMap.put("PYTHONUNBUFFERED", "1"); //$NON-NLS-1$ //$NON-NLS-2$

		File idfPythonScriptFile = IDFUtil.getIDFPythonScriptFile();
		if (!idfPythonScriptFile.exists())
		{
			throw new FileNotFoundException("File Not found:" + idfPythonScriptFile); //$NON-NLS-1$
		}
		
		String pythonPath = IDFUtil.getIDFPythonEnvPath();

		List<String> arguments = Collections.emptyList();
		try
		{
			arguments = new ArrayList<String>(Arrays.asList(pythonPath, idfPythonScriptFile.getAbsolutePath(), "-B", //$NON-NLS-1$
					IDFUtil.getBuildDir(project), IDFConstants.CONF_SERVER_CMD));
		}
		catch (CoreException e)
		{
			Logger.log(e);
		}
		Logger.log(arguments.toString());

		ProcessBuilder processBuilder = new ProcessBuilder(arguments);
		if (workingDir != null)
		{
			processBuilder.directory(workingDir.toFile());
		}
		Map<String, String> environment = processBuilder.environment();
		environment.putAll(idfEnvMap);

		Logger.log(environment.toString());

		String idfPath = environment.get("PATH"); //$NON-NLS-1$
		String processPath = environment.get("Path"); //$NON-NLS-1$
		if (!StringUtil.isEmpty(idfPath) && !StringUtil.isEmpty(processPath)) // if both exist!
		{
			idfPath = idfPath.concat(";").concat(processPath); //$NON-NLS-1$
			environment.put("PATH", idfPath); //$NON-NLS-1$
			environment.remove("Path");//$NON-NLS-1$
		}

		Logger.log(environment.toString());

		// redirect error stream to input stream
		processBuilder.redirectErrorStream(true);

		Process process = processBuilder.start();
		runnable = new JsonConfigServerRunnable(process, this);
		Thread t = new Thread(runnable);
		t.start();
	}

	public IJsonConfigOutput getOutput(String response, boolean isUpdate)
	{
		try
		{
			configOutput.parse(response, isUpdate);
		}
		catch (ParseException e)
		{
			Logger.log(SDKConfigCorePlugin.getPlugin(), e);
		}

		return configOutput;
	}

	public IJsonConfigOutput getOutput()
	{
		return configOutput;
	}

	public void addConsole(MessageConsoleStream console)
	{
		this.console = console;
	}

}
