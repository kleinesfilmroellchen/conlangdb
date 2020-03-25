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

	// set the help section visibility
	document.querySelector("#help-toggle").checked = window.localStorage.helpOpen === "false";

	const title = document.querySelector("#title");
	translation.then(translations => {
		// Page title
		const generalName = translations['t--conlangdb'];
		title.innerHTML = generalName + " — " + translations[`t--title-${SETTINGS.pagename}`];

		// Translations
		for (const translationKey in translations) {
			const translationValue = translations[translationKey];
			const applicableTo = document.querySelectorAll('.' + translationKey);
			applicableTo.forEach(node => {
				//Only if the node has no content or only whitespace:
				if (node.innerHTML.trim().length <= 0)
					node.innerHTML = translationValue;
			})
		}

		// Current language information
		const languageFrom = window.localStorage.languageFrom, languageTo = window.localStorage.languageTo;
		console.log(languageFrom, "->", languageTo);
		const langselectLi = document.querySelector("li#language-selection");
		// either one of the languages is undefined, i.e. the user has not selected one yet
		if (!(languageFrom && languageTo)) {
			langselectLi.innerHTML = translations["t--no-lang-selected"];
		} else {
			const langselectContent = document.querySelector("#language-selection-template").content.cloneNode(true);
			// TODO: needs to access language table via API to find human-readable name of language.
			langselectContent.querySelector("#language-from").innerHTML = languageFrom;
			langselectContent.querySelector("#language-to").innerHTML = languageTo;
			console.log(langselectContent);
			langselectLi.appendChild(langselectContent);
		}

		// Help text
		document.querySelector("#helptext").innerHTML = translations[`t--helptext-${SETTINGS.pagename}`];

		// Date and time footer
		document.querySelector("#timedate").innerHTML = (new Date()).toLocaleString(undefined, { year: "numeric", month: "numeric", day: "numeric", hour: "numeric", minute: "numeric", second: "numeric", timeZoneName: undefined })

		return translations;
	});

}

// Called when the help box is toggled by pressing the "Help" heading text.
// Stores the checked state into local storage.
function helpToggled() {
	const checkbox = document.querySelector("#help-toggle");
	window.localStorage.helpOpen = !checkbox.checked;
}