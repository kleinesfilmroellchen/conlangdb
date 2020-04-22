package klfr.conlangdb.http.util;

import java.io.IOException;
import java.io.InputStream;

import org.takes.Request;
import org.takes.rq.RqHeaders;

import klfr.conlangdb.CObject;

/**
 * A request decorator that will limit its body input stream to however many
 * bytes are indicated by the Content-Length header.
 */
public class RqBody extends CObject implements Request {
	private static final long serialVersionUID = 1L;

	private final Request inner;

	public RqBody(final Request inner) {
		this.inner = inner;
	}

	@Override
	public Iterable<String> head() throws IOException {
		return inner.head();
	}

	@Override
	public InputStream body() throws IOException {
		final var innerBody = inner.body();

		// figure out how many bytes to read
		final var maxreadStr = new RqHeaders.Smart(this).single("Content-Length", "0");
		var maxread_ = 0;
		try {
			maxread_ = Integer.parseInt(maxreadStr);
		} catch (final NumberFormatException e) {
		}
		final var maxread = maxread_;

		return new InputStream() {
			private int read = 0;

			@Override
			public int read() throws IOException {
				if (read >= maxread)
					return -1;
				final int innerData = innerBody.read();
				read++;
				// will also stop if the inner request has no more body data
				return innerData;
			}

			@Override
			public byte[] readAllBytes() throws IOException {
				return this.readNBytes(maxread - read);
			}

			@Override
			public byte[] readNBytes(int len) throws IOException {
				len = Math.min(len, maxread - read);
				if (len <= 0)
					return new byte[0];
				return innerBody.readNBytes(len);
			}
		};
	}

	@Override
	public CObject clone() {
		return new RqBody(inner);
	}

}