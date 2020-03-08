# ConlangDB

The ConlangDB project is a Web application running on Java and an SQL Database for managing constructed language word and translation data.

## Why?

This project was created out of necessity. One of my main hobbies aside from programming is worldbuilding and conlanging, the process of inventing languages, commonly called constructed languages or conlangs. As I have now created one large conlang and several more that I plan on working on in the future, I have run into the issue of managing all the data associated with them. Of course, grammar, structure, phonology, writing system, cultural context and similar things can easily be kept in normal text documents and some basic tables. But words, phrases and their translation quickly need a very efficient way of searching and organzing, otherwise they become unwieldy to handle very soon.

I ran into those issues two times: First, my conlang's data was kept in an Excel sheet, which could cope with the data load for about two weeks. I switched to a single-file MS Access database, mostly because I needed more than one translation for each word and, therefore, a real database was necessary. However, Access became very limiting very quickly in terms of what was possible with layout and table relations. So, I decided to redo my database structure and create my own web-based frontend. If it works for me, that's all I need.

## Installation and Running

This project is based on Gradle and the Java Standard Library. The only external dependency is JUnit for testing.

After cloning, navigate into the project folder and run

```bash
./gradlew build
```

to build the project. This will compile everything, run the tests and build the project into the `out` folder. Jar build is being worked on but shouldn't be too difficult.

The project is run with

```bash
./gradlew runDirect [--args <server arguments>]
```

This starts the server in the same process, so killing it also kills the server. Use shell detachment to prevent that.

The .classpath and .project files are only there to allow VSCode, Eclipse and other IDEs to work with the project normally. They do not provide correct build information.

### PostgreSQL setup

All of this will later be handled by the gradle build process, but for now, you have to manually create a database `ConlangDB` on your PostgreSQL server with a user `conlang` and password `planlingvo` ("constructed language" in [Esperanto](https://en.wikipedia.org/wiki/Esperanto)) that has full access to (at least) this database. Also, make sure that the PostgreSQL server (postmaster service) is running on the same system as the Java server and uses the default PostgreSQL port.

Here is the basic SQL code for setting that up, assuming an account with CREATEROLE and CREATEDB permissions:

```sql
create user conlang encrypted password 'planlingvo';
alter role conlang noinherit;
create database conlangdb
      with owner conlang   
           encoding 'UTF8'
           template template0
           lc_collate 'C' lc_ctype 'C';
grant all privileges on database conlangdb to conlang;
```

### Arguments

(some of these not implemented yet)

- `-p <port>` Specify port on which to listen. Default is 80.

## Architecture

The main component of ConlangDB is the Java application, which represents a constantly-running Webserver that accepts HTTP requests. It provides both websites and accompanying files (JS, CSS, images and the like), which are half-static (most information is statically hosted but some information needs to be regenerated), as well as API endpoints that are used by simple JS scripts as well as HTML forms to access and modify the database.

The database could theoretically be anthing compliant with JDBC or ODBC, but PostgreSQL is used currently because of its speed and versatility. If the need arises, there will be the possiblity to subclass the database manager class (DatabaseManager.java) and make the server use different subclasses depending on the API.
