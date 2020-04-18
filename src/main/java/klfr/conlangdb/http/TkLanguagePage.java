package klfr.conlangdb.http;

import java.io.IOException;
import java.util.List;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rq.RqHref;
import org.takes.rs.RsSimple;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.util.StringStreamUtil;

/**
 * HTML page for the single language page. Responds to the action=create query
 * parameter and serves a different "create language" page.
 */
public class TkLanguagePage extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	@Override
	public Response act(final Request req) {
		try {
			final var actionParam = new RqHref.Smart(req).single("action", "edit");
			if (actionParam.equals("create")) {
				return new RsSimple(List.of(), CResources.openBinary("html/language-create.html").get());
			}
			return new RsSimple(List.of(), CResources.openBinary("html/language-view.html").get());
		} catch (final IOException e) {
			return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
		}
	}

	public static class Headers extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request req) {
			return new RsSimple(List.of(),
					StringStreamUtil.streamify("<script src=\"/js/language-action.js\"></script>"));
		}

		@Override
		public CObject clone() {
			return new Headers();
		}

	}

	@Override
	public CObject clone() {
		return new TkLanguagePage();
	}

}
