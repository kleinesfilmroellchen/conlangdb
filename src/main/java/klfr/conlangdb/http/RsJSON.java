package klfr.conlangdb.http;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.List;

import org.json.JSONArray;
import org.json.JSONObject;
import org.takes.Response;

/**
 * A simple response that uses JSON data as its body. Appropriately, the
 * Content-Type and Content-Length headers are set. The JSON data is recieved
 * either from a preformatted String containing JSON, or from a JSONObject as
 * specified and implemented by the {@link org.json} Java JSON reference
 * implementation. This makes this class ideal for operation with other systems
 * which use the reference implementation of JSON in Java.
 */
public class RsJSON implements Response {

	private final String json;

	/**
	 * Standard constructor which accepts a JSON Object from the reference JSON
	 * implementation. The JSON is pretty-printed for ease of debugging. This, of
	 * course, does not affect the data in any way, but may be significant if a lot
	 * of JSON data is transferred and the amount of data is significantly increased
	 * by the pretty-printing. Use the String constructor with the already
	 * stringified JSON in such a case.
	 * 
	 * @param json A JSONObject from the reference implementation {@link org.json}.
	 *             This also ensures that the JSON is always valid, which is one of
	 *             the basic guarantees made by the JSON representing data
	 *             structures in org.json.
	 */
	public RsJSON(JSONObject json) {
		this.json = json.toString(2);
	}

	/**
	 * Standard constructor which accepts a JSON Array from the reference JSON
	 * implementation. The JSON is pretty-printed for ease of debugging. This, of
	 * course, does not affect the data in any way, but may be significant if a lot
	 * of JSON data is transferred and the amount of data is significantly increased
	 * by the pretty-printing. Use the String constructor with the already
	 * stringified JSON in such a case.
	 * 
	 * @param json A JSONArray from the reference implementation {@link org.json}.
	 *             This also ensures that the JSON is always valid, which is one of
	 *             the basic guarantees made by the JSON representing data
	 *             structures in org.json.
	 */
	public RsJSON(JSONArray json) {
		this.json = json.toString(2);
	}

	/**
	 * Alternative constructor which will accept any string(like) containing JSON.
	 * This is useful for creating simple JSON structures or when using other JSON
	 * libraries.
	 * 
	 * @param jsonAsString JSON in string form. Its validity is not checked.
	 */
	public RsJSON(CharSequence jsonAsString) {
		this.json = jsonAsString.toString();
	}

	/**
	 * This response only adds the Content-Type as MIME type
	 * {@code application/json} and the Content-Length as the UTF-8 encoded length
	 * of the given JSON data in string form.
	 */
	@Override
	public Iterable<String> head() throws IOException {
		return List.of("Content-Type: application/json; charset=UTF-8",
				"Content-Length: " + Integer.toString(json.getBytes(Charset.forName("utf-8")).length));
	}

	@Override
	public InputStream body() throws IOException {
		return streamify(json);
	}

	/**
	 * Makes a UTF-8 encoded input stream from the string.
	 */
	public static InputStream streamify(final String s) {
		return new ByteArrayInputStream(s.getBytes(Charset.forName("utf-8")));
	}

}