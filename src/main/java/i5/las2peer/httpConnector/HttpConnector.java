package i5.las2peer.httpConnector;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.text.DateFormat;
import java.util.Date;

import i5.httpServer.HttpServer;
import i5.httpServer.HttpsServer;
import i5.httpServer.RequestHandler;
import i5.las2peer.api.Connector;
import i5.las2peer.api.ConnectorException;
import i5.las2peer.logging.NodeObserver.Event;
import i5.las2peer.p2p.AgentNotKnownException;
import i5.las2peer.p2p.Node;
import i5.las2peer.security.Agent;

/**
 * Starter class for registering the HTTP connector at the LAS2peer server.
 *
 */
public class HttpConnector extends Connector {

	/* configuration parameters */
	private int DEFAULT_HTTP_CONNECTOR_PORT = 8080;
	protected int httpConnectorPort = DEFAULT_HTTP_CONNECTOR_PORT;

	private int DEFAULT_HTTPS_CONNECTOR_PORT = 8090;
	protected int httpsConnectorPort = DEFAULT_HTTPS_CONNECTOR_PORT;

	private boolean DEFAULT_START_HTTP_CONNECTOR = true;
	protected boolean startHttpConnector = DEFAULT_START_HTTP_CONNECTOR;

	private boolean DEFAULT_START_HTTPS_CONNECTOR = false;
	protected boolean startHttpsConnector = DEFAULT_START_HTTPS_CONNECTOR;

	private String DEFAULT_SSL_KEYSTORE = "keys/ssl";
	protected String sslKeystore = DEFAULT_SSL_KEYSTORE;

	private String DEFAULT_SSL_KEY_PASSWD = "123456";
	protected String sslKeyPasswd = DEFAULT_SSL_KEY_PASSWD;

	private boolean DEFAULT_ENABLE_FILE_ACCESS = false;
	protected boolean enableFileAccess = DEFAULT_ENABLE_FILE_ACCESS;

	private String DEFAULT_FILE_DIRECTORY = "./htdocs";
	protected String fileDirectory = DEFAULT_FILE_DIRECTORY;

	private long DEFAULT_MAX_SESSION_TIMEOUT = 30 * 60 * 60 * 1000; // 30 minutes
	protected long maxSessionTimeoutMS = DEFAULT_MAX_SESSION_TIMEOUT;

	private long DEFAULT_MIN_SESSION_TIMEOUT = 30 * 1000; // 30 seconds
	protected long minSessionTimeoutMS = DEFAULT_MIN_SESSION_TIMEOUT;

	private long DEFAULT_DEFAULT_SESSION_TIMEOUT = 10 * 60 * 60 * 1000; // 10 minutes
	protected long defaultSessionTimeout = DEFAULT_DEFAULT_SESSION_TIMEOUT;

	private long DEFAULT_DEFAULT_PERSISTENT_OUTDATE_S = 60 * 60 * 24 * 1000; // 1 day
	protected long defaultPersistentTimeoutMS = DEFAULT_DEFAULT_PERSISTENT_OUTDATE_S;

	private long DEFAULT_MIN_PERSISTENT_OUTDATE_S = 60 * 60 * 1000; // 1 hour
	protected long minPersistentTimeoutMS = DEFAULT_MIN_PERSISTENT_OUTDATE_S;

	private long DEFAULT_MAX_PERSISTENT_OUTDATE_S = 60 * 60 * 1000; // 1 hour
	protected long maxPersistentTimeoutMS = DEFAULT_MAX_PERSISTENT_OUTDATE_S;

	private boolean DEFAULT_PRINT_SEC_EXCEPTIONS = false;
	protected boolean printSecExceptions = DEFAULT_PRINT_SEC_EXCEPTIONS;

	private int DEFAULT_SOCKET_TIMEOUT = 60 * 1000; // 1 minute
	protected int socketTimeout = DEFAULT_SOCKET_TIMEOUT;

	private String DEFAULT_CROSS_ORIGIN_RESOURCE_DOMAIN = "";
	protected String crossOriginResourceDomain = DEFAULT_CROSS_ORIGIN_RESOURCE_DOMAIN;

	private int DEFAULT_CROSS_ORIGIN_RESOURCE_MAX_AGE = 60;
	protected int crossOriginResourceMaxAge = DEFAULT_CROSS_ORIGIN_RESOURCE_MAX_AGE;

	private boolean DEFAULT_ENABLE_CROSS_ORIGIN_RESOURCE_SHARING = false;
	protected boolean enableCrossOriginResourceSharing = DEFAULT_ENABLE_CROSS_ORIGIN_RESOURCE_SHARING;

	private boolean DEFAULT_PREFER_LOCAL_SERVICES = true;
	protected boolean preferLocalServices = DEFAULT_PREFER_LOCAL_SERVICES;

	private String DEFAULT_LOGFILE = "log/httpConnector.log";
	private String logfile = DEFAULT_LOGFILE;

	private HttpServer http;
	private HttpsServer https;

	private Node myNode = null;

	private PrintStream logStream = null;
	private DateFormat dateFormat = DateFormat.getDateTimeInstance();

	/**
	 * create a new HTTP connector instance.
	 * 
	 * @throws FileNotFoundException
	 */
	public HttpConnector() throws FileNotFoundException {
		setFieldValues();

		if (enableFileAccess)
			System.out.println("HttpConnector: Warning - File Access is enabled!");
	}

	/**
	 * set the log file for this connector
	 * 
	 * @param filename
	 * @throws FileNotFoundException
	 */
	public void setLogFile(String filename) throws FileNotFoundException {
		// auto create log directory if it doesn't exist
		new File(filename.substring(0, filename.lastIndexOf(File.separator))).mkdirs();
		setLogStream(new PrintStream(new FileOutputStream(filename, true)));
	}

	/**
	 * set the port for the HTTP connector to listen to
	 * 
	 * @param port
	 */
	public void setPort(int port) {
		if (port < 80)
			throw new IllegalArgumentException("illegal port number: " + port);
		if (myNode != null)
			throw new IllegalStateException("change of port only before startup!");

		httpConnectorPort = port;
	}

	/**
	 * set the port for the HTTP connector to listen to for the secure line
	 * 
	 * @param port
	 */
	public void setHttpsPort(int port) {
		if (port < 80)
			throw new IllegalArgumentException("illegal port number: " + port);
		if (myNode != null)
			throw new IllegalStateException("change of port only before startup!");

		httpsConnectorPort = port;
	}

	/**
	 * set the socket timeout for the underlying http server (only at configuration not during runtim)
	 * 
	 * @param timeoutInMs
	 */
	public void setSocketTimeout(int timeoutInMs) {
		socketTimeout = timeoutInMs;
	}

	/**
	 * set a stream to log all messages to
	 * 
	 * @param stream
	 */
	public void setLogStream(PrintStream stream) {
		logStream = stream;
	}

	@Override
	public void start(Node node) throws ConnectorException {
		if (!startHttpConnector && !startHttpsConnector)
			throw new ConnectorException("either the http connector of the https connector have to be started!");

		if (logStream == null)
			try {
				setLogFile(logfile);
			} catch (FileNotFoundException e) {
				throw new ConnectorException("cannot initialize standard log file at " + logfile, e);
			}

		myNode = node;

		System.setProperty("http-connector.printSecExceptions", "" + printSecExceptions);

		// enable file access ?
		if (enableFileAccess && fileDirectory != null) {
			System.setProperty("las.http.fileAccess", "true");
			System.setProperty("las.http.fileAccessDir", fileDirectory);
		}

		if (startHttpConnector) {
			// start the HTTP listener
			if (enableCrossOriginResourceSharing) {
				http = new HttpServer(HttpConnectorRequestHandler.class.getName(), httpConnectorPort,
						crossOriginResourceDomain, crossOriginResourceMaxAge);
			} else {
				http = new HttpServer(HttpConnectorRequestHandler.class.getName(), httpConnectorPort);
			}

			http.setSocketTimeout(socketTimeout);

			http.start();

			// wait for the server to start
			RequestHandler handler;
			do {
				try {
					Thread.sleep(500);
				} catch (InterruptedException e) {
					throw new ConnectorException("Startup has been interrupted!", e);
				}
				handler = http.getHandler();
			} while (handler == null);

			((HttpConnectorRequestHandler) handler).setConnector(this);
			logMessage("Http-Connector running on port " + httpConnectorPort);
		}

		if (startHttpsConnector) {
			if (enableCrossOriginResourceSharing) {
				https = new HttpsServer(sslKeystore, sslKeyPasswd, HttpConnectorRequestHandler.class.getName(),
						httpsConnectorPort, crossOriginResourceDomain, crossOriginResourceMaxAge);
			} else {
				https = new HttpsServer(sslKeystore, sslKeyPasswd, HttpConnectorRequestHandler.class.getName(),
						httpsConnectorPort);
			}

			https.setSocketTimeout(socketTimeout);
			https.start();
			logMessage("Https-Connector running on port " + httpConnectorPort);
		}
	}

	@Override
	public void stop() throws ConnectorException {

		// stop the listener
		if (http != null)
			http.stopServer();

		if (https != null)
			https.stopServer();

		try {
			if (http != null) {
				http.join();
				logMessage("Http-Connector has been stopped");

			}
			if (https != null) {
				https.join();
				logMessage("Https-Connector has been stopped");
			}
		} catch (InterruptedException e) {
			logError("Joining has been interrupted!");
		}
		this.myNode = null;

	}

	/**
	 * send an interrupt to all sub servers (mainly for hard test shutdown)
	 */
	public void interrupt() {
		if (http != null)
			http.interrupt();
		if (https != null)
			https.interrupt();
		System.out.println("interrupted!");
	}

	/**
	 * get the node, this connector is running at / for
	 * 
	 * @return the Las2Peer node of this connector
	 */
	public Node getL2pNode() {
		return myNode;
	}

	/**
	 * get a timeout value for a suggested timeout (e.g. given by the remote user) based on the set minimal an maximal
	 * timeout values
	 * 
	 * e.g. a getSessionTimeout(0) always gives the minimal session timeout value
	 * 
	 * @param suggested
	 * @return the session time out
	 */
	long getSessionTimeout(long suggested) {
		if (suggested < minSessionTimeoutMS)
			suggested = minSessionTimeoutMS;
		if (suggested > maxSessionTimeoutMS)
			suggested = maxSessionTimeoutMS;

		return suggested;
	}

	/**
	 * get the default timeout for remote sessions
	 * 
	 * @return the default session timeout
	 */
	long getDefaultSessionTimeout() {
		return defaultSessionTimeout;
	}

	/**
	 * 
	 * @param suggested
	 * @return the persistent session timeout
	 */
	long getPersistentSessionTimeout(long suggested) {
		if (suggested < minPersistentTimeoutMS)
			suggested = minPersistentTimeoutMS;
		if (suggested > maxPersistentTimeoutMS)
			suggested = maxPersistentTimeoutMS;

		return suggested;
	}

	/**
	 * get the default timeout for persistent sessions
	 * 
	 * @return the default timeout
	 */
	long getDefaultPersistentTimeout() {
		return defaultPersistentTimeoutMS;
	}

	/**
	 * Logs a message.
	 * 
	 * @param message
	 */
	void logMessage(String message) {
		logStream.println(dateFormat.format(new Date()) + "\t" + message);
		myNode.observerNotice(Event.HTTP_CONNECTOR_MESSAGE, myNode.getNodeId(), message);
	}

	/**
	 * Logs a session creation.
	 * 
	 * @param message
	 */
	void logSessionOpen(String message) {
		logStream.println(dateFormat.format(new Date()) + "\t" + message);
		myNode.observerNotice(Event.HTTP_CONNECTOR_SESSION_START, myNode.getNodeId(), message);
	}

	/**
	 * Logs a session closing.
	 * 
	 * @param message
	 */
	void logSessionClose(String message) {
		logStream.println(dateFormat.format(new Date()) + "\t" + message);
		myNode.observerNotice(Event.HTTP_CONNECTOR_SESSION_END, myNode.getNodeId(), message);
	}

	/**
	 * Logs a request.
	 * 
	 * @param request
	 */
	void logRequest(String request) {
		logStream.println(dateFormat.format(new Date()) + "\t Request:" + request);

		int lastServiceClassNamePosition = request.lastIndexOf("/");
		if (lastServiceClassNamePosition > 0) {
			String serviceClass = request.substring(1, lastServiceClassNamePosition);
			Agent service = null;
			/* temporarily disabled
			try {
				service = myNode.getServiceAgent(serviceClass);
			} catch (AgentNotKnownException e) {
				// Should be known..
				e.printStackTrace();
			}
			*/
			myNode.observerNotice(Event.HTTP_CONNECTOR_REQUEST, myNode.getNodeId(), service, request);
		}
		// Not a service call
		else {
			myNode.observerNotice(Event.HTTP_CONNECTOR_REQUEST, myNode.getNodeId(), request);
		}
	}

	/**
	 * Logs an error.
	 * 
	 * @param error
	 */
	void logError(String error) {
		logStream.println(dateFormat.format(new Date()) + "\t Error: " + error);
		myNode.observerNotice(Event.HTTP_CONNECTOR_ERROR, myNode.getNodeId(), error);
	}

	/**
	 * 
	 * @return true, if local running versions of services are preferred before broadcasting
	 */
	boolean preferLocalServices() {
		return preferLocalServices;
	}

}
