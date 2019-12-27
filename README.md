# dclare
the declarative rule engine

## focus points
- fully declarative
- rules are enforced continuously
- no need to write listeners
- impossible to specify concurrency while the implementation is heavily multi-threaded

## maven dependencies
To get all the dependencies in you .m2 repos use the following commands:
````bash
mvn dependency:resolve
mvn dependency:resolve -Dclassifier=javadoc
mvn dependency:resolve -Dclassifier=sources
````