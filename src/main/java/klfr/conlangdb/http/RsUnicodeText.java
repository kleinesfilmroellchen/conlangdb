package klfr.conlangdb.http;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.takes.Response;

import klfr.conlangdb.util.StringStreamUtil;

/**
 * Basic response that will create a response only with a body containing the
 * given text. The text is encoded in UTF-8 first, but the user must ensure that
 * this is specified to the client in the Content-Type header. The only header
 * added is a "Content-Length" header with the length of the given body when
 * encoded in UTF-8. If you are planning to modify the body after the fact, you
 * should remove this header first, as it will lead browsers to read not
 * enough/too much of your response.
 */
public class RsUnicodeText implements Response {
	private final String body;

	public RsUnicodeText(CharSequence body) {
		this.body = body.toString();
	}

	/**
	 * This response only adds a Content-Length header. The length value is computed
	 * from the body's length when encoded in individual UTF-8 bytes. ASCII
	 * characters will always use up one byte and characters on the basic
	 * multilingual plane (BMP) will use up two bytes. Examples are the Latin
	 * diacritic and variant characters such as é, À, Ö or ß, the Cyrillic script
	 * e.g. ф, Д, в or у, CJK (Chinese, Japanese, Korean) characters e.g. 私, あいこ,
	 * 작은.
	 */
	@Override
	public Iterable<String> head() throws IOException {
		return List.of("Content-Length: " + Integer.toString(StringStreamUtil.contentLength(body)));
	}

	@Override
	public InputStream body() throws IOException {
		return StringStreamUtil.streamify(body);
	}
}