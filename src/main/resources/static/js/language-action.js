// language-action.js: Language edit, delete, create logic

// load language data and display
(async () => {
	const isCreator = new URLSearchParams(window.location.search).get('action') == 'create';
	if (!isCreator) {
		const promise = fetch(window.location.pathname, { headers: new Headers({ 'Accept': 'application/json' }) })
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

			const titleText = document.querySelector('#title').innerHTML;
			document.querySelector('#title').innerHTML = titleText + ' ' + languageName;

			document.querySelectorAll('.language-name').forEach(elt => elt.innerHTML = languageName);
			const form = document.querySelector('#language-edit-container');
			const $ = a => form.querySelector(a);
			// set all input fields
			$('#language-name-edit').value = languageName
			$('#language-name-en-edit').value = langNameEn
			$('#language-id-edit').value = langID
			$('#language-description-edit').value = langDescription
			$('#language-description-en-edit').value = langDescriptionEn
			$('#language-fonturl-edit').value = fonturl
			translationPromise.then(translations => $('#language-isconlang').innerHTML = translations[`t--${isconlang}`]);

			// make the 'only for editing' elements invisible
			document.querySelectorAll('#language-edit-container *.only-edit').forEach(elt => elt.classList.add('hidden'));
			// disable all input elements
			document.querySelectorAll('#language-edit-container input, #language-edit-container textarea').forEach(elt => elt.readOnly = true);
		});
	}
})();

// execute a language modification.
// mode specifies the name of the associated elements, either edit or create
async function executeLanguageModification(mode) {
	const form = document.querySelector(`#language-${mode == 'save' ? 'edit' : mode}-container`);
	const $ = a => form.querySelector(a);
	const langData = {};

	langData['name'] = $('#language-name-edit')?.value;
	langData['name-en'] = $('#language-name-en-edit')?.value;
	langData['id'] = $('#language-id-edit')?.value;
	langData['description'] = $('#language-description-edit')?.value;
	langData['description-en'] = $('#language-description-en-edit')?.value;
	langData['fonturl'] = $('#language-fonturl-edit')?.value;

	console.log(langData);
	const translations = await translationPromise;

	const newLocationP = fetch(window.location.pathname, { method: 'post', body: JSON.stringify(langData) })
		.then(res => res.ok ? res.headers.get('Location') : Promise.reject(res.status))
		.catch(err => {
			if (err == 400) {
				alert(translations[`t--error-${mode}-malformed`]);
			} else {
				alert(translations[`t--error-${mode}`]);
			}
			console.error(err);
		});

	// change the design of the button and disable it
	document.querySelector(`button#button-${mode}`).classList.add('progress');
	document.querySelector(`button#button-${mode}`).disabled = true;
	document.querySelector(`button.t--button-${mode}`).innerHTML = translations[`t--button-${mode}-progress`];

	// may take very long
	const newLocation = await newLocationP;
	console.log(newLocation);
	// reset button design
	document.querySelector(`button#button-${mode}`).classList.remove('progress');
	document.querySelector(`button#button-${mode}`).disabled = false;
	document.querySelector(`button.t--button-${mode}`).innerHTML = translations[`t--button-${mode}`];
	return newLocation;
}
/**
 * Navigates to 
 */
Window.prototype.navigateOnChange = function navigateOnChange(newLocation) {
	if (newLocation && (this.location.pathname != newLocation)) {
		this.location.assign((`${this.location}`).replace(this.location.pathname, newLocation));
		return true;
	}
	return false;
}

window.addEventListener('DOMContentLoaded', () => {
	document.querySelector('button#button-edit')?.addEventListener('click', async evt => {
		// make the 'only for editing' elements visible or invisible (toggle)
		document.querySelectorAll('#language-edit-container *.only-edit').forEach(elt => elt.classList.toggle('hidden'));
		// enable all editable elements
		document.querySelectorAll('#language-edit-container input, #language-edit-container textarea').forEach(elt => elt.readOnly = !elt.readOnly);
		// change edit button's text
		const editmode = !document.querySelector('button#button-save').classList.toggle('hidden');
		const translations = await translationPromise;
		document.querySelector('button#button-edit').innerHTML = editmode ? translations['t--button-end-edit'] : translations['t--button-edit'];
		console.log(editmode ? 'Edit mode' : 'View mode');
	});
	document.querySelector('button#button-save')?.addEventListener('click', async event => {
		const newLocation = await executeLanguageModification('save');
		// simulate click on 'end edit', if necessary
		window.navigateOnChange(newLocation) || document.querySelector('button#button-edit').click();
	});
	document.querySelector('button#button-delete')?.addEventListener('click', async event => {
		// change the design of the button and disable it
		document.querySelector(`button#button-delete`).classList.add('progress');
		document.querySelector(`button#button-delete`).disabled = true;
		const translations = await translationPromise;
		document.querySelector(`button.t--button-delete`).innerHTML = translations[`t--button-delete-progress`];
		if (confirm(translations['t--confirm-language-delete']))
			fetch(window.location.pathname, { method: 'delete' })
				.then(res => res.ok ? window.location.pathname = '/language/list' : Promise.reject(res))
				.catch(err => { console.log(err); alert("Error occurred on deleting language."); });

		document.querySelector(`button#button-delete`).classList.remove('progress');
		document.querySelector(`button#button-delete`).disabled = false;
		document.querySelector(`button.t--button-delete`).innerHTML = translations[`t--button-delete`];
	});
	document.querySelector('button#button-create')?.addEventListener('click', async event => {
		const newLocation = await executeLanguageModification('create');
		window.navigateOnChange(newLocation) || (window.location.search = '');
	});
});