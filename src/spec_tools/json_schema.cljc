(ns spec-tools.json-schema
  "Tools for converting specs into JSON Schemata."
  (:require [clojure.spec :as s]
            [spec-tools.visitor :as visitor :refer [visit]]
            [spec-tools.type :as type]
            [clojure.set :as set]))

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

(defn transform [spec] (visit spec accept-spec))

;;
;; predicate list taken from https://github.com/clojure/clojure/blob/master/src/clj/clojure/spec/gen.clj
;;

; any? (one-of [(return nil) (any-printable)])
(defmethod accept-spec 'clojure.core/any? [_ _ _] {})

; some? (such-that some? (any-printable))
(defmethod accept-spec 'clojure.core/some? [_ _ _] {})

; number? (one-of [(large-integer) (double)])
(defmethod accept-spec 'clojure.core/number? [_ _ _] {:type "number" :format "double"})

; integer? (large-integer)
(defmethod accept-spec 'clojure.core/integer? [_ _ _] {:type "integer"})

; int? (large-integer)
(defmethod accept-spec 'clojure.core/int? [_ _ _] {:type "integer" :format "int64"})

; pos-int? (large-integer* {:min 1})
(defmethod accept-spec 'clojure.core/pos-int? [_ _ _] {:type "integer", :format "int64", :minimum 1})

; neg-int? (large-integer* {:max -1})
(defmethod accept-spec 'clojure.core/neg-int? [_ _ _] {:type "integer", :format "int64", :maximum -1})

; nat-int? (large-integer* {:min 0})
(defmethod accept-spec 'clojure.core/nat-int? [_ _ _] {:type "integer", :format "int64" :minimum 0})

; float? (double)
(defmethod accept-spec 'clojure.core/float? [_ _ _] {:type "number"})

; double? (double)
(defmethod accept-spec 'clojure.core/double? [_ _ _] {:type "number"})

; boolean? (boolean)
(defmethod accept-spec 'clojure.core/boolean? [_ _ _] {:type "boolean"})

; string? (string-alphanumeric)
(defmethod accept-spec 'clojure.core/string? [_ _ _] {:type "string"})

; ident? (one-of [(keyword-ns) (symbol-ns)])
(defmethod accept-spec 'clojure.core/ident? [_ _ _] {:type "string"})

; simple-ident? (one-of [(keyword) (symbol)])
(defmethod accept-spec 'clojure.core/simple-ident? [_ _ _] {:type "string"})

; qualified-ident? (such-that qualified? (one-of [(keyword-ns) (symbol-ns)]))
(defmethod accept-spec 'clojure.core/qualified-ident? [_ _ _] {:type "string"})

; keyword? (keyword-ns)
(defmethod accept-spec 'clojure.core/keyword? [_ _ _] {:type "string"})

; simple-keyword? (keyword)
(defmethod accept-spec 'clojure.core/simple-keyword? [_ _ _] {:type "string"})

; qualified-keyword? (such-that qualified? (keyword-ns))
(defmethod accept-spec 'clojure.core/qualified-keyword? [_ _ _] {:type "string"})

; symbol? (symbol-ns)
(defmethod accept-spec 'clojure.core/symbol? [_ _ _] {:type "string"})

; simple-symbol? (symbol)
(defmethod accept-spec 'clojure.core/simple-symbol? [_ _ _] {:type "string"})

; qualified-symbol? (such-that qualified? (symbol-ns))
(defmethod accept-spec 'clojure.core/qualified-symbol? [_ _ _] {:type "string"})

; uuid? (uuid)
(defmethod accept-spec 'clojure.core/uuid? [_ _ _] {:type "string" :format "uuid"})

; uri? (fmap #(java.net.URI/create (str "http://" % ".com")) (uuid))
(defmethod accept-spec 'clojure.core/uri? [_ _ _] {:type "string" :format "uri"})

; bigdec? (fmap #(BigDecimal/valueOf %)
;               (double* {:infinite? false :NaN? false}))
(defmethod accept-spec 'clojure.core/bigdec? [_ _ _] {:type "number" :format "double"})


; inst? (fmap #(java.util.Date. %)
;             (large-integer))
(defmethod accept-spec 'clojure.core/bigdec? [_ _ _] {:type "number" :format "double"})

; seqable? (one-of [(return nil)
;                   (list simple)
;                   (vector simple)
;                   (map simple simple)
;                   (set simple)
;                   (string-alphanumeric)])
(defmethod accept-spec 'clojure.core/seqable? [_ _ _] {:type "array"})

; indexed? (vector simple)
(defmethod accept-spec 'clojure.core/map? [_ _ _] {:type "array"})

; map? (map simple simple)
(defmethod accept-spec 'clojure.core/map? [_ _ _] {:type "object"})

; vector? (vector simple)
(defmethod accept-spec 'clojure.core/vector? [_ _ _] {:type "array"})

; list? (list simple)
(defmethod accept-spec 'clojure.core/list? [_ _ _] {:type "array"})

; seq? (list simple)
(defmethod accept-spec 'clojure.core/seq? [_ _ _] {:type "array"})

; char? (char)
(defmethod accept-spec 'clojure.core/char? [_ _ _] {:type "string"})

; set? (set simple)
(defmethod accept-spec 'clojure.core/set? [_ _ _] {:type "array" :uniqueItems true})

; nil? (return nil)
(defmethod accept-spec 'clojure.core/nil? [_ _ _] {:type "null"})

; false? (return false)
(defmethod accept-spec 'clojure.core/false? [_ _ _] {:type "boolean"})

; true? (return true)
(defmethod accept-spec 'clojure.core/true? [_ _ _] {:type "boolean"})

; zero? (return 0)
(defmethod accept-spec 'clojure.core/zero? [_ _ _] {:type "integer"})

; rational? (one-of [(large-integer) (ratio)])
(defmethod accept-spec 'clojure.core/coll? [_ _ _] {:type "double"})

; coll? (one-of [(map simple simple)
;                (list simple)
;                (vector simple)
;                (set simple)])
(defmethod accept-spec 'clojure.core/coll? [_ _ _] {:type "object"})

; empty? (elements [nil '() [] {} #{}])
(defmethod accept-spec 'clojure.core/empty? [_ _ _] {:type "array" :maxItems 0 :minItems 0})

; associative? (one-of [(map simple simple) (vector simple)])
(defmethod accept-spec 'clojure.core/associative? [_ _ _] {:type "object"})

; sequential? (one-of [(list simple) (vector simple)])
(defmethod accept-spec 'clojure.core/sequential? [_ _ _] {:type "array"})

; ratio? (such-that ratio? (ratio))
(defmethod accept-spec 'clojure.core/ratio? [_ _ _] {:type "integer"})

; bytes? (bytes)
(defmethod accept-spec 'clojure.core/ratio? [_ _ _] {:type "string" :format "byte"})

(defmethod accept-spec 'clojure.core/pos? [_ _ _] {:minimum 0 :exclusiveMinimum true})
(defmethod accept-spec 'clojure.core/neg? [_ _ _] {:maximum 0 :exclusiveMaximum true})

(defmethod accept-spec ::visitor/set [dispatch spec children]
  {:enum children})

(defn- is-map-of?
  "Predicate to check if spec looks like an expansion of clojure.spec/map-of."
  [spec]
  (let [[_ inner-spec & {:as kwargs}] (visitor/extract-form spec)
        pred (when (seq? inner-spec) (first inner-spec))]
    ;; (s/map-of key-spec value-spec) expands to
    ;; (s/every (s/tuple key-spec value-spec) :into {} ...)
    (and (= pred #?(:clj 'clojure.spec/tuple :cljs 'cljs.spec/tuple)) (= (get kwargs :into)) {})))

(defmethod accept-spec 'clojure.spec/keys [dispatch spec children]
  (let [[_ & {:keys [req req-un opt opt-un]}] (visitor/extract-form spec)
        names (map name (concat req req-un opt opt-un))
        required (mapv name (concat req req-un))]
    {:type "object"
     :properties (zipmap names children)
     :required required}))

(defmethod accept-spec 'clojure.spec/or [dispatch spec children]
  {:anyOf children})

(defmethod accept-spec 'clojure.spec/and [dispatch spec children]
  (simplify-all-of {:allOf children}))

; merge

(defmethod accept-spec 'clojure.spec/every [dispatch spec children]
  (let [form (visitor/extract-form spec)
        type (type/resolve-type form)]
    ;; Special case handling of s/map-of, which expands to s/every
    (if (is-map-of? spec)
      {:type "object" :additionalProperties (get-in (unwrap children) [:items 1])}
      (case type
        :map {:type "object", :additionalProperties (unwrap children)}
        :set {:type "array", :uniqueItems true, :items (unwrap children)}
        :vector {:type "array", :items (unwrap children)}))))

(defmethod accept-spec 'clojure.spec/every-kv [dispatch spec children]
  {:type "object", :additionalProperties (second children)})

(defmethod accept-spec ::visitor/map-of [dispatch spec children]
  {:type "object", :additionalProperties (second children)})

(defmethod accept-spec ::visitor/set-of [dispatch spec children]
  {:type "array", :items (unwrap children), :uniqueItems true})

(defmethod accept-spec ::visitor/vector-of [dispatch spec children]
  {:type "array", :items (unwrap children)})

(defmethod accept-spec 'clojure.spec/* [dispatch spec children]
  {:type "array" :items (unwrap children)})

(defmethod accept-spec 'clojure.spec/+ [dispatch spec children]
  {:type "array" :items (unwrap children) :minItems 1})

(defmethod accept-spec 'clojure.spec/? [dispatch spec children]
  {:type "array" :items (unwrap children) :minItems 0})

(defmethod accept-spec 'clojure.spec/alt [dispatch spec children]
  {:anyOf children})

(defmethod accept-spec 'clojure.spec/cat [dispatch spec children]
  {:type "array"
   :minItems (count children)
   :maxItems (count children)
   :items {:anyOf children}})

; &

(defmethod accept-spec 'clojure.spec/tuple [dispatch spec children]
  {:type "array" :items children :minItems (count children)})

; keys*

(defmethod accept-spec 'clojure.spec/nilable [dispatch spec children]
  {:oneOf [(unwrap children) {:type "null"}]})

;; this is just a function in clojure.spec?
(defmethod accept-spec 'clojure.spec/int-in-range? [dispatch spec children]
  (let [[_ minimum maximum _] (visitor/strip-fn-if-needed spec)]
    {:minimum minimum :maximum maximum}))

(defmethod accept-spec ::visitor/spec [dispatch spec children]
  (let [spec (visitor/extract-spec spec)
        json-schema-meta (reduce-kv
                           (fn [acc k v]
                             (if (= "json-schema" (namespace k))
                               (assoc acc (keyword (name k)) v)
                               acc))
                           {}
                           (into {} spec))
        extra-info (-> spec
                       (select-keys [:name :description])
                       (set/rename-keys {:name :title}))]
    (merge (unwrap children) extra-info json-schema-meta)))

(defmethod accept-spec ::default [dispatch spec children]
  {})
