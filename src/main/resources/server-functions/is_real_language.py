# (code VARCHAR(3)) RETURNS BOOLEAN
# is_real_language.py Server function file
# Returns whether the language is a real ISO language code by querying an
# online JSON file.
# Argument: 'code': str / VARCHAR(3)
# Returns:  bool / BOOLEAN
import requests
import csv
try:
	res = requests.get('https://iso639-3.sil.org/sites/iso639-3/files/downloads/iso-639-3.tab')
	if not res.ok:
		raise Exception()
	# return whether there is at least one language with identical alpha3/alpha2 code
	return len(list(filter(
			# Id is 3-character language code, Part1 is 2-character language code
			lambda x: code == x['Id'] or code == x['Part1'],
			# DictReader treats first line as header, the delimiter of tab files are tabs
			csv.DictReader(res.text.splitlines(), delimiter='\t')))
		) > 0 # check the result set length
except Exception as e:
	plpy.info(e)
	return False