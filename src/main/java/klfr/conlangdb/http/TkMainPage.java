package klfr.conlangdb.http;

import java.io.*;
import java.util.LinkedList;
import java.util.logging.Logger;

import org.takes.*;

import klfr.conlangdb.CResources;

/**
 * Handler for the main page of the web application.
 */
public class TkMainPage implements Take {
	private static final Logger log = Logger.getLogger(TkMainPage.class.getCanonicalName());

	@Override
	public Response act(Request req) {
		return new Response() {

			@Override
			public Iterable<String> head() throws IOException {
				return new LinkedList<String>();
			}

			@Override
			public InputStream body() throws IOException {
				return CResources.openBinary("html/mainpage.html").get();
			}
			
		};
	}
}