// language-action.js: Language edit, delete, create logic

// load language data and display
(async () => {
	const isCreator = new URLSearchParams(window.location.search).get("action") == 'create';
	if (!isCreator) {
		const promise = fetch(window.location.pathname, { headers: new Headers({ "Accept": "application/json" }) })
			.then(res => res.ok ? res.json() : Promise.reject('fetch failed'))
			.catch(err => {
				if (err == 'fetch failed') {
					// hide language edit objects
					document.querySelectorAll('main .language-edit').forEach(elt => elt.classList.add('hidden'));
					document.querySelector('#language-not-found-alert').classList.remove('hidden');
				}
				return {};
			});

		window.addEventListener('DOMContentLoaded', async () => {
			const { 'name': languageName, 'name-en': langNameEn, 'description': langDescription, 'description-en': langDescriptionEn, fonturl, 'id': langID, isconlang } = await promise;

			const titleText = document.querySelector("#title").innerHTML;
			document.querySelector("#title").innerHTML = titleText + " " + languageName;

			document.querySelectorAll(".language-name").forEach(elt => elt.innerHTML = languageName);
			const form = document.querySelector("#language-edit-container");
			const $ = a => form.querySelector(a);
			// set all input fields
			$("#language-name-edit").value = languageName
			$("#language-name-en-edit").value = langNameEn
			$("#language-id-edit").value = langID
			$("#language-description-edit").value = langDescription
			$("#language-description-en-edit").value = langDescriptionEn
			$("#language-fonturl-edit").value = fonturl
			translationPromise.then(translations => $("#language-isconlang").innerHTML = translations[`t--${isconlang}`]);

			// make the "only for editing" elements invisible
			document.querySelectorAll("#language-edit-container *.only-edit").forEach(elt => elt.classList.add('hidden'));
			// disable all input elements
			document.querySelectorAll("#language-edit-container input, #language-edit-container textarea").forEach(elt => elt.readOnly = true);
		});
	}
})();
window.addEventListener('DOMContentLoaded', () => {
	document.querySelector("button#button-edit").addEventListener('click', async evt => {
		// make the "only for editing" elements visible or invisible (toggle)
		document.querySelectorAll("#language-edit-container *.only-edit").forEach(elt => elt.classList.toggle('hidden'));
		// enable all editable elements
		document.querySelectorAll("#language-edit-container input, #language-edit-container textarea").forEach(elt => elt.readOnly = !elt.readOnly);
		// change edit button's text
		const editmode = !document.querySelector("button#button-save").classList.toggle('hidden');
		const translations = await translationPromise;
		document.querySelector("button#button-edit").innerHTML = editmode ? translations["t--button-end-edit"] : translations["t--button-edit"];
		console.log(editmode ? "Edit mode" : "View mode");
	});
	document.querySelector("button#button-save").addEventListener('click', async evt => {
		const form = document.querySelector("#language-edit-container");
		const $ = a => form.querySelector(a);
		const langData = {};

		langData["name"] = $("#language-name-edit").value;
		langData["name-en"] = $("#language-name-en-edit").value;
		langData["id"] = $("#language-id-edit").value;
		langData["description"] = $("#language-description-edit").value;
		langData["description-en"] = $("#language-description-en-edit").value;
		langData["fonturl"] = $("#language-fonturl-edit").value;

		console.log(langData);

		const newLocationP = fetch(window.location.pathname, { method: 'post', body: JSON.stringify(langData) })
			.then(res => res.ok ? res.headers.get("Location") : Promise.reject("post failed"))
			.catch(err => { alert("An error occurred on saving the edit."); console.error(err); });

		// change the design of the save button and disable it
		document.querySelector("button#button-save").classList.add('progress');
		document.querySelector("button#button-save").disabled = true;
		const translations = await translationPromise;
		document.querySelector("button#button-save").innerHTML = translations['t--button-save-progress'];

		// may take very long
		const newLocation = await newLocationP;
		console.log(newLocation);
		if (newLocation && (window.location.pathname != newLocation))
			window.location.assign((window.location + "").replace(window.location.pathname, newLocation));
		else {
			// close editing mode
			// re-enable save button
			document.querySelector("button#button-save").classList.remove('progress');
			document.querySelector("button#button-save").disabled = false;
			document.querySelector("button#button-save").innerHTML = translations['t--button-save'];
			// simulate click on "end edit"
			document.querySelector("button#button-edit").click();
		}
	});
});