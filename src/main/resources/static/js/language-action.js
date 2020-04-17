// language-action.js: Language edit, delete, create logic

// load language data and display
(async () => {
	const isCreator = new URLSearchParams(window.location.search).get("action") == 'create';
	if (!isCreator) {
		const promises = Promise.all([
			getLanguageName(window.location.pathname.split("/").reverse()[0]),
			fetch(window.location.pathname, { headers: new Headers({ "Accept": "application/json" }) }).then(res => res.json())]);
		window.addEventListener('DOMContentLoaded', async () => {
			const [languageName, langData] = await promises;
			console.log(languageName, langData);
			document.querySelectorAll(".language-name").forEach(elt => elt.innerHTML = languageName);
			const form = document.querySelector("#language-edit-container");
			const $ = a => form.querySelector(a);
			// set all input fields
			$("#language-name-edit").value = langData["name"];
			$("#language-name-en-edit").value = langData["name-en"];
			$("#language-id-edit").value = langData["id"];
			$("#language-description-edit").innerHTML = langData["description"];
			$("#language-description-en-edit").innerHTML = langData["description-en"];
			$("#language-fonturl-edit").value = langData["fonturl"];
			translationPromise.then(translations => $("#language-isconlang").innerHTML = translations[`t--${langData["isconlang"]}`]);

			// make the "only for editing" elements invisible
			document.querySelectorAll("#language-edit-container *.only-edit").forEach(elt => elt.classList.add('hidden'));
			// disable all input elements
			document.querySelectorAll("#language-edit-container input, #language-edit-container textarea").forEach(elt => elt.readOnly = true);
		});
	}
})();
window.addEventListener('DOMContentLoaded', () => {
	document.querySelector("button#button-edit").addEventListener('click', evt => {
		console.log(evt);
		// make the "only for editing" elements visible or invisible (toggle)
		document.querySelectorAll("#language-edit-container *.only-edit").forEach(elt => elt.classList.toggle('hidden'));
		// enable all editable elements
		document.querySelectorAll("#language-edit-container input, #language-edit-container textarea").forEach(elt => elt.readOnly = !elt.readOnly);
		const editmode = !document.querySelector("button#button-save").classList.toggle('hidden');
		console.log(editmode ? "Edit mode" : "View mode");
	});
	document.querySelector("button#button-save").addEventListener('click', evt => {
		console.log(evt);

	});
});