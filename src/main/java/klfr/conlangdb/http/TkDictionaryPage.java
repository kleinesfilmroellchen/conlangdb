package klfr.conlangdb.http;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import klfr.conlangdb.CObject;

public class TkDictionaryPage extends CObject implements Take {
	private static final long serialVersionUID = 1L;

	@Override
	public Response act(Request arg0) {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public CObject clone() {
		return new TkDictionaryPage();
	}

}
