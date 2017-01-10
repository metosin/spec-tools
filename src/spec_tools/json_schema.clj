(ns spec-tools.json-schema
  "Tools for converting specs into JSON Schemata."
  (:require [clojure.spec :as s]
            [spec-tools.visitor :as visitor :refer [visit]]))

(defn- strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head 'clojure.core/fn))
      (nth form 2)
      form)))

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

(defmethod accept-spec 'clojure.core/int? [_ _ _] {:type "integer"})
(defmethod accept-spec 'clojure.core/integer? [_ _ _] {:type "integer"})

(defmethod accept-spec 'clojure.core/float? [_ _ _] {:type "number"})
(defmethod accept-spec 'clojure.core/double? [_ _ _] {:type "number"})

(defmethod accept-spec 'clojure.core/pos? [_ _ _] {:minimum 0 :exclusiveMinimum true})
(defmethod accept-spec 'clojure.core/neg? [_ _ _] {:maximum 0 :exclusiveMaximum true})

(defmethod accept-spec 'clojure.core/string? [_ _ _] {:type "string"})
(defmethod accept-spec 'clojure.core/keyword? [_ _ _] {:type "string"})

(defmethod accept-spec 'clojure.core/boolean? [_ _ _] {:type "boolean"})

(defmethod accept-spec 'clojure.core/nil? [_ _ _] {:type "null"})

(defmethod accept-spec ::visitor/set [dispatch spec children]
  {:enum children})

(defn- is-map-of?
  "Predicate to check if spec looks like an expansion of clojure.spec/map-of."
  [spec]
  (let [[_ inner-spec & {:as kwargs}] (s/form spec)
        pred (when (seq? inner-spec) (first inner-spec))]
    ;; (s/map-of key-spec value-spec) expands to
    ;; (s/every (s/tuple key-spec value-spec) :into {} ...)
    (and (= pred 'clojure.spec/tuple) (= (get kwargs :into)) {})))

(defmethod accept-spec 'clojure.spec/every [dispatch spec children]
  ;; Special case handling of s/map-of, which expands to s/every
  (if (is-map-of? spec)
    {:type "object" :additionalProperties (get-in (unwrap children) [:items 1])}
    {:type "array" :items (unwrap children)}))

(defmethod accept-spec 'clojure.spec/tuple [dispatch spec children]
  {:type "array" :items children :minItems (count children)})

(defmethod accept-spec 'clojure.spec/* [dispatch spec children]
  {:type "array" :items (unwrap children)})

(defmethod accept-spec 'clojure.spec/+ [dispatch spec children]
  {:type "array" :items (unwrap children) :minItems 1})

(defmethod accept-spec 'clojure.spec/keys [dispatch spec children]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)
        names (map name (concat req req-un opt opt-un))
        required (map name (concat req req-un))]
    {:type "object"
     :properties (zipmap names children)
     :required required}))

(defmethod accept-spec 'clojure.spec/or [dispatch spec children]
  {:anyOf children})

(defmethod accept-spec 'clojure.spec/and [dispatch spec children]
  (simplify-all-of {:allOf children}))

(defmethod accept-spec 'clojure.spec/nilable [dispatch spec children]
  {:oneOf [(unwrap children) {:type "null"}]})

(defmethod accept-spec 'clojure.spec/int-in-range? [dispatch spec children]
  (let [[_ minimum maximum _] (visitor/strip-fn-if-needed spec)]
    {:minimum minimum :maximum maximum}))

(defmethod accept-spec ::default [dispatch spec children]
  {})

(defn to-json [spec] (visit spec accept-spec))
