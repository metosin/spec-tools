(ns spec-tools.json-schema
  "Tools for converting specs into JSON Schemata."
  (:require [clojure.spec :as s]
            [spec-tools.visitor :as visitor :refer [visit]]))

(defn- only-entry? [key a-map] (= [key] (keys a-map)))

(defn- simplify-all-of [spec]
  (let [subspecs (->> (:allOf spec) (remove empty?))]
    (cond
      (empty? subspecs) (dissoc spec :allOf)
      (and (= (count subspecs) 1) (only-entry? :allOf spec)) (first subspecs)
      :else (assoc spec :allOf subspecs))))

(defn- unwrap
  "Unwrap [x] to x. Asserts that coll has exactly one element."
  [coll]
  {:pre [(= 1 (count coll))]}
  (first coll))

(defn- spec-dispatch [dispatch spec children] dispatch)
(defmulti accept-spec spec-dispatch :default ::default)

;; predicate list taken from https://github.com/clojure/clojure/blob/master/src/clj/clojure/spec/gen.clj

; any? (one-of [(return nil) (any-printable)])
; some? (such-that some? (any-printable))
; number? (one-of [(large-integer) (double)])

; integer? (large-integer)
(defmethod accept-spec 'clojure.core/integer? [_ _ _] {:type "integer"})

; int? (large-integer)
(defmethod accept-spec 'clojure.core/int? [_ _ _] {:type "integer"})

; pos-int? (large-integer* {:min 1})
; neg-int? (large-integer* {:max -1})
; nat-int? (large-integer* {:min 0})

; float? (double)
(defmethod accept-spec 'clojure.core/float? [_ _ _] {:type "number"})

; double? (double)
(defmethod accept-spec 'clojure.core/double? [_ _ _] {:type "number"})

; boolean? (boolean)
(defmethod accept-spec 'clojure.core/boolean? [_ _ _] {:type "boolean"})

; string? (string-alphanumeric)
(defmethod accept-spec 'clojure.core/string? [_ _ _] {:type "string"})

; ident? (one-of [(keyword-ns) (symbol-ns)])
; simple-ident? (one-of [(keyword) (symbol)])
; qualified-ident? (such-that qualified? (one-of [(keyword-ns) (symbol-ns)]))

; keyword? (keyword-ns)
(defmethod accept-spec 'clojure.core/keyword? [_ _ _] {:type "string"})

; simple-keyword? (keyword)
; qualified-keyword? (such-that qualified? (keyword-ns))
; symbol? (symbol-ns)
; simple-symbol? (symbol)
; qualified-symbol? (such-that qualified? (symbol-ns))
; uuid? (uuid)
; uri? (fmap #(java.net.URI/create (str "http://" % ".com")) (uuid))
; bigdec? (fmap #(BigDecimal/valueOf %)
;               (double* {:infinite? false :NaN? false}))
; inst? (fmap #(java.util.Date. %)
;             (large-integer))
; seqable? (one-of [(return nil)
;                   (list simple)
;                   (vector simple)
;                   (map simple simple)
;                   (set simple)
;                   (string-alphanumeric)])
; indexed? (vector simple)
; map? (map simple simple)
; vector? (vector simple)
; list? (list simple)
; seq? (list simple)
; char? (char)
; set? (set simple)

; nil? (return nil)
(defmethod accept-spec 'clojure.core/nil? [_ _ _] {:type "null"})

; false? (return false)
; true? (return true)
; zero? (return 0)
; rational? (one-of [(large-integer) (ratio)])
; coll? (one-of [(map simple simple)
;                (list simple)
;                (vector simple)
;                (set simple)])
; empty? (elements [nil '() [] {} #{}])
; associative? (one-of [(map simple simple) (vector simple)])
; sequential? (one-of [(list simple) (vector simple)])
; ratio? (such-that ratio? (ratio))
; bytes? (bytes)

(defmethod accept-spec 'clojure.core/pos? [_ _ _] {:minimum 0 :exclusiveMinimum true})
(defmethod accept-spec 'clojure.core/neg? [_ _ _] {:maximum 0 :exclusiveMaximum true})

(defmethod accept-spec ::visitor/set [dispatch spec children]
  {:enum children})

(defn- is-map-of?
  "Predicate to check if spec looks like an expansion of clojure.spec/map-of."
  [spec]
  (let [[_ inner-spec & {:as kwargs}] (s/form spec)
        pred (when (seq? inner-spec) (first inner-spec))]
    ;; (s/map-of key-spec value-spec) expands to
    ;; (s/every (s/tuple key-spec value-spec) :into {} ...)
    (and (= pred #?(:clj 'clojure.spec/tuple :cljs 'cljs.spec/tuple)) (= (get kwargs :into)) {})))

; keys
(defmethod accept-spec 'clojure.spec/keys [dispatch spec children]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)
        names (map name (concat req req-un opt opt-un))
        required (map name (concat req req-un))]
    {:type "object"
     :properties (zipmap names children)
     :required required}))

; or
(defmethod accept-spec 'clojure.spec/or [dispatch spec children]
  {:anyOf children})

; and
(defmethod accept-spec 'clojure.spec/and [dispatch spec children]
  (simplify-all-of {:allOf children}))

; merge

; every
(defmethod accept-spec 'clojure.spec/every [dispatch spec children]
  ;; Special case handling of s/map-of, which expands to s/every
  (if (is-map-of? spec)
    {:type "object" :additionalProperties (get-in (unwrap children) [:items 1])}
    {:type "array" :items (unwrap children)}))

; every-ks
; coll-of
; map-of

; *
(defmethod accept-spec 'clojure.spec/* [dispatch spec children]
  {:type "array" :items (unwrap children)})

; +
(defmethod accept-spec 'clojure.spec/+ [dispatch spec children]
  {:type "array" :items (unwrap children) :minItems 1})

; ?
; alt
; cat
; &

; tuple
(defmethod accept-spec 'clojure.spec/tuple [dispatch spec children]
  {:type "array" :items children :minItems (count children)})

; keys*

; nilable

(defmethod accept-spec 'clojure.spec/nilable [dispatch spec children]
  {:oneOf [(unwrap children) {:type "null"}]})

;; this is just a function in clojure.spec?
(defmethod accept-spec 'clojure.spec/int-in-range? [dispatch spec children]
  (let [[_ minimum maximum _] (visitor/strip-fn-if-needed spec)]
    {:minimum minimum :maximum maximum}))

(defmethod accept-spec ::default [dispatch spec children]
  {})

(defn to-json [spec] (visit spec accept-spec))
