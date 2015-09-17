java-renamer
============

This tool replaces strings inside java class files.

Why?
====

Because you can not use reserved keywords as field or method names in the Java
programming language, but the JVM would support that.

Usage
=====

```
java Renamer in.class out.class replacements.tsv
```

* `in.class`: input class file
* `out.class`: output class file with renamed methods/fields/classes/strings
* `replacements.tsv`: contains all strings that should be replaced, one per line.
