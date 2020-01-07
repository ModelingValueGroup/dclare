# dclare
the declarative rule engine

## focus points
- fully declarative
- rules are enforced continuously
- no need to write listeners
- impossible to specify concurrency while the implementation is heavily multi-threaded

## maven dependencies
To get all the dependencies in your ```lib``` folder: use the following commands:
````bash
mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib
mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib -Dclassifier=javadoc
mvn dependency:copy-dependencies -Dmdep.stripVersion=true -DoutputDirectory=lib -Dclassifier=sources
````
