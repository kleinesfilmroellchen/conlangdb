------------------- Database creation statements, from create-database.sql

-- Database nomenclature and standards:
-- 
-- Single tables that represent data start with T.
-- 
-- Tables that represent relations with dependencies on other tables (i.e.
-- DELETEs cascade to them) start with Rel.
-- 
-- View tables and temporary tables start with View.
-- 
-- Trigger start with Trig. Constraint triggers start with ConstraintTrig.
-- 
-- Routines start with the language name and use snake case / C nomenclature
-- (underscore separation)
-- 
-- Indices for single columns have the form IdxTableColumn.
-- 
-- Identifier columns are named ID in all tables.
-- 
-- ID columns that are foreign keys are named XID, where X is a single letter
-- associated with the referenced table, such as 'W' for the word table.
-- 
-- 'Short Text' columns, i.e. columns that contain names or descriptors, are
-- VARCHAR(30) i.e. no more than 30 characters.

CREATE TABLE if not exists TLanguage (
	ID varchar(3) not null primary key,
	Name varchar(30) not null unique,
	Name_En varchar(30) not null unique,
	Description text,
	Description_En text,
	IsConlang boolean generated always as (not py_is_real_language(ID)) stored,
	Config regconfig not null default 'simple',
	FontUrl varchar(40),
	CONSTRAINT ConlangHasDescription CHECK (
		case
			when IsConlang then (char_length(Description) > 0)
			else true
		end
	)
);
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.ID is 'ISO-639 language code for real languages, custom code in the range qaa-qtz for constructed languages.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.Name is 'Language name, in the language itself, romanized.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.Name_En is 'Language name, in English or primary database language.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.Description is 'Language description and information, in the language itself, romanized.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.Description_En is 'Language description and information, in English or primary database language.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.IsConlang is 'Whether the language is a constructed language, auto-generated.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.Config is 'Text search configuration for the language, most likely empty for conlangs.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TLanguage.FontUrl is 'Font file base name, to be used by the frontend for displaying the language''s native script.';
--JAVA-SEPARATOR-NEXT-CMD


CREATE TABLE if not exists TWord (
	ID bigserial not null primary key,
	LID varchar(3) not null,
	Native text,
	Romanized text not null,
	-- generated by trigger, see below
	TextSearch tsvector,
	UNIQUE (LID, Romanized),
	FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT
);
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWord.ID is 'Word ID number.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWord.LID is 'Language code.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWord.Native is 'Word text in native script, i.e. probably Unicode Private Use Area characters that are to be displayed with the language''s custom font, as specified by TLanguage.FontUrl.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWord.Romanized is 'Word text in romanized form, i.e. standardized accurate transcription into latin alphabet, accents and punctuation.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWord.TextSearch is 'Text search optimized for of native and romanized text, auto-generated.';
--JAVA-SEPARATOR-NEXT-CMD

-- Special PL/PgSQL function that creates the text search vector for a word,
-- using the collation defined by the word's language. This cannot be a simple
-- index with expression, because it requires language collation lookup with a
-- SELECT query, an unallowed expressiion in index expressions.
CREATE OR REPLACE FUNCTION pgsql_create_tword_tsvector() RETURNS Trigger AS $$
	BEGIN
	NEW.TextSearch := to_tsvector(
		(
			SELECT config
			FROM TLanguage
			WHERE TLanguage.ID = NEW.LID
		)::regconfig,
		NEW.Romanized || ' ' || NEW.Native
	);
	RETURN NEW;
	END;
$$ LANGUAGE plpgsql;
--JAVA-SEPARATOR-NEXT-CMD

-- 1st Trigger on update
DROP TRIGGER IF EXISTS TrigCreateSearchVectorForTWordUpdate On TWord;
--JAVA-SEPARATOR-NEXT-CMD
CREATE TRIGGER TrigCreateSearchVectorForTWordUpdate
	AFTER UPDATE OF Romanized, Native, LID
		ON TWord FOR EACH ROW
	WHEN (
		( OLD.Romanized is distinct from NEW.Romanized )
		or ( OLD.Native is distinct from NEW.Native )
	)
	EXECUTE FUNCTION pgsql_create_tword_tsvector();
--JAVA-SEPARATOR-NEXT-CMD

-- 2nd Trigger on insert (without change condition)
DROP TRIGGER IF EXISTS TrigCreateSearchVectorForTWordInsert On TWord;
--JAVA-SEPARATOR-NEXT-CMD
CREATE TRIGGER TrigCreateSearchVectorForTWordInsert
	AFTER INSERT ON TWord FOR EACH ROW
	EXECUTE FUNCTION pgsql_create_tword_tsvector();
--JAVA-SEPARATOR-NEXT-CMD

CREATE INDEX IF NOT EXISTS IdxTWordRomanized ON TWord (LID, Romanized);
--JAVA-SEPARATOR-NEXT-CMD

CREATE INDEX IF NOT EXISTS IdxTWordNative ON TWord (LID, Native);
--JAVA-SEPARATOR-NEXT-CMD

CREATE INDEX IF NOT EXISTS IdxTWordTS ON TWord USING GIN (TextSearch);
--JAVA-SEPARATOR-NEXT-CMD


CREATE TABLE IF NOT EXISTS TDefinition (
	ID bigserial not null primary key,
	WID bigint not null,
	Definition text not null,
	FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE
);
--JAVA-SEPARATOR-NEXT-CMD
comment on column TDefinition.ID is 'Definition ID.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TDefinition.WID is 'Word ID of the word that is defined by this definition.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TDefinition.Definition is 'Definition text, in English or primary database language.';
--JAVA-SEPARATOR-NEXT-CMD

CREATE INDEX IF NOT EXISTS IdxTDefinitionDefinition ON TDefinition (Definition);
--JAVA-SEPARATOR-NEXT-CMD


CREATE TABLE IF NOT EXISTS RelTranslation (
	WIDOne bigint not null,
	WIDTwo bigint not null,
	Description text,
	PRIMARY KEY (WIDOne, WIDTwo),
	CONSTRAINT NoSelfTranslation CHECK (WIDOne is distinct from WIDTwo),
	FOREIGN KEY (WIDOne) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (WIDTwo) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE
);
--JAVA-SEPARATOR-NEXT-CMD
comment on column RelTranslation.WIDOne is 'First word that the translation relates to.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column RelTranslation.WIDTwo is 'Second word that the translation relates to.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column RelTranslation.Description is 'Description and additional information for the translation, in the primary database language.';
--JAVA-SEPARATOR-NEXT-CMD

-- This trigger prevents insert and update when another translation with an equal but swapped word relation already exists.
CREATE OR REPLACE FUNCTION pgsql_no_identical_translation() RETURNS TRIGGER AS $$
	BEGIN
		PERFORM WIDOne from RelTranslation where WIDOne=NEW.WIDTwo or WIDTwo=NEW.WIDOne;
		IF FOUND THEN
			RETURN NULL;
		END IF;
		RETURN NEW;
	END;
$$ LANGUAGE plpgsql;
--JAVA-SEPARATOR-NEXT-CMD

DROP TRIGGER IF EXISTS NoIdenticalTranslationUpdate ON RelTranslation;
--JAVA-SEPARATOR-NEXT-CMD
CREATE CONSTRAINT TRIGGER NoIdenticalTranslationUpdate
	AFTER UPDATE ON RelTranslation
	DEFERRABLE
	FOR EACH ROW WHEN (
		( OLD.WIDOne is distinct from NEW.WIDOne )
		or ( OLD.WIDTwo is distinct from NEW.WIDTwo )
	)
	EXECUTE FUNCTION pgsql_no_identical_translation();
--JAVA-SEPARATOR-NEXT-CMD
DROP TRIGGER IF EXISTS NoIdenticalTranslationInsert ON RelTranslation;
--JAVA-SEPARATOR-NEXT-CMD
CREATE CONSTRAINT TRIGGER NoIdenticalTranslationInsert
	AFTER INSERT ON RelTranslation
	DEFERRABLE
	FOR EACH ROW
	EXECUTE FUNCTION pgsql_no_identical_translation();
--JAVA-SEPARATOR-NEXT-CMD

CREATE TABLE IF NOT EXISTS TWordAttribute (
	ID bigserial not null primary key,
	LID varchar(3) not null,
	Name varchar(30) not null,
	Description text,
	Symbol varchar(5),
	UNIQUE (LID, Symbol), UNIQUE (LID, Name),
	FOREIGN KEY (LID) References TLanguage (ID) ON UPDATE CASCADE ON DELETE RESTRICT
);
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWordAttribute.ID is 'Word attribute ID.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWordAttribute.LID is 'Language that this word attribute belongs to.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWordAttribute.Name is 'Formal attribute name, possibly in the language itself (romanized).';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWordAttribute.Description is 'Attribute description and information, in English or the primary database language.';
--JAVA-SEPARATOR-NEXT-CMD
comment on column TWordAttribute.Symbol is 'Abbreviated symbol as it will appear in dictionaries. A default is generated.';
--JAVA-SEPARATOR-NEXT-CMD

-- Trigger that acts as a default setter of the Symbol column
CREATE OR REPLACE FUNCTION pgsql_default_symbol_wordattribute() RETURNS Trigger AS $$
	BEGIN
		IF NEW IS NOT NULL THEN
			NEW.Symbol := lower(substring(NEW.Name from 0 for 3));
		END IF;
	RETURN NEW;
	END;
$$ LANGUAGE plpgsql;
--JAVA-SEPARATOR-NEXT-CMD

DROP TRIGGER IF EXISTS TrigSymbolDefaultTWordAttribute ON TWordAttribute;
--JAVA-SEPARATOR-NEXT-CMD
CREATE TRIGGER TrigSymbolDefaultTWordAttribute
	AFTER INSERT ON TWordAttribute FOR EACH ROW
	EXECUTE FUNCTION pgsql_default_symbol_wordattribute();
--JAVA-SEPARATOR-NEXT-CMD

CREATE INDEX IF NOT EXISTS IdxTWordAttributeSymbol ON TWordAttribute (Symbol) INCLUDE (Name);
--JAVA-SEPARATOR-NEXT-CMD


CREATE TABLE IF NOT EXISTS RelAttributeForWord (
	WID bigint not null,
	AID bigint not null,
	PRIMARY KEY (WID, AID),
	FOREIGN KEY (WID) References TWord (ID) ON UPDATE CASCADE ON DELETE CASCADE,
	FOREIGN KEY (AID) References TWordAttribute (ID) ON UPDATE CASCADE ON DELETE CASCADE
);
--JAVA-SEPARATOR-NEXT-CMD
