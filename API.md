# API documentation

The APIs are all server endpoints that communicate in a RESTful way and use mostly JSON as a format. This document serves as a reference for frontend developers, because through this API, the server is theoretically independend of web technologies such as HTML, CSS and JavaScript and frontends may be written in any language and for any device.

The server mostly ignores HTTP headers and methods, unless it is absolutely necessary. The server will also rarely complain about problematic or incorrect requests and their contents, but the client must expect nonsentical or incomplete responses in such a case.

Cookies are never used. The Web frontend makes use of `window.localStorage` to store session-specific parameters which are of no interest to the server.

Part of the RESTful behavior is that all the information the server has about a client is the data sent with the HTTP request. There is no hidden server-side client data.

## Caching

Static files are allowed to be cached for 20 days, with the additional tag of "immutable" indicating to the cache that the resource will not change. For development purposes, it is best to keep the developer tab open and disable the cache. The static files include the translation endpoints (which are generated only depending on static files).

Computation-intense endpoints are short-cached, i.e. for a time between 10 seconds and a minute. This removes server load for often refreshes but still enables the resource to be updated regularly. These endpoints include the statistics endpoint.

HTML documents are currently not cached. In theory, the individual static page handlers can dynamically add content, although most of the "framework content", metadata and headers are statically fetched from files. The static page handlers are free to implement cache control and cache verification.

As the website appears the same to every user, public caching is enabled.

## Font handling

Many conlangs will not use any existing script and, therefore, mostly use characters defined in the Private Use Areas of Unicode as custom characters. As these are not printable by any existing font, the user may provide custom fonts where such characters have defined looks. The user can then place these fonts directly into the `res/font` folder, where the server will serve them under the `/font` URLs. The languages have the column `FontUrl` which specifies the language's font location relative to the `/font` folder.

## Static files

The files under the paths `/js`, `/img` and `/css` serve static JavaScript, Image and CSS files respectively, which can be found directly in the server's static resources folder (`/res/static/...`). One special case is the `/favicon.ico`, which is equivalent to `/img/favicon.png`. This shorthand is provided because most modern browsers automatically fetch the favicon from the standard path. The files under the path `/font` serve static font files.

## "Static" HTML pages

These pages are semi-static, in the way that their contents rarely change and the client has little influence on them at request time, but they are generated on-demand by the server from multiple HTML components.

All these pages have in common that they don't contain any text. All HTML elements that contain text are marked with a special translation class, which is a CSS class starting with `t--`. The included JavaScript `js/main.js` then requests an appropriate translation file from the server (see below) and replaces the content of these elements. The JavaScript will decide on the language to be used with two methods: first, the query parameter `lang` is considered, if it does not exist, the user agent's language is used. This also means that the query parameter `lang` has no influence on server behavior. Loading the pages `/language/list` and `/language/list?lang=zh` will result in the exact same response, but the JavaScript fetching the translation files will behave differently.

### `/`: Main page

This endpoint serves a front page that includes some basic statistics fetched from `/statistics`.

### `/language/list` with `Accept: text/html`: Language list

This endpoint serves a page which will fetch and display the language list provided on the exact same endpoint (see below) using its own query parameters.

### `/language/LANG` with `Accept: text/html`: Language detail view

This endpoint serves a page which displays the full detail information for the language with ID "LANG" with the option for the user to edit or delete the language.

If the `action=create` query parameter is set, no language data is fetched. An empty form is displayed that allows the user to create a new language.

All of the "form actions" on the page invoke the same path of the current page with different methods such as POST and DELETE (documented below). An exception is the language creation page variation, which determines the language identifier from user input.

## API

APIs generally communicate over JSON or query parameters in the case of GET requests.

### `/translation/xx_XX`: Translation lists

For the HTML pages to be filled with content, these translation endpoints provide JSON with simple string-string mappings. Each key specifies the class of the elements that it represents the contents of. The value specifies the literal HTML to be filled into the elements. Generally, HTML markup other than raw text is avoided. The help texts use `<br>` tags to create newlines and HTML escape sequences such as `&ldquo;` are commonly used. The major exception are the texts that have placeholders for computed values themselves. These are almost exclusively `<span>`s with special ids. The English translation can serve as a reference on where such placeholders are possible and what they represent.

The `xx_XX` portion of the translation path specifies language and optionally region in POSIX format, i.e. ISO language code and ISO region/country code. Examples are `en`, `en_US`, `de_CH` and `fr`. A two-letter code should be used where present, i.e. `en` for English should be used instead of `eng`. The server automatically fills out fallback translations in the following scheme:

- If no translation is found for the region and language combination, the translation for just the language is used.
- If no translation is found for the language, the `en.json` file is used, which is equivalent to the endpoint `/translation/en`.

If a certain region or language (or a combination thereof) is not found, the server treats that locale to have no translations whatsoever. The above scheme allows, for example, for the translation of only a couple of region-specific terms, whereas the general language translation file will handle most of the translations. Also, invalid or unsupported locales will in the worst case result in the English translation being returned.

### `/conscript/LANG`: Conscript provider for different languages

This path enables the use of custom font files with the ConlangDB system. This API is provided with the language ID "LANG" and returns a small CSS file defining an `@font-face` rule for the font family `"NATIVE-SCRIPT-FONT"`. Additionally, a class selector for `.native-script-font` is included which makes all elements with this class use the conscript. The url for the font face is generated from the `FontUrl` property of the language, which may point to a font file name without extension. The user can provide different formats for this conscript and all of them are included as alternatives in the source list of the CSS. Also, in the future, it may be possible to provide bold and italic font faces which may be referenced by the CSS using some standard naming conventions. For now, the CSS allows bold and italic font synthesis.

### `/XXX/list` with `Accept: application/json`: Lists for various data

These endpoints all also have an HTML file associated to them, which is retrieved by setting the `Accept` header to `text/html`. Those files contain JavaScript which fetches these endpoints. Although there are multiple different data lists with different properties, they all share the basic query parameters they take and the format of the JSON they return:

- `fields`: A comma-separated list of fields that are to be retrieved. These specify exactly what data the client is interested in. Note that almost never do the allowed field values correspond with actual field names in tables in the database (and often they do not correspond to any field, but subqueries or computations). Field names which are unrecognized are left out of the response silently. Some list endpoints may always return certain fields.
- `page` and `ipp`: These specify the section of the entire data list to return. The `ipp` parameter is short for "items per page" and specifies what size the "pages" or data sections should be. On websites common ones may be 20, 50 or 100. The `page` is a zero-based index into this array of sections. For example, if a request to `/foo/list?ipp=20&page=0` is made (assuming a data collection with the name foo) is made, items 0 to 19 are returned. `/foo/list?ipp=20&page=3` represents items 60 to 79 and `/foo/list?ipp=100&page=1` represents items 100 to 199. In SQL terms, the two parameters translate to `LIMIT ipp OFFSET (page * ipp)`. If either parameter is not given, all of the data is returned. This may result in large responses and processing times.
- `order`: This specifies in which order the results should be delivered. The default may be an identity column or something specific to the list endpoint. The format of this parameter is a field name followed by a space followed by either `asc` or `desc`, for ascending order (natural order) or descending order (reverse order). How the ordering occurs depends on the field that is ordered by. Special field names may also be allowed to specify a data-specific, more complicated sorting order.

The format of the JSON is a JSON array in correct order. Each element is a JSON object with the field names as keys and the field values as values. As specified, some fields may always be included in these objects. All objects have the exact same keys. The datatypes of the values may vary.

Some endpoints with the data list format have slightly different URLs. All valid URLs are given below.

#### `/language/list`: Languages

Allowed `fields` values:
- `name`: Name of the language in native script or romanized form. Romanized is recommended. This is always included.
- `name-en`: Name of the language in English. Most conlangs will have the native language name in latin here.
- `id`: Language identifier. This is either the ISO-639 language code for real languages or a "private language code" as defined by ISO-639 in the range `qaa-qtz`. It is recommended that the last two digits represent a reasonable abbreviation of the romanized native language name, if possible. This is always included.
- `description`: Description of the language in the language itself, again, either romanized or native.
- `description-en`: Description of the language in English. The user may choose to put the descriptions in his own language here, but this field is generally used for descriptions in a real language that database users are expected to be able to read.
- `isconlang`: Whether the language is a constructed language. This is computed at language creation time and checks whether the language code ("id") is in a comprehensive ISO language code list fetched from <https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3.tab>
- `fonturl`: URL to a font file for the language on the server under the main resource path `/font`.

#### `/word/list`: Words

This is one of the main APIs of the entire system. The word list API will return a list of words with their translations in a certain translation language. Essentially, this provides one half of a dictionary, with the other half obtained with source and target language swapped.

The query parameter `from` represents the source language ID. All words existent in the source language are included, whereas many words of the reference language never appear anywhere. The special query parameter `to` defines the language key of the target language, the language that translations are shown for. The name stems from the fact that in effect, the main language is translated into this target language (see also the from-to naming scheme in the client). If this query parameter is omitted, no translations are included in the result. This is recommended for simple word list retrievals, as including translations requires a much more involved database query with longer processing time. The translations are included in the result objects as an array named `translations`; this only contains the string names of each translating word. To obtain the full word information on one or more words, use the single word access API `/word/LANG/TEXT`

Allowed `fields` values:

- `text`: Actual text of the word in native script (this may not be printable!). As word texts need to be unique, the APIs cannot access the database's word IDs.
- `romanized`: Romanized version of the word. This text should only contain Latin characters (diacritics included, which will be a necessity for many romanizations) and basic (preferrably ASCII) punctuation. It is recommended to use a consistent romanization, especially if there are multiple different ways to romanize words.

### `/language/LANG`: Language access

This API allows for retrieving (GET), manipulating or creating (POST) and deleting (DELETE) single languages. The language is identified with the ID "LANG". 404 is used for all nonexistent languages that are attempted to be GET'd or DELETE'd.

#### GET with `Accept: application/json`

Retrieves all data for a single language as a JSON object. (key list to be inserted here)

#### POST

Change or create a language's data. The request body is a JSON object that indicates all the language data to be changed. The keys are identical with the ones returned by GET. The only special key that can be used is the "id" key, which will change the language's ID to the new given ID.

The answer by the server is a 204 for a language change and 201 (Created) for language creation. The Location header is set to the language's path, which may be equal to the path that the POST request was made on (and will always be for newly created languages). It will be changed if through an `id` key in the request body, the language's ID was changed and it is therefore now available on a new, different path. The client can therefore simply GET the Location URL.

#### DELETE

Delete a language. The answer is 200 and the answer body contains all the language information as with a GET request

### `/word/LANG/TEXT`: Single word access

This API allows for retrieving (GET), manipulating or creating (POST) and deleting (DELETE) single words. LANG is the language id and TEXT is the romanized form of the word. (The database restrictions ensure that any language ID - romanized text - combination is unique)

#### GET with `Accept: application/json`

Retrieves all data for a single word as a JSON object. The form of the object is as follows:

- `text`: Native text of the word, may contain complicated or non-printable Unicode characters. This key is absent if for the word no native text is defined.
- `romanized`: Romanized form of the word's text, will always have a value.
- `translations`: An array of simplified translation word objects. The keys are `text` for the native text, `romanized` and `description` for the translation description. For this purpose, the `translate` query parameter can specify the translation language that should be used for looking up the translation data. May be empty if either no translations exist or no translation language was given.
- `definitions`: An array of definition strings. May be empty.
- `attributes`: An array of attribute objects. These have the same form as the attribute objects of the attribute endpoints. May be empty.

#### POST

Modifies base word data, cannot, however, modify translation, definition or attribute data. The specific APIs used for this purpose are listed below.

Except for the array keys, all other data sent with the GET can be modified by sending a JSON object with the exact same form. Non-specified data is not modified. The result will usually be `204`. If the specified word does not exist, it is created. The romanized form of the word and the language code are taken from the request path and all other required parameters need to be specified. In this case, the answer will be `201`. The Location header will contain the path to the word, which may be different from the request path if the romanized form of the word was changed. `400` errors most commonly occur on database constraint violations, such as changing the word's text or romanized form to something that is already defined for this language.

The language ID may be modified with the key `language`. This is uncommon, but possible, and the Location header reflects the new language. Note that changing the language ID will very likely change the meaning that translation relations to other words have, with some translations becoming synonyms and vice-versa. (This is because only the APIs treat translations and synonyms differently, but to the database, they are the same thing.)

#### DELETE

Deletes a word. The answer is `204` if successful.

#### `/word/LANG/TEXT/ACTION`: Word modification sub-APIs

These APIs modify certain parts of a word. The ACTION is one of the paths listed below. Note that these APIs cannot be GET, and trying to do so will result in `405` (Method Unallowed).

##### `definition`: Add and remove definitions for this word

A POST on this path will create a new definition, with the definition text being the **exact Request body**. This is different from most JSON-based APIs, but possible here because definitions only have their text data. A definition can be deleted by DELETE-ing and supplying the **entire definition text in the Request body**. Successful deletes will return `204`.

##### `translation/LANG2/WORD2`: Add and remove translation data

This endpoint can also be POST-ed and DELETE-d. Both requests need to be done to a subpath of the form `LANG2/WORD2`; in total, the request path will be something like `/word/LANG/TEXT/translation/LANG2/WORD2`. This path will add or delete translations to the word WORD2 (romanized form, as in WORD itself) in the language LANG2.

While POST-ing: If the word does not exist in the other language yet, it will be created in the process, but without setting its native text. If the translation does already exist, it remains untouched. Specifying a body will set the translation description of the translation **to the entire Request body**, if the body is empty, no modification to the translation description is done. As above, this diverges from usual JSON format, but is perfectly fine for this linear endpoint. The status code informs about what action was actually executed: `304` indicates that the translation was already present and no modification of the translation description was attempted. `204` indicates that the translation was created, and the other word already existed, so no new word data was created. `201` indicates that the other word was newly created and can itself be found under the path specified by the Location header.

While DELETE-ing: Note that the other word is never deleted, but only the translation association is removed. The normal status is `204` for successfully deleted translations.

##### `attribute/ATTR`: Add and remove attribute data

The ATTR part of the path is the symbol of the attribute that should be added (POST) or DELETE-d. Note that attribute symbols are unique for a given language. The status is `204` when adding or removing the attribute was successful and `304` if on POST the attribute already existed for the given word. Note that this API does not create attributes, this needs to be done beforehand with the attribute APIs.
