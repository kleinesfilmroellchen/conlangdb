package klfr.conlangdb.http;

import static klfr.conlangdb.http.RsUnicodeText.streamify;

import java.util.List;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsSimple;

import klfr.conlangdb.CResources;

/**
 * HTML version of the language list.
 */
public class TkLanguageListPage implements Take {

	@Override
	public Response act(Request arg0) {
		return new RsSimple(List.of(), CResources.openBinary("html/language-list.html").get());
	}

	public static class Header implements Take {
		public Response act(Request req) {
			return new RsSimple(List.of(), streamify("<script src=\"/js/language-list.js\"></script>"));
		}
	}

}
