(ns spec-tools.core
  (:refer-clojure :exclude [merge -name])
  #?(:cljs (:require-macros [spec-tools.core :refer [spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.parse :as parse]
            [spec-tools.form :as form]
            [clojure.set :as set]
            [spec-tools.transform :as stt]
            [clojure.spec.alpha :as s]
            #?@(:clj  [[clojure.spec.gen.alpha :as gen]
                       [clojure.edn]]
                :cljs [[goog.date.UtcDateTime]
                       [cljs.reader]
                       [cljs.spec.gen.alpha :as gen]]))
  (:import
    #?@(:clj
        [(clojure.lang AFn IFn Var)
         (java.io Writer)])))

;;
;; helpers
;;

(declare spec?)
(declare into-spec)
(declare create-spec)
(declare coerce)

(defn ^:skip-wiki registry
  ([]
   (s/registry))
  ([re]
   (->> (s/registry)
        (filter #(-> % first str (subs 1) (->> (re-matches re))))
        (into {}))))

(defn ^:skip-wiki get-spec
  "Finds recursively a spec implementation from the registry"
  [name]
  (if-let [spec (get (s/registry) name)]
    (if (keyword? spec)
      (get-spec spec)
      spec)))

(defn ^:skip-wiki coerce-spec
  "Returns a spec from a spec name or spec. Throwns exception
  if no spec was found."
  [name-or-spec]
  (or
    (and (spec? name-or-spec) name-or-spec)
    (get-spec name-or-spec)
    (throw
      (ex-info
        (str "can't coerce to spec: " name-or-spec)
        {:name-or-spec name-or-spec}))))

(defn ^:skip-wiki serialize
  "Writes specs into a string that can be read by the reader.
  TODO: Should optionally write the realated Registry entries."
  [spec]
  (pr-str (s/form spec)))

(defn ^:skip-wiki deserialize
  "Reads specs from a string.
  TODO: Should optionally read the realated Registry entries."
  [s]
  #?(:clj  (clojure.edn/read-string s)
     :cljs (cljs.reader/read-string s)))

;;
;; Transformers
;;

(def ^:dynamic ^:private *transformer* nil)
(def ^:dynamic ^:private *encode?* nil)

(defprotocol Coercion
  (-coerce [this value transformer options]))

(defprotocol Transformer
  (-name [this])
  (-options [this])
  (-encoder [this spec value])
  (-decoder [this spec value]))

(defn type-transformer
  "Returns a Transformer instance out of options map or Transformer instances.
  Available options:

  | Key                | Description
  |--------------------|-----------------
  | `:name`            | Name of the transformer
  | `:encoders`        | Map of type `type -> transform`
  | `:decoders`        | Map of type `type -> transform`
  | `:default-encoder` | Default `transform` for encoding
  | `:default-decoder` | Default `transform` for decoding

  Example of a JSON type-transformer:

  ```clojure
  (require '[spec-tools.core :as st])
  (require '[spec-tools.transform :as stt])

  (def json-transformer
    (type-transformer
      {:name :json
       :decoders stt/json-type-decoders
       :encoders stt/json-type-encoders
       :default-encoder stt/any->any}))
  ```

  Composed Strict JSON Transformer:

  ```clojure
  (def strict-json-transformer
    (st/type-transformer
      st/json-transformer
      st/strip-extra-keys-transformer
      st/strip-extra-values-transformer))
  ```"
  [& options-or-transformers]
  (let [->opts #(if (satisfies? Transformer %) (-options %) %)
        {transformer-name :name :keys [encoders decoders default-encoder default-decoder] :as options}
        (reduce impl/deep-merge nil (map ->opts options-or-transformers))]
    (let [encode-key (some->> transformer-name name (str "encode/") keyword)
          decode-key (some->> transformer-name name (str "decode/") keyword)]
      (reify
        Transformer
        (-name [_] transformer-name)
        (-options [_] options)
        (-encoder [_ spec _]
          (or (get spec encode-key)
              (get encoders (parse/type-dispatch-value (:type spec)))
              default-encoder))
        (-decoder [_ spec _]
          (or (get spec decode-key)
              (get decoders (parse/type-dispatch-value (:type spec)))
              default-decoder))))))

(def json-transformer
  "Transformer that transforms data between JSON and EDN."
  (type-transformer
    {:name :json
     :decoders stt/json-type-decoders
     :encoders stt/json-type-encoders
     :default-encoder stt/any->any}))

(def string-transformer
  "Transformer that transforms data between Strings and EDN."
  (type-transformer
    {:name :string
     :decoders stt/string-type-decoders
     :encoders stt/string-type-encoders
     :default-encoder stt/any->any}))

(def strip-extra-keys-transformer
  "Transformer that drop extra keys from `s/keys` specs."
  (type-transformer
    {:name ::strip-extra-keys
     :decoders stt/strip-extra-keys-type-decoders}))

(def strip-extra-values-transformer
  "Transformer that drop extra values from `s/tuple` specs."
  (type-transformer
    {:name ::strip-extra-values
     :decoders stt/strip-extra-values-type-decoders}))

(def fail-on-extra-keys-transformer
  "Transformer that fails on extra keys in `s/keys` specs."
  (type-transformer
    {:name ::fail-on-extra-keys
     :decoders stt/fail-on-extra-keys-type-decoders}))

;;
;; Transforming
;;

(defn explain
  "Like `clojure.core.alpha/explain` but supports transformers"
  ([spec value]
   (explain spec value nil))
  ([spec value transformer]
   (binding [*transformer* transformer, *encode?* false]
     (s/explain (into-spec spec) value))))

(defn explain-data
  "Like `clojure.core.alpha/explain-data` but supports transformers"
  ([spec value]
   (explain-data spec value nil))
  ([spec value transformer]
   (binding [*transformer* transformer, *encode?* false]
     (s/explain-data (into-spec spec) value))))

(defn conform
  "Given a spec and a value, returns the possibly destructured value
   or ::s/invalid"
  ([spec value]
   (conform spec value nil))
  ([spec value transformer]
   (binding [*transformer* transformer, *encode?* false]
     (s/conform (into-spec spec) value))))

(defn conform!
  "Given a spec and a value, returns the possibly destructured value
   or fails with ex-info with :type of ::conform. ex-data also contains
   :problems, :spec and :value. call s/unform on the result to get the
   actual conformed value."
  ([spec value]
   (conform! spec value nil))
  ([spec value transformer]
   (binding [*transformer* transformer, *encode?* false]
     (let [spec' (into-spec spec)
           conformed (s/conform spec' value)]
       (if-not (s/invalid? conformed)
         conformed
         (let [problems (s/explain-data spec' value)
               data {:type ::conform
                     :problems (#?(:clj  :clojure.spec.alpha/problems
                                   :cljs :cljs.spec.alpha/problems) problems)
                     :spec spec
                     :value value}]
           (throw (ex-info (str "Spec conform error: " data) data))))))))

(defn coerce
  "Coerces the value using a [[Transformer]]. Returns original value for
  those parts of the value that can't be trasformed."
  ([spec value transformer]
   (coerce spec value transformer nil))
  ([spec value transformer options]
   (-coerce (into-spec spec) value transformer options)))

(defn decode
  "Decodes a value using a [[Transformer]] from external format to a value
  defined by the spec. First, calls [[coerce]] and returns the value if it's
  valid - otherwise, calls [[conform]] & [[unform]]. Returns `::s/invalid`
  if the value can't be decoded to conform the spec."
  ([spec value]
   (decode spec value nil))
  ([spec value transformer]
   (let [spec (into-spec spec)
         coerced (coerce spec value transformer)]
     (if (s/valid? spec coerced)
       coerced
       (binding [*transformer* transformer, *encode?* false]
         (let [conformed (s/conform spec value)]
           (if (s/invalid? conformed)
             conformed
             (s/unform spec conformed))))))))

(defn encode
  "Transforms a value (using a [[Transformer]]) from external
  format into a value defined by the spec. On error, returns `::s/invalid`."
  [spec value transformer]
  (binding [*transformer* transformer, *encode?* true]
    (let [spec (into-spec spec)
          conformed (s/conform spec value)]
      (if (s/invalid? conformed)
        conformed
        (s/unform spec conformed)))))

(defn select-spec
  "Best effort to drop recursively all extra keys out of a keys spec value."
  [spec value]
  (coerce spec value strip-extra-keys-transformer))

;;
;; Walker, from Nekala
;;

(defmulti walk (fn [{:keys [type]} _ _ _] (parse/type-dispatch-value type)) :default ::default)

(defmethod walk ::default [spec value accept options]
  (if (and (spec? spec) (not (:skip? options)))
    (accept spec value (assoc options :skip? true))
    value))

(defmethod walk :or [{:keys [::parse/items]} value accept options]
  (reduce
    (fn [v item]
      (let [transformed (accept item v options)]
        (if (= transformed v) v (reduced transformed))))
    value items))

(defmethod walk :and [{:keys [::parse/items]} value accept options]
  (reduce
    (fn [v item]
      (let [transformed (accept item v options)]
        transformed))
    value items))

(defmethod walk :nilable [{:keys [::parse/item]} value accept options]
  (accept item value options))

(defmethod walk :vector [{:keys [::parse/item]} value accept options]
  (if (sequential? value)
    (let [f (if (seq? value) reverse identity)]
      (->> value (map (fn [v] (accept item v options))) (into (empty value)) f))
    value))

(defmethod walk :tuple [{:keys [::parse/items]} value accept options]
  (if (sequential? value)
    (into (empty value)
          (comp (map-indexed vector)
                (map (fn [[i v]]
                       (if (< i (count items))
                         (some-> (nth items i) (accept v options))
                         v))))
          value)
    value))

(defmethod walk :set [{:keys [::parse/item]} value accept options]
  (if (or (set? value) (sequential? value))
    (->> value (map (fn [v] (accept item v options))) (set))
    value))

(defmethod walk :map [{:keys [::parse/key->spec]} value accept options]
  (if (map? value)
    (reduce-kv
      (fn [acc k v]
        (let [spec (if (qualified-keyword? k) (s/get-spec k) (s/get-spec (get key->spec k)))
              value (if spec (accept spec v options) v)]
          (assoc acc k value)))
      value
      value)
    value))

(defmethod walk :map-of [{:keys [::parse/key ::parse/value]} data accept options]
  (if (map? data)
    (reduce-kv
      (fn [acc k v]
        (let [k' (accept key k options)
              v' (accept value v options)]
          (assoc acc k' v')))
      (empty data)
      data)
    data))

;;
;; Spec Record
;;

(defn- extra-spec-map [data]
  (->> (dissoc data :form :spec)
       (reduce
         (fn [acc [k v]]
           (if (= "spec-tools.parse" (namespace k)) acc (assoc acc k v)))
         {})))

(defn- fail-on-invoke [spec]
  (throw
    (ex-info
      (str
        "Can't invoke spec with a non-function predicate: " spec)
      {:spec spec})))

(defn- leaf? [spec]
  (:leaf? (into-spec spec)))

(defn- decompose-spec-type 
  "Dynamic conforming can't walk over composite specs like s/and & s/or.
  So, we'll use the first type. Examples:

     `[:and [:int :string]]` -> `:int`
     `[:or [:string :keyword]]` -> `:string`"
  [spec]
  (let [type (:type spec)]
    (if (sequential? type)
      (update spec :type (comp first second))
      spec)))

(defrecord Spec [spec form type]
  #?@(:clj [s/Specize
            (specize* [s] s)
            (specize* [s _] s)])

  Coercion
  (-coerce [this value transformer options]
    (let [specify (fn [x]
                    (cond
                      (keyword? x) (recur (s/get-spec x))
                      (spec? x) x
                      (s/spec? x) (create-spec {:spec x})
                      (map? x) (if (qualified-keyword? (:spec x))
                                 (recur (s/get-spec (:spec x)))
                                 (create-spec (update x :spec (fnil identity any?))))))
          transformed (if-let [transform (if (and transformer (not (:skip? options)))
                                           (-decoder transformer this value))]
                        (transform this value) value)]
      (walk this transformed #(coerce (specify %1) %2 transformer %3) options)))

  s/Spec
  (conform* [this x]
    (let [transformer *transformer*, encode? *encode?*]
      ;; if there is a transformer present
      (if-let [transform (if transformer ((if encode? -encoder -decoder) transformer (decompose-spec-type this) x))]
        ;; let's transform it
        (let [transformed (transform this x)]
          ;; short-circuit on ::s/invalid
          (or (and (s/invalid? transformed) transformed)
              ;; recur
              (let [conformed (s/conform spec transformed)]
                ;; it's ok if encode transforms leaf values into invalid values
                (or (and encode? (s/invalid? conformed) (leaf? this) transformed) conformed))))
        (s/conform spec x))))

  (unform* [_ x]
    (s/unform spec x))

  (explain* [this path via in x]
    (let [problems (if (or (s/spec? spec) (s/regex? spec))
                     ;; transformer might fail deliberately, while the vanilla
                     ;; conform would succeed - we'll short-circuit it here.
                     ;; https://dev.clojure.org/jira/browse/CLJ-2115 would help
                     (let [conformed (s/conform* this x)
                           [explain? val] (if (s/invalid? conformed)
                                            [(s/invalid? (conform this x)) x]
                                            [true (s/unform spec conformed)])]
                       (if explain?
                         (s/explain* (s/specize* spec) path via in val)
                         [{:path path
                           :pred form
                           :val val
                           :via via
                           :in in}]))
                     (if (s/invalid? (s/conform* this x))
                       [{:path path
                         :pred form
                         :val x
                         :via via
                         :in in}]))
          spec-reason (:reason this)
          with-reason (fn [problem]
                        (cond-> problem
                                spec-reason
                                (assoc :reason spec-reason)))]
      (if problems
        (map with-reason problems))))

  (gen* [this overrides path rmap]
    (if-let [gen (:gen this)]
      (gen)
      (or
        (gen/gen-for-pred spec)
        (s/gen* (or (s/spec? spec) (s/specize* spec)) overrides path rmap))))

  (with-gen* [this gfn]
    (assoc this :gen gfn))

  (describe* [this]
    (let [data (clojure.core/merge {:spec form} (extra-spec-map this))]
      `(spec-tools.core/spec ~data)))

  IFn
  #?(:clj  (invoke [this x] (if (ifn? spec) (spec x) (fail-on-invoke this)))
     :cljs (-invoke [this x] (if (ifn? spec) (spec x) (fail-on-invoke this)))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (clojure.core/merge
                      (select-keys t [:form])
                      (if (:type t) (select-keys t [:type]))
                      (extra-spec-map t))))))

(defn spec? [x]
  (if (instance? Spec x) x))

(defn spec-name
  "Returns a spec name. Like the private clojure.spec.alpha/spec-name"
  [spec]
  (cond
    (ident? spec) spec

    (s/regex? spec) (::s/name spec)

    (and (spec? spec) (:name spec)) (:name spec)

    #?(:clj  (instance? clojure.lang.IObj spec)
       :cljs (implements? IMeta spec))
    (-> (meta spec) ::s/name)

    :else nil))

(defn spec-description
  "Returns a spec description."
  [spec]
  (if (spec? spec) (:description spec)))

(defn create-spec
  "Creates a Spec intance from a map containing the following keys:

           :spec  the wrapped spec predicate (default to `any?`)
           :form  source code of the spec predicate, if :spec is a spec,
                  :form is read with `s/form` out of it. For non-spec
                  preds, spec-tools.form/resolve-form is called, if still
                  not available, spec-creation will fail.
           :type  optional type for the spec. if not set, will be auto-
                  resolved via spec-tools.parse/parse-spec (optional)
         :reason  reason to be added to problems with s/explain (optional)
            :gen  generator function for the spec (optional)
           :name  name of the spec (optional)
    :description  description of the spec (optional)
          :xx/yy  any qualified keys can be added (optional)"
  [{:keys [spec type form] :as m}]
  (when (qualified-keyword? spec)
    (assert (get-spec spec) (str " Unable to resolve spec: " spec)))
  (let [spec (or spec any?)
        spec (cond
               (qualified-keyword? spec) (get-spec spec)
               (symbol? spec) (form/resolve-form spec)
               :else spec)
        form (or (if (qualified-keyword? form)
                   (s/form form))
                 form
                 (let [form (s/form spec)]
                   (if-not (= form ::s/unknown) form))
                 (form/resolve-form spec)
                 ::s/unknown)
        info (parse/parse-spec form)
        type (if (contains? m :type) type (:type info))
        name (-> spec meta ::s/name)
        record (map->Spec
                 (clojure.core/merge m info {:spec spec :form form :type type :leaf? (parse/leaf-type? type)}))]
    (cond-> record name (with-meta {::s/name name}))))

#?(:clj
   (defmacro spec
     "Creates a Spec instance with one or two arguments:

     ;; using type inference
     (spec integer?)

     ;; with explicit type
     (spec integer? {:type :long})

     ;; map form
     (spec {:spec integer?, :type :long})

     calls `create-spec`, see it for details."
     ([pred-or-info]
      (let [[pred info] (impl/extract-pred-and-info pred-or-info)]
        `(spec ~pred ~info)))
     ([pred info]
      `(let [info# ~info
             form# '~(impl/resolve-form &env pred)]
         (assert (map? info#) (str "spec info should be a map, was: " info#))
         (create-spec
           (clojure.core/merge
             info#
             {:form form#
              :spec ~pred}))))))


(defn- into-spec [x]
  (cond
    (spec? x) x
    (keyword? x) (recur (s/get-spec x))
    :else (create-spec {:spec x})))

;;
;; merge
;;

(defn- map-spec-keys [spec]
  (let [spec (or (if (qualified-keyword? spec)
                   (s/form spec))
                 spec)
        info (parse/parse-spec spec)]
    (select-keys info [::parse/keys ::parse/keys-req ::parse/keys-opt])))

(defn ^:skip-wiki merge-impl [forms spec-form merge-spec]
  (let [form-keys (map map-spec-keys forms)
        spec (reify
               s/Spec
               (conform* [_ x]
                 (let [conformed-vals (map #(s/conform % x) forms)]
                   (if (some #{::s/invalid} conformed-vals)
                     ::s/invalid
                     (apply clojure.core/merge x (map #(select-keys %1 %2) conformed-vals (map ::parse/keys form-keys))))))
               (unform* [_ x]
                 (s/unform* merge-spec x))
               (explain* [_ path via in x]
                 (s/explain* merge-spec path via in x))
               (gen* [_ overrides path rmap]
                 (s/gen* merge-spec overrides path rmap)))]
    (create-spec
      (clojure.core/merge
        {:spec spec
         :form spec-form
         :type :map}
        (apply merge-with set/union form-keys)))))

#?(:clj
   (defmacro merge [& forms]
     `(let [merge-spec# (s/merge ~@forms)]
        (merge-impl ~(vec forms) '(spec-tools.core/merge ~@(map #(impl/resolve-form &env %) forms)) merge-spec#))))
