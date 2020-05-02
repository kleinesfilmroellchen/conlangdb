package klfr.conlangdb.http;

import java.util.List;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;
import org.takes.rs.RsSimple;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.util.StringStreamUtil;

public class TkSingleWordPage extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	@Override
	public Response act(Request arg0) {
		return new RsSimple(List.of(), CResources.openBinary("html/word-view.html").get());
	}

	@Override
	public CObject clone() {
		return new TkSingleWordPage();
	}

	public static class Headers extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request arg0) {
			return new RsSimple(List.of(), StringStreamUtil.streamify("<script src=\"/js/word-action.js\"></script>"));
		}

		@Override
		public CObject clone() {
			return new Headers();
		}
		
	}

}
