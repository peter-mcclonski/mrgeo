/*
 * Copyright 2009-2017. DigitalGlobe, Inc.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */

package org.mrgeo.cmd.mapalgebra.python;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import py4j.GatewayServer;
import py4j.GatewayServerListener;
import py4j.Py4JServerConnection;

import java.io.DataOutputStream;
import java.io.IOException;
import java.net.Socket;
import java.net.SocketException;

class PythonGatewayListener implements GatewayServerListener
{
private static final Logger log = LoggerFactory.getLogger(PythonGatewayListener.class);

final private GatewayServer server;
final private Socket clientSocket;
final private int javaPythonPort;
final private int pythonJavaPort;

PythonGatewayListener(GatewayServer server, Socket clientSocket, int javaPythonPort, int pythonJavaPort)
{
  this.server = server;
  this.clientSocket = clientSocket;
  this.javaPythonPort = javaPythonPort;
  this.pythonJavaPort = pythonJavaPort;

}

@Override
public void connectionError(Exception e)
{
  log.warn("Connection error");
}

@Override
public void connectionStarted(Py4JServerConnection py4JServerConnection)
{
  Socket socket = py4JServerConnection.getSocket();

  log.warn("Started connection " +
      socket.getInetAddress().getHostName() + "(" + socket.getInetAddress().getHostAddress() +
      ")" + ":" + socket.getLocalPort());
}

@Override
public void connectionStopped(Py4JServerConnection py4JServerConnection)
{
  Socket socket = py4JServerConnection.getSocket();

  log.warn("Stopped connection " +
      socket.getInetAddress().getHostName() + "(" + socket.getInetAddress().getHostAddress() +
      ")" + ":" + socket.getLocalPort());

}

@Override
@SuppressWarnings("squid:S1148") // Exception handler
public void serverError(Exception e)
{
  if (e instanceof SocketException && e.getLocalizedMessage().equals("Socket closed"))
  {
    log.warn("Socket closed, probably at the other end");

  }
  else
  {
    log.error("Server error {}", e);
    e.printStackTrace();
    System.out.flush();
  }
}

@Override
public void serverPostShutdown()
{
  log.warn("Server post-shutdown");
}

@Override
public void serverPreShutdown()
{
  log.warn("Server pre-shutdown");
}

@Override
public void serverStarted()
{
  int listeningPort = server.getListeningPort();
  if (listeningPort == -1 || listeningPort != javaPythonPort)
  {
    log.error("GatewayServer failed to bind");
    //throw new IOException("GatewayServer failed to bind");
  }

  log.info("Starting PythonGatewayServer. Communicating (java->python) on port " + listeningPort);

  try
  {
    sendGatewayPort(clientSocket, javaPythonPort, pythonJavaPort);
  }
  catch (IOException e)
  {
    log.error("Gateway error {}", e);
  }

}

@Override
public void serverStopped()
{
  log.warn("Server stopped");
  System.out.flush();
}

private void sendGatewayPort(Socket clientSocket, int javaPythonPort, int pythonJavaPort) throws IOException
{
  // Communicate the bound port back to the caller via the caller-specified callback port
  log.info("Sending java->python port (" + javaPythonPort + ") and python->java port (" + pythonJavaPort +
      ") to pymrgeo running at " +
      clientSocket.getInetAddress().getHostName() + "(" + clientSocket.getInetAddress().getHostAddress() +
      ")" + ":" + clientSocket.getPort());
  try
  {
    DataOutputStream dos = new DataOutputStream(clientSocket.getOutputStream());
    dos.writeInt(javaPythonPort);
    dos.writeInt(pythonJavaPort);
    dos.close();
  }
  catch (IOException e)
  {
    throw new IOException("Can not write to socket callback socket", e);
  }
}

}
