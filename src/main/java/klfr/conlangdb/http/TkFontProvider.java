package klfr.conlangdb.http;

import java.io.IOException;
import java.sql.SQLException;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.takes.Response;
import org.takes.facets.fork.RqRegex;
import org.takes.facets.fork.TkRegex;

import klfr.conlangdb.CObject;
import klfr.conlangdb.CResources;
import klfr.conlangdb.database.DatabaseCommand;
import klfr.conlangdb.database.DatabaseCommunicator;
import klfr.conlangdb.http.util.HttpStatusCode;
import klfr.conlangdb.http.util.RsUnicodeText;
import klfr.conlangdb.util.StringStreamUtil;

public class TkFontProvider extends CObject implements TkRegex {

	public static final Pattern requestPath = Pattern.compile("/conscript/(\\S{1,3})");

	public static final Pattern fontUrlReplaceSequence = Pattern.compile(Pattern.quote("%%JAVA-FONT-FILE%%"));

	/**
	 * Simple database command which retrieves the font URL (i.e. font file base
	 * name in the font static resource folder) for a given language ID, or Nothing
	 * if the language has no font url specified or the language ID is not found in
	 * the database.
	 */
	public static class GetFontUrl extends DatabaseCommand<String> {
		private static final long serialVersionUID = 1L;
		public final String languageID;

		protected GetFontUrl(final String languageID) {
			super(con -> {
				try {
					final var stmt = con.prepareStatement("select fonturl from tlanguage where id=?;");
					stmt.setString(1, languageID);
					final var rset = stmt.executeQuery();
					if (!rset.next())
						return Nothing();
					return Just(rset.getString("fonturl"));
				} catch (SQLException e) {
					return Nothing();
				}
			});
			this.languageID = languageID;
		}

		@Override
		public Stream<Object> getArguments() {
			return Stream.of(languageID);
		}

		@Override
		public Optional<Object> getArgument(int index) {
			return index == 0 ? Just(languageID) : Nothing();
		}

		@Override
		public DatabaseCommand<String> clone() {
			return new GetFontUrl(languageID);
		}

	}

	@Override
	public Response act(RqRegex rq) {
		try {
			rq.matcher().matches();
			final String language = rq.matcher().group(1);
			final var cmd = new GetFontUrl(language);
			DatabaseCommunicator.submitCommand(cmd);
			final var maybeFontUrl = cmd.get();
			if (maybeFontUrl.isEmpty() || maybeFontUrl.get().isEmpty())
				return new RsCWrap(HttpStatusCode.NO_CONTENT);
			final var fonturl = maybeFontUrl.get();
			final var css = fontUrlReplaceSequence
					.matcher(StringStreamUtil.stringify(CResources.open("native-font.css").get()))
					.replaceAll(fonturl);
			log.finer(css);
			return new RsCWrap(new RsUnicodeText(css), HttpStatusCode.OK);
		} catch (InterruptedException | ExecutionException | IOException e) {
			log.log(Level.SEVERE, "", e);
			return new RsCWrap(HttpStatusCode.INTERNAL_SERVER_ERROR);
		}

	}

	@Override
	public CObject clone() {
		return new TkFontProvider();
	}

}