// language-list.js: JavaScript handling language list page logic.
window.addEventListener('DOMContentLoaded', () => {
	const params = window.location.search.length > 0 ? new URLSearchParams(window.location.search) : new URLSearchParams("?fields=name-en,isconlang&order=name asc&ipp=200");
	const rowTemplate = document.querySelector("template#language-row");
	const tbody = document.querySelector("table#language-list tbody");
	Promise.all([fetch(`/language/list?${params.toString()}`, { headers: new Headers({ "Accept": "application/json" }) }).then(res => res.json()),
		translationPromise
	]).then(async ([languages, translations]) => {
		const ids = languages.map(lang => lang.id).join(",");
		const statistics = (await fetch(`/statistics?word-count=${ids}`).then(res => res.json()))['word-count'];
		// HYPERFANCY DESTRUCTURING!
		for (const { id, name, "name-en": nameEn = "None", isconlang: isConlang } of languages) {
			const row = rowTemplate.content.cloneNode(true);
			row.id = `language-row-${id}`
			row.querySelector(".language-name").innerHTML = name;
			row.querySelector(".language-name").href = `/language/${id}`;

			row.querySelector(".language-name-en").innerHTML = nameEn;
			row.querySelector(".language-code").innerHTML = id;
			row.querySelector(".language-isconlang").innerHTML = translations[`t--${isConlang}`];
			row.querySelector(".language-word-count").innerHTML = statistics[id];

			const languageSelectButton = row.querySelector("input.language-select");
			languageSelectButton.dataset.langId = id;
			languageSelectButton.onclick = evt => {
				// state may be " "=not selected, "t"=selected as to, "f"=selected as from
				//cycle is empty -> to -> from -> to -> ...
				const curState = languageSelectButton.dataset.state;
				const curFrom = tbody.querySelector('input[data-state="f"].language-select'),
					curTo = tbody.querySelector('input[data-state="t"].language-select');
				switch (curState) {
					case " ":
						languageSelectButton.dataset.state = 't';
						if (curTo) curTo.dataset.state = 'f';
						if (curFrom) curFrom.dataset.state = ' ';
						break;
					// switch positions
					case 't':
						languageSelectButton.dataset.state = 'f';
						if (curFrom) curFrom.dataset.state = 't';
						break;
					case 'f':
						languageSelectButton.dataset.state = 't';
						if (curTo) curTo.dataset.state = 'f';
						break;
				}
				//adjust the displayed accordingly
				if (curFrom) adjustLanguageSelectBtnValue(curFrom);
				if (curTo) adjustLanguageSelectBtnValue(curTo);
				adjustLanguageSelectBtnValue(languageSelectButton);

				// store the language selection
				try {
					window.localStorage.languageFrom = tbody.querySelector('input[data-state="f"].language-select').dataset.langId;
				} catch (e) { }
				try {
					window.localStorage.languageTo = tbody.querySelector('input[data-state="t"].language-select').dataset.langId;
				} catch (e) { }
			};

			tbody.appendChild(row);
		}
		// set the initially selected languages
		const langFromBtn = tbody.querySelector(`input[data-lang-id="${window.localStorage.languageFrom}"].language-select`);
		const langToBtn = tbody.querySelector(`input[data-lang-id="${window.localStorage.languageTo}"].language-select`);
		langFromBtn.dataset.state = 'f';
		langToBtn.dataset.state = 't';

		//set all graphical initial values
		tbody.querySelectorAll('input[type="button"].language-select').forEach(adjustLanguageSelectBtnValue);

	}).catch(console.error);

	document.querySelector('button#open-language-creator')?.addEventListener('click', async event => {
		const langToCreate = document.querySelector('input#new-language-name').value;
		const newpath = (window.location + '').replace(window.location.pathname +'?' + window.location.search, `/language/${langToCreate}?action=create`);
		console.log(newpath);
		window.location.assign(newpath)
	});
});

function adjustLanguageSelectBtnValue(button) {
	button.value = button.dataset.state == 't' ? '→'
		: button.dataset.state == 'f' ? '←'
			: ' ';
}