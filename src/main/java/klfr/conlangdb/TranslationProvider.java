package klfr.conlangdb;

import java.util.*;

import org.json.*;

/**
 * Handles putting together a translation dictionary that covers both fallback
 * methods. Returned objects can immediately be serialized and sent to the
 * client.
 */
class TranslationProvider extends CObject {
	private static final long serialVersionUID = 1L;

	/**
	 * Default translations which are loaded only once on startup for efficiency.
	 */
	private static final Map<String, String> defaultTranslations = getTranslation("en");

	// TODO: implement storage of locale translations

	@Override
	public CObject clone() {
		return new TranslationProvider();
	}

	public static Map<String, String> getTranslation(String language) {
		return getTranslation(language, Optional.empty());
	}

	public static Map<String, String> getTranslation(String language, String region) {
		return getTranslation(language, Optional.ofNullable(region));
	}

	/**
	 * Returns the translation dictionary for the specified language and, if given,
	 * the specified region.<br>
	 * <br>
	 * The translations are searched in the following manner: The main translation
	 * file is the file with the identical language and region code, which may not
	 * exist. The secondary translation file is the file with only the language
	 * code, which may not exist. The universal fallback translation file is the
	 * file with the language code {@code en}, which must always exist. The returned
	 * translation dictionary contains all of the keys from the primary translation
	 * file, as well as all keys from the secondary file that are missing, as well
	 * as all keys from the default file that are missing, in this exact order. This
	 * means that more specific local terms take precedence over general language
	 * terms and fallback English terms. This also allows translators to e.g. only
	 * translate a couple of specific terms for a given region where such a specific
	 * translation might be important, and leave all other translations to the
	 * language's default. Also, one can detect untranslated terms by them appearing
	 * in English.
	 * 
	 * @param language The ISO-639 two- or three-letter code for the language that
	 *                 should
	 * @param region   The ISO-3166 two- or three-letter code for the country or
	 *                 region that a translation should be retrieved for. If the
	 *                 region is unspecified or a translation file for the specified
	 *                 language-region combination could not be found, the region is
	 *                 ignored.
	 * @return A translation dictionary that represents the translations for the
	 *         specified language and region, and, if necessary, includes fallback
	 *         keys from language and default translations.
	 */
	public static Map<String, String> getTranslation(String language, Optional<String> region) {
		Map<String, String> translationDict = new HashMap<>(50);
		if (region.isPresent()) {
			var maybetranslationDict = CResources.openJSON(f("translation/%s_%s.json",
					language.replace(".", "").toLowerCase(), region.get().replace(".", "").toLowerCase()));
			if (maybetranslationDict.isPresent())
				translationDict = jsonToMap(maybetranslationDict.get());
		}
		var maybeLangDict = CResources.openJSON("translation/" + language.replace(".", "").toLowerCase() + ".json");
		if (maybeLangDict.isPresent()) {
			// fallback for every language key
			var langDict = jsonToMap(maybeLangDict.get());
		}
	}

	/**
	 * Creates a standard Java Map from the JSON object by treating all of its
	 * values as strings.
	 * 
	 * @param obj JSON object which is also a map, but in the wrong format.
	 * @return key-value mapping as Map collection.
	 */
	public static Map<String, String> jsonToMap(final JSONObject obj) {
		final var keys = obj.keySet();
		final HashMap<String, String> map = new HashMap<>(keys.size());
		for (var key : keys)
			map.put(key, obj.get(key).toString());
		return map;
	}
}