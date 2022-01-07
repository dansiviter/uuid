[![GitHub Workflow Status](https://img.shields.io/github/workflow/status/dansiviter/uuid/Build?style=flat-square)](https://github.com/dansiviter/uuid/actions/workflows/build.yaml) [![Known Vulnerabilities](https://snyk.io/test/github/dansiviter/uuid/badge.svg?style=flat-square)](https://snyk.io/test/github/dansiviter/uuid) [![Sonar Coverage](https://img.shields.io/sonar/coverage/dansiviter_uuid?server=https%3A%2F%2Fsonarcloud.io&style=flat-square)](https://sonarcloud.io/dashboard?id=dansiviter_uuid) ![Java 11+](https://img.shields.io/badge/-Java%2011%2B-informational?style=flat-square)

# UUID
A simple Java UUID library that, in addition to older types, implements the Type 6 [draft-02 Peabody specification](https://datatracker.ietf.org/doc/html/draft-peabody-dispatch-new-uuid-format). This UUID type, in addition to the existing properties of type 1, is lexically sortable which may make it suitable for scenarios such as a database primary key.


## Usage

```java
var factory = UuidGeneratorFactory.type6();
var uuid = factory.get();
```
