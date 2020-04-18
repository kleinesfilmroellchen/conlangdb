package klfr.conlangdb;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import org.json.JSONObject;

/**
 * Handles putting together a translation dictionary that covers both fallback
 * methods. Returned objects can immediately be serialized and sent to the
 * client.
 */
public class TranslationProvider extends CObject {
	private static final long serialVersionUID = 1L;
	private static final Logger log = Logger.getLogger(TranslationProvider.class.getCanonicalName());

	/**
	 * Default translation dictionary which are loaded only once on startup for
	 * efficiency. Using the standard method will also place it into the cache.
	 */
	private static Map<String, String> defaultTranslations = jsonToMap(
			CResources.openJSON("translation/en.json").get());

	/**
	 * Translation cache that stores a translation dictionary for a given locale
	 * once created.
	 */
	private static final Map<TranslationLocale, JSONObject> translations = new ConcurrentHashMap<TranslationLocale, JSONObject>(
			5, 0.6f);

	/**
	 * Simple hashable immutable class that holds information about a language and
	 * possibly a region.
	 */
	private static class TranslationLocale extends CObject implements Comparable<TranslationLocale> {
		private static final long serialVersionUID = 1L;
		private final String language;
		private final Optional<String> region;

		public TranslationLocale(String language, Optional<String> region) {
			this.language = language.toLowerCase();
			this.region = region;
		}

		public String toString() {
			return "TransLocale:" + this.language
					+ (this.region.isPresent() ? ("_" + this.region.get().toUpperCase()) : "");
		}

		public boolean equals(Object other) {
			if (other instanceof TranslationLocale) {
				var otherTL = (TranslationLocale) other;
				return otherTL.language.equals(this.language) && otherTL.region.equals(this.region);
			}
			return false;
		}

		@Override
		public int hashCode() {
			return this.region.hashCode() ^ this.language.hashCode() ^ 0xdeafb00d;
		}

		@Override
		public CObject clone() {
			return new TranslationLocale(language, region);
		}

		@Override
		public int compareTo(TranslationLocale o) {
			final var compareLang = this.language.compareTo(o.language);
			if (compareLang == 0) {
				// if languages are equal, compare regions
				return this.region.isPresent() ? (o.region.isPresent() ?
				// compare regions if both are present
						this.region.get().compareTo(o.region.get()) :
						// only we have region, other is greater
						-1) : (
				// only other has region, we are greater, otherwise both have no region and we
				// are equal
				o.region.isPresent() ? 1 : 0);
			}
			return compareLang;
		}
	}

	@Override
	public CObject clone() {
		return new TranslationProvider();
	}

	public static JSONObject getTranslation(String language) {
		return getTranslation(language, Optional.empty());
	}

	public static JSONObject getTranslation(String language, String region) {
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
	public static JSONObject getTranslation(String language, Optional<String> region) {
		var tl = new TranslationLocale(language, region);
		//// Search for a translation in the map
		if (translations.containsKey(tl)) {
			log.fine(f("Translation found for %s", tl));
			return translations.get(tl);
		}

		//// Otherwise, generate the translation for the locale
		log.info(f("Generating translation for %s...", tl));
		Map<String, String> translationDict = new HashMap<>(100);
		// Java maps provide a putAll() function that puts all of the key-value mappings
		// of the parameter map into the map being modified. As this overrides any
		// existing mappings that are present, we need to add the translations in
		// reverse order: first, all default translations, then language translations,
		// then region-language translations.
		//// Defaults:
		translationDict.putAll(defaultTranslations);

		//// Language:
		var maybeLangDict = CResources.openJSON("translation/" + language.replace(".", "").toLowerCase() + ".json");
		if (maybeLangDict.isPresent()) {
			// add all language keys: this should override almost every key present
			translationDict.putAll(jsonToMap(maybeLangDict.get()));
		}

		//// Region:
		if (region.isPresent()) {
			var maybetranslationDict = CResources.openJSON(f("translation/%s_%s.json",
					language.replace(".", "").toLowerCase(), region.get().replace(".", "").toUpperCase()));
			if (maybetranslationDict.isPresent())
				translationDict.putAll(jsonToMap(maybetranslationDict.get()));
		}
		// remove schema from dictionary. This removes information that the user doesn't
		// need and prevents an "invalid selector" error on the JS translation frontend.
		translationDict.remove("$schema");
		//// Store the newly created translation for later reuse and return it
		var translationDictJson = mapToJSON(translationDict);
		translations.put(tl, translationDictJson);
		return translationDictJson;
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

	/**
	 * Creates a JSONObject from the standard Java map by adding each key-value
	 * pair.
	 * 
	 * @param map The map to convert.
	 * @return a new JSONObject with only strings as values.
	 */
	public static JSONObject mapToJSON(final Map<String, String> map) {
		final var obj = new JSONObject();
		for (var kv : map.entrySet())
			obj.put(kv.getKey(), kv.getValue());
		return obj;
	}
}