package klfr.conlangdb.util;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.SequenceInputStream;
import java.io.StringWriter;
import java.nio.charset.Charset;
import java.nio.charset.IllegalCharsetNameException;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.logging.Logger;

/**
 * Static class with methods for working with strings, streams, readers/writers
 * and their conversions.
 */
public final class StringStreamUtil {

	private static final Logger log = Logger.getLogger(StringStreamUtil.class.getCanonicalName());

	/**
	 * Decodes the input stream's contents to a string and assumes UTF-8 encoding.
	 * 
	 * @param s The input stream to decode.
	 * @return a new string with the stream contents.
	 * @throws IOException if the input stream fucks up.
	 */
	public static String stringify(InputStream s) throws IOException {
		return stringify(s, Charset.forName("utf-8"));
	}

	/**
	 * Decodes the input stream's contents to a string.
	 * 
	 * @param s       The input stream to decode.
	 * @param charset The character set with which the input stream is encoded.
	 * @return a new string with the stream contents.
	 * @throws IOException if the input stream fucks up.
	 */
	public static String stringify(InputStream s, CharSequence charset)
			throws IOException, IllegalCharsetNameException, UnsupportedCharsetException {
		return stringify(s, Charset.forName(charset.toString()));

	}

	/**
	 * Decodes the input stream's contents to a string.
	 * 
	 * @param s       The input stream to decode.
	 * @param charset The character set with which the input stream is encoded.
	 * @return a new string with the stream contents.
	 * @throws IOException if the input stream fucks up.
	 */
	public static String stringify(InputStream s, Charset charset) throws IOException {
		final var r = new InputStreamReader(s, charset);
		final var w = new StringWriter();
		r.transferTo(w);
		return w.toString();
	}

	/**
	 * Makes an encoded input stream from the string.
	 */
	public static InputStream streamify(final CharSequence s, Charset charset) {
		return new ByteArrayInputStream(s.toString().getBytes(charset));
	}

	/**
	 * Makes an encoded input stream from the string.
	 * 
	 * @throws UnsupportedCharsetException If the given charset name does not refer
	 *                                     to a charset.
	 */
	public static InputStream streamify(final CharSequence s, CharSequence charset) throws UnsupportedCharsetException {
		return streamify(s, Charset.forName(charset.toString()));
	}

	/**
	 * Makes a UTF-8 encoded input stream from the string.
	 */
	public static InputStream streamify(final CharSequence s) {
		return streamify(s, Charset.forName("utf-8"));
	}

	/**
	 * Determines the length of the character sequence in bytes when encoded in the
	 * charset encoding. This is useful for the Content-Length header of HTTP
	 * responses.
	 * 
	 * @param s       The sequence to be encoded.
	 * @param charset The charset to be used for encoding.
	 * @throws UnsupportedCharsetException If the given charset name does not refer
	 *                                     to a charset.
	 * @return The number of bytes in the string when encoded with the given
	 *         charset.
	 */
	public static Integer contentLength(CharSequence s, CharSequence charset) throws UnsupportedCharsetException {
		return contentLength(s, Charset.forName(charset.toString()));
	}

	/**
	 * Determines the length of the character sequence in bytes when encoded in the
	 * charset encoding. This is useful for the Content-Length header of HTTP
	 * responses.
	 * 
	 * @param s       The sequence to be encoded.
	 * @param charset The charset to be used for encoding.
	 * @return The number of bytes in the string when encoded with the given
	 *         charset.
	 */
	public static Integer contentLength(CharSequence s, Charset charset) {
		return s.toString().getBytes(charset).length;
	}

	/**
	 * Determines the length of the character sequence in bytes when encoded in
	 * UTF-8. This is useful for the Content-Length header of HTTP responses.
	 * 
	 * @param s The sequence to be encoded.
	 * @return The number of bytes in the string when encoded with UTF-8.
	 */
	public static Integer contentLength(CharSequence s) {
		return contentLength(s, Charset.forName("utf-8"));
	}

	/**
	 * Creates a sequenced input stream which concatenates all the input stream's
	 * output together.
	 * 
	 * @param streams The input streams to use.
	 * @return A SequenceInputStream.
	 */
	public static InputStream sequencify(final InputStream... streams) {
		return new SequenceInputStream(new Enumeration<InputStream>() {
			private int i = 0;

			@Override
			public boolean hasMoreElements() {
				return i < streams.length;
			}

			@Override
			public InputStream nextElement() {
				return streams[i++];
			}
		});
	}

	public static <T> Iterable<T> sequencify(final Iterable<T>... iterables) {
		return new Iterable<T>() {
			@Override
			public Iterator<T> iterator() {
				return new Iterator<T>() {
					private int i = 0;
					private Iterator<T> currentIterator = iterables[i].iterator();

					@Override
					public boolean hasNext() {
						if (currentIterator.hasNext())
							return true;
						while (!currentIterator.hasNext() && i < iterables.length - 1) {
							currentIterator = iterables[++i].iterator();
							if (currentIterator.hasNext())
								return true;
						}
						return false;
					}

					@Override
					public T next() {
						log.fine("sequence iterator %d of %d: %s. hasnext=%s".formatted(i, iterables.length - 1,
								currentIterator, currentIterator.hasNext()));
						if (currentIterator.hasNext()) {
							log.fine("normal next");
							return currentIterator.next();
						}
						log.fine("proceed to another iterator and try again");
						if (i >= iterables.length - 1)
							throw new NoSuchElementException();
						currentIterator = iterables[++i].iterator();
						return next();
					}

				};
			}
		};
	}
}