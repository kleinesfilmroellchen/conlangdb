package klfr.conlangdb.http;

import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.util.Optional;

import org.json.JSONObject;
import org.takes.Response;
import org.takes.facets.fork.RqRegex;
import org.takes.facets.fork.TkRegex;
import org.takes.rs.RsEmpty;
import org.takes.rs.RsText;
import org.takes.rs.RsWithBody;
import org.takes.rs.RsWithHeader;
import org.takes.rs.RsWithType;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.TranslationProvider;

/**
 * Take for processing translations. This is a Regex take which recieves the
 * regex-matched request and can extract the language from the request path,
 * which is of the form {@code /translation/<lang>} with <lang> being a
 * POSIX-style combination of ISO-639 language code in lower case and optionally
 * an underscore and an ISO-3166 region code in uppercase. Examples: {@code en}
 * for English (not region-specific), {@code de_AT} for German (Austria),
 * {@code jp_JP} for Japanese (Japan).
 */
public class TkTranslations extends CObject implements TkRegex {
	private static final long serialVersionUID = 1L;

	/**
	 * @param regex The regex that parses the language code and region code given in
	 *              the path. Language is in group 1. Region is in group 2.
	 */
	@Override
	public Response act(RqRegex regex) {
		var rx = regex.matcher();
		rx.matches();
		String lang = rx.group(1), region = rx.group(2);
		JSONObject translation = TranslationProvider.getTranslation(lang, Optional.ofNullable(region));
		log.finer("Translation: " + translation.toString(2));
		return new RsWithHeader(
				new RsWithType(new RsWithBody(translation.toString()), "application/json", Charset.forName("utf-8")),
				"Connection", "close");
	}

	@Override
	public CObject clone() {
		return new TkTranslations();
	}

}