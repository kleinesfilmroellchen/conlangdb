package klfr.conlangdb;

import java.io.*;
import java.nio.charset.Charset;
import java.util.Optional;

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
	 * Opens a resource to a file as a binary stream
	 * 
	 * @param rname
	 * @return
	 */
	public static Optional<InputStream> openBinary(String rname) {
		try {
			return Optional.ofNullable(MODULE_LOADER.getResourceAsStream(RESOURCE_PATH + "/" + rname));
		} catch (IOException | NullPointerException e) {
			return Optional.empty();
		}
	}

	/**
	 * Opens a resource as UTF-8 text file, which is the most common format. Might
	 * return Nothing.
	 * 
	 * @param rname Name of the resource inside the resource folder
	 * @return A reader accessing the resource with UTF-8 encoding.
	 */
	public static Optional<Reader> open(String rname) {
		try {
			return Optional.ofNullable(new InputStreamReader(
					MODULE_LOADER.getResourceAsStream(RESOURCE_PATH + "/" + rname), Charset.forName("utf-8")));
		} catch (IOException | NullPointerException e) {
			return Optional.empty();
		}
	}

	public static Optional<JSONObject> openJSON(String rname) {
		var r = open("translation/en.json");
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

	@Override
	public CObject clone() {
		return new CResources();
	}
}