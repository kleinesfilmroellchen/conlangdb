// word-action.js: Single word page JavaScript

window.addEventListener('DOMContentLoaded', () => {
	document.querySelector('button#button-edit')?.addEventListener('click', async evt => {
		// toggle visibility on edit and view container
		document.querySelectorAll('div.word-data-container').forEach(elt => elt.classList.toggle('hidden'));
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
});