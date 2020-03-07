package klfr.conlangdb.handlers;

import java.io.*;

import com.sun.net.httpserver.*;

/**
 * Handler for the main page of the web application.
 */
public class MainPageHandler extends GeneralHandler {
	private static final long serialVersionUID = 1L;

	@Override
	protected boolean doGet(HttpExchange exchange) throws IOException {
		log.fine("do GET");
		bodyWriter.write("<p>Hello, World!</p>");
		return false;
	}

	@Override
	protected boolean doHead(HttpExchange exchange) throws IOException {
		log.fine("do HEAD");
		exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
		return false;
	}

	@Override
	protected boolean doPost(HttpExchange exchange) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected boolean doPut(HttpExchange exchange) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	protected boolean doDelete(HttpExchange exchange) throws IOException {
		// TODO Auto-generated method stub
		return false;
	}
}