package klfr.conlangdb;

import klfr.conlangdb.handlers.*;

import com.sun.net.httpserver.*;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.http.*;
import java.util.Collection;
import java.util.Iterator;
import java.util.concurrent.*;
import java.util.logging.Logger;

/**
 * Main class of the server.
 */
class ServerMain extends CObject {
	private static final long serialVersionUID = 1L;

	private static final Logger log = Logger.getLogger(ServerMain.class.getCanonicalName());

	/**
	 * Simple class that holds command line arguments in object form.
	 */
	public static class Arguments extends CObject {
		/**
		 *
		 */
		private static final long serialVersionUID = 1L;
		public int port = 80;
		public String errorMessage = null;

		public String toString() { return f("Arguments(port=%d,error='%s')", port, errorMessage);}

		@Override
		public CObject clone() {
			Arguments nw = new Arguments();
			nw.port = this.port;
			nw.errorMessage = this.errorMessage;
			return nw;
		}
	}

	private final Arguments arguments;

	public ServerMain(Arguments arg) {
		this.arguments = arg;
	}

	/**
	 * Blocking main method.
	 */
	public void start() {
		try {
			log.config(() -> arguments.toString());
			HttpServer server = HttpServer.create(new InetSocketAddress(arguments.port), 0);
			log.info(() -> f("ConlangDB server started on port %3d", arguments.port));
			server.createContext("/", new MainPageHandler());
			server.setExecutor(new ThreadPoolExecutor(1, 10, 16, TimeUnit.SECONDS, new LinkedBlockingQueue<>(10)));
			server.start();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		try {
			while (true)
				Thread.sleep(20000);
		} catch (InterruptedException e) {
			throw new RuntimeException(e);
		}
	}

	@Override
	public CObject clone() {
		return new ServerMain(this.arguments);
	}
}