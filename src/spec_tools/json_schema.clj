(ns spec-tools.json-schema
  "Tools for converting specs into JSON Schemata."
  (:require [clojure.spec :as s]))

(defn- strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head 'clojure.core/fn))
      (nth form 2)
      form)))

(defn- spec-dispatch
  [spec]
  (cond
    (or (s/spec? spec) (s/regex? spec) (keyword? spec))
    (let [form (s/form spec)]
      (if (not= form :clojure.spec/unknown)
        (if (seq? form)
          (first form)
          (spec-dispatch form))
        spec))
    (set? spec) ::set
    (seq? spec) (first (strip-fn-if-needed spec))
    :else spec))

(defn- formize [spec] (if (seq? spec) spec (s/form spec)))

(defn- simplify-all-of [spec]
  (let [subspecs (->> (:allOf spec) (remove empty?))]
    (cond
      (empty? subspecs) (dissoc spec :allOf)
      (and (= (count subspecs) 1) (= [:allOf] (keys spec))) (first subspecs)
      :else (assoc spec :allOf subspecs))))

(defmulti to-json "Convert a spec into a JSON Schema." spec-dispatch :default ::default)

(defmethod to-json 'clojure.core/int? [spec] {:type "integer"})
(defmethod to-json 'clojure.core/integer? [spec] {:type "integer"})

(defmethod to-json 'clojure.core/float? [spec] {:type "number"})
(defmethod to-json 'clojure.core/double? [spec] {:type "number"})

(defmethod to-json 'clojure.core/pos? [spec] {:minimum 0 :exclusiveMinimum true})
(defmethod to-json 'clojure.core/neg? [spec] {:maximum 0 :exclusiveMaximum true})

(defmethod to-json 'clojure.core/string? [spec] {:type "string"})
(defmethod to-json 'clojure.core/keyword? [spec] {:type "string"})

(defmethod to-json 'clojure.core/boolean? [spec] {:type "boolean"})

(defmethod to-json 'clojure.core/nil? [spec] {:type "null"})

(defmethod to-json ::set [spec]
  {:enum (vec (if (keyword? spec) (s/form spec) spec))})

(defmethod to-json 'clojure.spec/every [spec]
  (let [[_ inner-spec & kwargs] (formize spec)
        pred (when (seq? inner-spec) (first inner-spec))]
    ;; Special case handling for (s/map-of). If someone actually wants to have a
    ;; list of tuples in their JSON, this will break.
    (if (and (= pred 'clojure.spec/tuple)
             (= (get (into {} (map vec (partition 2 kwargs))) :into)) {})
      {:type "object" :additionalProperties (get-in (to-json inner-spec) [:items 1])}
      {:type "array" :items (to-json inner-spec)})))

(defmethod to-json 'clojure.spec/tuple [spec]
  (let [[_ & inner-specs] (formize spec)]
    {:type "array" :items (mapv to-json inner-specs) :minItems (count inner-specs)}))

(defmethod to-json 'clojure.spec/* [spec]
  (let [[_ inner-spec] (s/form spec)]
    {:type "array" :items (to-json inner-spec)}))

(defmethod to-json 'clojure.spec/+ [spec]
  (let [[_ inner-spec] (s/form spec)]
    {:type "array" :items (to-json inner-spec) :minItems 1}))

(defmethod to-json 'clojure.spec/keys [spec]
  (let [[_ & {:keys [req req-un opt opt-un]}] (s/form spec)
        properties (into {} (map (juxt name to-json)
                                 (concat req req-un opt opt-un)))]
    {:type "object"
     :properties properties
     :required (map name (concat req req-un))
     :additionalProperties false}))

(defmethod to-json 'clojure.spec/or [spec]
  (let [[_ & {:as inner-spec-map}] (s/form spec)]
    {:anyOf (mapv to-json (vals inner-spec-map))}))

(defmethod to-json 'clojure.spec/and [spec]
  (let [[_ & inner-specs] (s/form spec)]
    (simplify-all-of {:allOf (mapv to-json inner-specs)})))

(defmethod to-json 'clojure.spec/nilable [spec]
  (let [[_ inner-spec] (s/form spec)]
    {:oneOf [(to-json inner-spec) {:type "null"}]}))

(defmethod to-json 'clojure.spec/int-in-range? [spec]
  (let [[_ minimum maximum _] (strip-fn-if-needed spec)]
    {:minimum minimum :maximum maximum}))

(defmethod to-json ::default [spec]
  (prn :UNKNOWN (spec-dispatch spec) spec)
  {})
