# jfxproperties

This package provides a utility to build up `PropertyObject` values that store all of the `javafx`/java-bean properties
of a type and allow for direct and fast access to them. Additionally, the `PropertyObject` facilitates iteration across 
these properties in a "reflection-like" style. This library relies on 
[Google Guava's Reflection](https://github.com/google/guava/wiki/ReflectionExplained) facilities to perform reflective 
calls. Efforts have been made to make the API as ergonomic, fast and useful in usage as possible.