// main.js: Master JavaScript

// minimized simple mutex implementation
class Mutex { constructor() { this.b = !1, this.q = [] } synchronize(t) { const s = this; return new Promise(function (n, h) { s.q.push([t, n, h]), s.b || s.d() }) } d() { this.b = !0; const t = this.q.shift(); t ? this.e(t) : this.b = !1 } e(t) { const [s, n, h] = t, i = this; s().then(n, h).then(function () { i.d() }) } };


const pagename = document.querySelector('meta[name="pageid"]').content;

const translationPromise = getPageTranslation();

async function getPageTranslation() {
	const language = window.localStorage.userLanguage || navigator.language.replace("-", "_");
	console.info("Language", language);
	// The server adds all the English texts where the actual translation file has no text translated.
	return fetch(`/translation/${language}`).then(result => result.json()).catch(() => fetch(`/translation/en`).then(result => result.json()));
}

const getLanguageName = (() => {
	const languages = new Map();
	const langMx = new Mutex();
	return async langId => langMx.synchronize(async () => {
		if (languages.has(langId)) return languages.get(langId);
		const langData = await fetch(`/language/${langId}`, { headers: new Headers({ "Accept": "application/json" }) })
			.then(res => res.ok ? res.json() : Promise.reject())
			.catch(reason => { console.log(reason); return { name: 'Unknown' }; });
		languages.set(langId, langData.name);

		return langData.name;
	});
})();

window.addEventListener('DOMContentLoaded', () => {
	// set the help section visibility
	document.querySelector("#help-toggle").checked = window.localStorage.helpOpen === "false";

	const title = document.querySelector("#title");
	translationPromise.then(translations => {
		// Page title
		const generalName = translations['t--conlangdb'];
		title.innerHTML = generalName + " — " + translations[`t--title-${pagename}`];

		// Page description
		document.querySelector('meta[name="description"]').content = translations[`t--description-${pagename}`];

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
			langselectLi.appendChild(langselectContent);
			langselectLi.querySelector("a.language-from-name").href = `/language/${languageFrom}`;
			langselectLi.querySelector("a.language-to-name").href = `/language/${languageTo}`;
			getLanguageName(languageFrom).then(langname => document.querySelectorAll(".language-from-name").forEach(elt => elt.innerHTML = langname));
			getLanguageName(languageTo).then(langname => document.querySelectorAll(".language-to-name").forEach(elt => elt.innerHTML = langname));
		}

		// Help text
		document.querySelector("#helptext").innerHTML = translations[`t--helptext-${pagename}`];

		// Date and time footer
		document.querySelector("#timedate").innerHTML = (new Date()).toLocaleString(undefined, { year: "numeric", month: "numeric", day: "numeric", hour: "numeric", minute: "numeric", second: "numeric", timeZoneName: undefined })

		return translations;
	});

});

// Called when the help box is toggled by pressing the "Help" heading text.
// Stores the checked state into local storage.
function helpToggled() {
	const checkbox = document.querySelector("#help-toggle");
	window.localStorage.helpOpen = !checkbox.checked;
}

// Called when a statistics container requests the retrieval of statistics.
function requestStatistics() {
	const container = document.querySelector("#statistics-container");
	const curLangs = `${window.localStorage.languageTo},${window.localStorage.languageFrom}`
	translationPromise.then(async (t) => {
		const statistics = await fetch(`/statistics?language-count=all,constructed&word-count=all,${curLangs}&wordattribute-count=all,${curLangs}&definition-count=all,${curLangs}`).then(res => res.json());
		console.log(statistics);
		// add all four statistics paragraphs
		for (const tkey of ["t--statistic-languages", "t--statistic-words", "t--statistic-definitions", "t--statistic-wordattributes"]) {
			const statP = document.createElement("p");
			statP.innerHTML = t[tkey];
			container.appendChild(statP);
		}

		// replace placeholder span content
		container.querySelectorAll(".lang1").forEach(elt => getLanguageName(window.localStorage.languageFrom).then(langname => elt.innerHTML = langname));
		container.querySelectorAll(".lang2").forEach(elt => getLanguageName(window.localStorage.languageTo).then(langname => elt.innerHTML = langname));
		for (const countkey of ["language", "word", "definition", "wordattribute"].map(x => `${x}-count`)) {
			container.querySelectorAll(`#${countkey}`).forEach(elt => elt.innerHTML = statistics[`${countkey}`].all);
			container.querySelectorAll(`#${countkey}-lang1`).forEach(elt => elt.innerHTML = statistics[`${countkey}`][window.localStorage.languageFrom]);
			container.querySelectorAll(`#${countkey}-lang2`).forEach(elt => elt.innerHTML = statistics[`${countkey}`][window.localStorage.languageTo]);
		}
		container.querySelectorAll("#conlang-count").forEach(elt => elt.innerHTML = statistics["language-count"].constructed);

		return t;
	});
}
