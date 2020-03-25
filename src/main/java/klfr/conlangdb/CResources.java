package klfr.conlangdb;

import java.io.*;
import java.nio.charset.Charset;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Properties;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Class accessing non-code resources that are in the module's resource folder.
 */
public final class CResources extends CObject {
	private static final long serialVersionUID = 1L;
	/**
	 * Name of the top-level folder that contains all resources, relative to the
	 * module-info.class.
	 */
	public static final String RESOURCE_PATH = "res";
	private static final Module MODULE_LOADER = CResources.class.getModule();

	/**
	 * Opens a resource to a file as a binary stream.
	 * 
	 * @param rname The name of the resource file as a path relative to the resource
	 *              root folder. <strong>ATTENTION: This method does not sanitize
	 *              the path. Although the resource loading mechanism of the module
	 *              loader should prevent it, it is possible to escape the protected
	 *              folder by using /.. in the path. Callers of this method that
	 *              pass in user controlled strings such as HTTP request paths and
	 *              parameters, REST API JSON strings etc. should always sanitize
	 *              their input first.</strong>
	 * @return An input stream (binary) that reads from the resource, or Nothing if
	 *         the specified resource could not be found or an IO error occurs.
	 */
	public static Optional<InputStream> openBinary(String rname) {
		log.fine(f("RESOURCE %s open binary", rname));
		try {
			return Optional.ofNullable(MODULE_LOADER.getResourceAsStream(RESOURCE_PATH + "/" + rname));
		} catch (IOException | NullPointerException e) {
			return Optional.empty();
		}
	}

	/**
	 * Opens a resource as UTF-8 encoded text file, which is the most common format.
	 * 
	 * @param rname The name of the resource file as a path relative to the resource
	 *              root folder. <strong>ATTENTION: This method does not sanitize
	 *              the path. Although the resource loading mechanism of the module
	 *              loader should prevent it, it is possible to escape the protected
	 *              folder by using /.. in the path. Callers of this method that
	 *              pass in user controlled strings such as HTTP request paths and
	 *              parameters, REST API JSON strings etc. should always sanitize
	 *              their input first.</strong>
	 * @return A reader accessing the resource with UTF-8 encoding, or Nothing if
	 *         the specified resource could not be found or an IO error occurs.
	 */
	public static Optional<Reader> open(String rname) {
		log.fine(f("RESOURCE %s open", rname));
		try {
			return Optional.ofNullable(new InputStreamReader(
					MODULE_LOADER.getResourceAsStream(RESOURCE_PATH + "/" + rname), Charset.forName("utf-8")));
		} catch (IOException | NullPointerException e) {
			return Optional.empty();
		}
	}

	/**
	 * Opens a resource as UTF-8 encoded text file and parses it into a JSON
	 * (JavaScript object notation) object.
	 * 
	 * @param rname The name of the resource file as a path relative to the resource
	 *              root folder. <strong>ATTENTION: This method does not sanitize
	 *              the path. Although the resource loading mechanism of the module
	 *              loader should prevent it, it is possible to escape the protected
	 *              folder by using /.. in the path. Callers of this method that
	 *              pass in user controlled strings such as HTTP request paths and
	 *              parameters, REST API JSON strings etc. should always sanitize
	 *              their input first.</strong>
	 * @return A JSONObject that contains the JSON data of the file. Or Nothing if
	 *         the specified resource could not be found, the JSON is malformed or
	 *         an IO error occurs.
	 */
	public static Optional<JSONObject> openJSON(String rname) {
		var r = open(rname);
		return ifelse(() -> {
			var json = new StringWriter();
			var reader = r.get();
			try {
				reader.transferTo(json);
				return Optional.ofNullable(new JSONObject(json.toString()));
			} catch (IOException | JSONException e) {
				return Optional.<JSONObject>empty();
			}
		}, () -> Optional.<JSONObject>empty()).apply(r.isPresent());
	}

	/**
	 * Loads and parses a Java Property file, which is a simple format for
	 * string-string mapping.
	 * 
	 * @param propname The name of the resource file as a path relative to the
	 *                 resource root folder. <strong>ATTENTION: This method does not
	 *                 sanitize the path. Although the resource loading mechanism of
	 *                 the module loader should prevent it, it is possible to escape
	 *                 the protected folder by using /.. in the path. Callers of
	 *                 this method that pass in user controlled strings such as HTTP
	 *                 request paths and parameters, REST API JSON strings etc.
	 *                 should always sanitize their input first.</strong>
	 * @return A Properties object that contains the Java property data of the file.
	 *         Or Nothing if the specified resource could not be found, the property
	 *         file is malformed or an IO error occurs.
	 */
	public static Optional<Properties> openProperties(final String propname) {
		final var propReader = open(propname);
		final var props = new Properties();
		try {
			props.load(propReader.get());
			propReader.get().close();
			return Just(props);
		} catch (IOException | NoSuchElementException | IllegalArgumentException | NullPointerException e) {
			return Nothing();
		}
	}

	@Override
	public CObject clone() {
		return new CResources();
	}
}