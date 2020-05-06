// dictionary.js: Dictionary page JavaScript
window.addEventListener('DOMContentLoaded', async evt => {
	const page = parseInt(new URLSearchParams(window.location.search).get('page')) || 0;

	document.querySelectorAll('button.nextpage').forEach(elt => elt.addEventListener('click', evt => {
		const qry = new URLSearchParams(window.location.search);
		qry.set('page', page + 1);
		window.location.search = qry;
	}));
	document.querySelectorAll('button.prevpage').forEach(elt => elt.addEventListener('click', evt => {
		const qry = new URLSearchParams(window.location.search);
		qry.set('page', Math.abs(page - 1));
		window.location.search = qry;
	}));

	const selector = document.querySelector('select#page-size-select');
	selector.selectedIndex = parseInt(window.localStorage.pageSelectionIdx);
	selector.addEventListener('change', evt => {
		const ipp = selector.selectedOptions[0]?.value || "30";
		if (ipp == "0")
			delete window.localStorage.ipp;
		else
			window.localStorage.ipp = ipp;
		window.localStorage.pageSelectionIdx = selector.selectedIndex;
		window.location.reload();
	});

	const langFrom = window.localStorage.languageFrom, langTo = window.localStorage.languageTo;
	const words = await fetch(`/word/list?from=${langFrom}&to=${langTo}${window.localStorage.ipp ? `&page=${page}&ipp=${window.localStorage.ipp}` : ''}&fields=text,romanized,types,definitions`,
		{ headers: { 'Accept': 'application/json' } })
		.then(res => res.ok ? res.json() : Promise.reject(res.status))
		.catch(ecode => {
			if (ecode == 429)
				setTimeout(() => {
					window.location.reload();
				}, 3000);
		});
	const rowtemplate = document.querySelector('template#dictionary-row').content;
	const table = document.querySelector('#dictionary-container');
	for ({ text = '', romanized, translations, definitions, types } of words) {
		const row = rowtemplate.cloneNode(true);
		row.querySelector('.word').innerHTML = `<span class="native-script-font">${text}</span> (${romanized})`;
		row.querySelector('.word').id = romanized;
		row.querySelector('a.word').href = `/word/${langFrom}/${romanized}`;
		row.querySelector('.word-type').innerHTML = types.join(' ');
		// max. 7 translations to not clutter up the page too much
		row.querySelector('.translations').innerHTML = translations.slice(0, 7).join(', ');
		// show one definition, if present
		row.querySelector('.definition').innerHTML = definitions[0] || "";
		table.appendChild(row);
	}
});
