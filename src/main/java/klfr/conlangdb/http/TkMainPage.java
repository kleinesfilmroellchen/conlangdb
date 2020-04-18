package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedList;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import klfr.conlangdb.CResources;

/**
 * Handler for the main page of the web application.
 */
public class TkMainPage implements Take {

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