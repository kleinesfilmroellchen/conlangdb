package klfr.conlangdb.http;

import org.takes.Request;
import org.takes.Response;
import org.takes.Take;

import klfr.conlangdb.CObject;

public class TkSingleWordAPI extends CObject {
	private static final long serialVersionUID = 1L;

	public static class Get extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Get();
		}
	}

	public static class Post extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Post();
		}
	}

	public static class Delete extends CObject implements Take {
		private static final long serialVersionUID = 1L;

		@Override
		public Response act(Request arg0) {
			// TODO Auto-generated method stub
			return null;
		}

		@Override
		public CObject clone() {
			return new Delete();
		}
	}

	@Override
	public CObject clone() {
		return new TkSingleWordAPI();
	}

}
