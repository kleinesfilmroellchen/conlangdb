/*
 * Global settings object SETTINGS.
 * Attributes:
 *     pagename: Name of page that should appear in the title.
 * 
 */

const translation = getPageTranslation();

async function getPageTranslation() {
	const language = new URLSearchParams(window.location.search).get("lang") || navigator.language;
	// The server adds all the English texts where the actual translation file has no text translated.
	return fetch(`/translation/${language}`).then(result => result.json()).then();
}

function bodyLoaded() {
	const title = document.querySelector("#title");
	translation.then(translations => {
		const generalName = translations['t--conlangdb'];
		title.innerHTML = generalName + " — " + translations[`t--title-${SETTINGS.pagename}`]
		return await translations;
	});
	console.log(translation);
	translation.then(translations => {
		for (const translationKey in translations) {
			const translationValue = translations[translationKey];
			const applicableTo = document.querySelectorAll('.' + translationKey);
			applicableTo.forEach(node => {
				//Only if the node has no content or only whitespace:
				if (node.innerHTML.trim().length <= 0)
					node.innerHTML = translationValue;
			})
		}
	});
}