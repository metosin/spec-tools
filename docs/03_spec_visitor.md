# Spec Visitor

```clj
(require '[spec-tools.visitor :as visitor])
```

An utility to walk over and transform specs using the [Visitor-pattern](https://en.wikipedia.org/wiki/Visitor_pattern). Main entry point is the `spec-tools.visitor/visit` function, extendable via `spec-tools.visitor/visit-spec` multimethod. 

Both the [Spec to JSON Schema](04_json_schema.md) and [Spec to Swagger Schema](05_swagger.md) conversions are implemented using the visitor.

Example to collect all spec names & forms:

```clj
(require '[clojure.spec.alpha :as s])
(require '[spec-tools.visitor :as visitor])

(s/def ::id string?)
(s/def ::name string?)
(s/def ::street string?)
(s/def ::city #{:tre :hki})
(s/def ::address (s/keys :req-un [::street ::city]))
(s/def ::user (s/keys :req-un [::id ::name ::address]))

;; visitor to recursively collect all registered spec forms
(let [specs (atom {})]
  (visitor/visit
    ::user
    (fn [_ spec _ _]
      (if-let [s (s/get-spec spec)]
        (swap! specs assoc spec (s/form s))
        @specs))))
;#:user{:id clojure.core/string?,
;       :name clojure.core/string?,
;       :street clojure.core/string?,
;       :city #{:tre :hki},
;       :address (clojure.spec.alpha/keys :req-un [:user/street :user/city]),
;       :user (clojure.spec.alpha/keys :req-un [:user/id :user/name :user/address])}
```
