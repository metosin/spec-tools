# Data Specs

```clj
(require '[spec-tools.data-spec :as ds])
```

Data Specs offers an alternative, Schema-like data-driven syntax to define simple nested collection specs. Rules:

* Just data, no macros
* Can be transformed into vanilla specs with valid forms (via form inference)
* Supports nested Maps `{}`, Vectors `[]` and Sets `#{}`
  * Vectors and Sets are homogeneous, and must contains exactly one spec
* Maps have either a single spec key (homogeneous keys) or any number keyword keys.
  * With homogeneous keys, keys are also conformed
  * Map (keyword) keys
    * can be qualified or non-qualified (a qualified name will be generated for it)
    * are required by default
    * can be wrapped into `ds/opt` or `ds/req` for making them optional or required.
  * Map values
    * can be functions, specs, qualified spec names or nested collections.
* wrapping value into `ds/maybe` makes it `s/nilable`

**NOTE**: to avoid macros, current implementation uses the undocumented functional core of `clojure.spec.alpha`: `every-impl`, `tuple-impl`, `map-spec-impl`, `nilable-impl` and `or-spec-impl`. Support for [spec-alpha2](https://github.com/metosin/spec-tools/issues/169) should help to remove these.

**NOTE**: To use enums with data-specs, you need to wrap them: `(s/spec #{:S :M :L})`

```clj
(s/def ::age pos-int?)

;; a data-spec
(def person
  {::id integer?
   ::age ::age
   :boss boolean?
   (ds/req :name) string?
   (ds/opt :description) string?
   :languages #{keyword?}
   :aliases [(ds/or {:maps {:alias string?}
                     :strings string?})]
   :orders [{:id int?
             :description string?}]
   :address (ds/maybe
              {:street string?
               :zip string?})})

;; it's just data.
(def new-person
  (dissoc person ::id))
```
To turn a data-spec into a Spec, call `ds/spec` on it, providing either a options map or a qualified keyword describing the root spec name - used to generate unique names for sub-specs that will be registered. Valid options:

| Key              | Description
| -----------------|----------------
| `:spec`          | The wrapped data-spec.
| `:name`          | Qualified root spec name - used to generate unique names for sub-specs.
| `:keys-spec`     | Function to generate the keys-specs, default `ds/keys-specs`.
| `:keys-default`  | Function to wrap not-wrapped keys, e.g. `ds/opt` to make keys optional by default.

```clj
;; options-syntax
(def person-spec
  (ds/spec
    {:name ::person
     :spec person}))

;; legacy syntax
(def person-spec
  (ds/spec ::person person))

(def new-person-spec
  (ds/spec ::person new-person))
```

The following specs are now registered:

```clj
(keys (st/registry #"user.*"))
; (:user/id
;  :user/age
;  :user$person/boss
;  :user$person/name
;  :user$person/description
;  :user$person/languages
;  :user$person$aliases$maps/alias
;  :user$person/orders
;  :user$person$orders/description
;  :user$person$orders/id
;  :user$person/address
;  :user$person$address/street
;  :user$person$address/zip)
```

And now we have specs:

```clj
(s/valid?
  new-person-spec
  {::age 63
   :boss true
   :name "Liisa"
   :languages #{:clj :cljs}
   :aliases [{:alias "Lissu"} "Liisu"]
   :orders [{:id 1, :description "cola"}
            {:id 2, :description "kebab"}]
   :description "Liisa is a valid boss"
   :address {:street "Amurinkatu 2"
             :zip "33210"}})
; true
```

All generated specs are wrapped into Specs Records so transformations works out of the box:

```clj
(st/encode
  new-person-spec
  {::age "63"
   :boss "true"
   :name "Liisa"
   :languages ["clj" "cljs"]
   :aliases [{:alias "Lissu"} "Liisu"]
   :orders [{:id "1", :description "cola"}
            {:id "2", :description "kebab"}]
   :description "Liisa is a valid boss"
   :address nil}
  st/string-transformer)
; {::age 63
;  :boss true
;  :name "Liisa"
;  :aliases [{:alias "Lissu"} "Liisu"]
;  :languages #{:clj :cljs}
;  :orders [{:id 1, :description "cola"}
;           {:id 2, :description "kebab"}]
;  :description "Liisa is a valid boss"
;  :address nil}
```
