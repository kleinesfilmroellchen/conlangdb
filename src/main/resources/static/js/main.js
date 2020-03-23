/*
 * Global settings object SETTINGS.
 * Attributes:
 *     pagename: Name of page that should appear in the title.
 * 
 */

const translation = getPageTranslation();

async function getPageTranslation() {
	const language = new URLSearchParams(window.location.search).get("lang") || navigator.language.replace("-", "_");
	console.info("Language", language);
	// The server adds all the English texts where the actual translation file has no text translated.
	return fetch(`/translation/${language}`).then(result => result.json()).catch(() => fetch(`/translation/en`));
}

// Triggered on body load by the onload HTML attribute
function bodyLoaded() {
	const title = document.querySelector("#title");
	translation.then(translations => {
		const generalName = translations['t--conlangdb'];
		title.innerHTML = generalName + " — " + translations[`t--title-${SETTINGS.pagename}`]
		return translations;
	});
	console.log(translation);
	translation.then(translations => {
		for (const translationKey in translations) {
			const translationValue = translations[translationKey];
			const applicableTo = document.querySelectorAll('.' + translationKey);
			console.log(applicableTo);
			applicableTo.forEach(node => {
				//Only if the node has no content or only whitespace:
				if (node.innerHTML.trim().length <= 0)
					node.innerHTML = translationValue;
			})
		}
		return translations;
	});
}