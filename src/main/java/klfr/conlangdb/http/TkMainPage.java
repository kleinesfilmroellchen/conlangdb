package klfr.conlangdb.http;

import java.io.*;
import java.util.logging.Logger;

import org.takes.*;
import org.takes.rs.*;

/**
 * Handler for the main page of the web application.
 */
public class TkMainPage implements Take {
	private static final Logger log = Logger.getLogger(TkMainPage.class.getCanonicalName());
	@Override
	public Response act(Request req) {
		log.fine("GET on main page");
		return new RsCWrap(new RsHtml("hey there, my friend!"));
	}
}