# OpenAPI3

```clojure
(require '[spec-tools.openapi3.core :as openapi])
```

An utility to transform Specs to OpenApi3 Schemas.

## Spec transformations ##

`openapi/transform` converts specs into OpenAPI3 Schema. The most of the
features are similar to swagger2 spec transformation with some extra
functionality.

```clojure
;; OpenAPI3 support oneOf and null type
(openapi/transform (s/nilable string?))
;;=> {:oneOf [{:type "string"} {:type "null"}]}

;; OpenAPI3 support anyOf
(openapi/transform (s/cat :string string? :int integer?))
;;=> {:type "array", :items {:anyOf [{:type "integer"} {:type "string"}]}}
```

## OpenAPI3 Spec generation ##

`openapi/openapi3-spec` function takes an extended OpenAPI3 spec as map and
transforms it into a valid [OpenAPI Object](https://swagger.io/specification/#openapi-object). Rules:
  * by default, data is passed through, allowing any valid OpenAPI3 data to be
    used
  * for qualified map keys, `openapi/expand` multimethod is invoked with the
    key, value and the map as arguments
    * dispatches on the key
    * returns a map that gets merged into original map, without the dispatched
      key

Predefined dispatch keys below.

### `::openapi3/parameters` ###

TODO

### `::openapi3/schemas` ###

TODO

### `::openapi3/content` ###

TODO

### `::openapi3/headers` ###

TODO

### Full example ###

TODO
