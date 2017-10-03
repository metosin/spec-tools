(ns spec-tools.core
  #?(:cljs (:require-macros [spec-tools.core :refer [spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.parse :as parse]
            [spec-tools.form :as form]
            [spec-tools.conform :as conform]
            [clojure.spec.alpha :as s]
    #?@(:clj  [
            [clojure.spec.gen.alpha :as gen]
            [clojure.edn]]
        :cljs [[goog.date.UtcDateTime]
               [cljs.reader]
               [clojure.test.check.generators]
               [cljs.spec.gen.alpha :as gen]]))
  (:import
    #?@(:clj
        [(clojure.lang AFn IFn Var)
         (java.io Writer)])))

;;
;; helpers
;;

(declare spec?)

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

(def +invalid+ '::s/invalid)
(def +problems+ #?(:clj :clojure.spec.alpha/problems, :cljs :cljs.spec.alpha/problems))

;;
;; Dynamic conforming
;;

(def ^:dynamic ^:private *conforming* nil)

(defn type-conforming [opts]
  (fn [spec]
    (let [type (:type spec)]
      (get opts type))))

(def json-conforming
  (type-conforming conform/json-type-conforming))

(def string-conforming
  (type-conforming conform/string-type-conforming))

(def strip-extra-keys-conforming
  (type-conforming conform/strip-extra-keys-type-conforming))

(def fail-on-extra-keys-conforming
  (type-conforming conform/fail-on-extra-keys-type-conforming))

(defn explain
  ([spec value]
   (explain spec value nil))
  ([spec value conforming]
   (binding [*conforming* conforming]
     (s/explain spec value))))

(defn explain-data
  ([spec value]
   (explain-data spec value nil))
  ([spec value conforming]
   (binding [*conforming* conforming]
     (s/explain-data spec value))))

(defn conform
  "Given a spec and a value, returns the possibly destructured value
   or ::s/invalid"
  ([spec value]
   (conform spec value nil))
  ([spec value conforming]
   (binding [*conforming* conforming]
     (s/conform spec value))))

(defn conform!
  "Given a spec and a value, returns the possibly destructured value
   or fails with ex-info with :type of ::conform. ex-data also contains
   :problems, :spec and :value. call s/unform on the result to get the
   actual conformed value."
  ([spec value]
   (conform! spec value nil))
  ([spec value conforming]
   (binding [*conforming* conforming]
     (let [conformed (s/conform spec value)]
       (if-not (= conformed +invalid+)
         conformed
         (let [problems (s/explain-data spec value)
               data {:type ::conform
                     :problems (+problems+ problems)
                     :spec spec
                     :value value}]
           (throw (ex-info (str "Spec conform error: " data) data))))))))

(defn select-spec [spec value]
  (conform spec value strip-extra-keys-conforming))

;;
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :form :spec))

(defn- fail-on-invoke [spec]
  (throw
    (ex-info
      (str
        "Can't invoke spec with a non-function predicate: " spec)
      {:spec spec})))

(defrecord Spec [spec form type]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [this x]
    (let [conforming *conforming*]
      (if-let [conform (if conforming (conforming this))]
        (let [conformed (conform this x)]
          (or (and (= +invalid+ conformed) conformed)
              (s/conform spec conformed)))
        (s/conform spec x))))
  (unform* [_ x]
    (s/unform spec x))
  (explain* [this path via in x]
    (let [problems (if (or (s/spec? spec) (s/regex? spec))
                     ;; conforming might fail deliberately, while the vanilla
                     ;; conform would succeed - we'll short-circuit it here.
                     ;; https://dev.clojure.org/jira/browse/CLJ-2115 would help
                     (let [conformed (s/conform* this x)
                           [explain? val] (if (= conformed +invalid+)
                                            [(= (conform this x) +invalid+) x]
                                            [true (s/unform spec conformed)])]
                       (if explain?
                         (s/explain* (s/specize* spec) path via in val)
                         [{:path path
                           :pred form
                           :val val
                           :via via
                           :in in}]))
                     (if (= +invalid+ (s/conform* this x))
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
        (s/gen* spec overrides path rmap))))
  (with-gen* [this gfn]
    (assoc this :gen gfn))
  (describe* [this]
    (let [data (merge {:spec form} (extra-spec-map this))]
      `(spec-tools.core/spec ~data)))
  IFn
  #?(:clj  (invoke [this x] (if (ifn? spec) (spec x) (fail-on-invoke this)))
     :cljs (-invoke [this x] (if (ifn? spec) (spec x) (fail-on-invoke this)))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (merge
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
  (cond
    (and (spec? spec) (:description spec)) (:description spec)
    :else nil))

(defn create-spec
  "Creates a Spec intance from a map containing the following keys:

           :spec  the wrapped spec predicate (mandatory)
           :form  source code of the spec predicate, if :spec is a spec,
                  :form is read with `s/form` out of it. For non-spec
                  preds, spec-tools.forms/resolve-form is called, if still
                  not available, spec-creation will fail.
           :type  optional type for the spec. if not set, will be auto-
                  resolved via spec-tools.forms/resolve-type (optional)
         :reason  reason to be added to problems with s/explain (optional)
            :gen  generator function for the spec (optional)
           :name  name of the spec (optional)
    :description  description of the spec (optional)
          :xx/yy  any qualified keys can be added (optional)"
  [{:keys [spec type form] :as m}]
  (assert spec "missing spec predicate")
  (when (qualified-keyword? spec)
    (assert (s/get-spec spec) (str " Unable to resolve spec: " (:spec m))))
  (let [spec (if (qualified-keyword? spec)
               (s/get-spec spec) spec)
        form (or (if (qualified-keyword? form)
                   (s/form form))
                 form
                 (let [form (s/form spec)]
                   (if-not (= form ::s/unknown) form))
                 (form/resolve-form spec)
                 ::s/unknown)
        info (parse/parse-spec form)
        type (if-not (contains? m :type) (:type info) type)
        name (-> spec meta ::s/name)
        record (map->Spec
                 (merge m info {:spec spec :form form, :type type}))]
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
           (merge
             info#
             {:form form#
              :spec ~pred}))))))

#?(:clj
   (defmacro doc
     "Creates a Spec instance with one or two arguments,
      setting the :type to nil (e.g. no dynamic conforming).

      ;; using type inference
      (doc integer?)

      ;; with explicit type
      (doc integer? {:name \"it's a integer\"})

      ;; map form
      (doc {:spec integer?, :name \"it's a integer\"}})

      calls `spec`, see it for details."
     ([pred-or-info]
      (let [[spec info] (impl/extract-pred-and-info pred-or-info)]
        `(doc ~spec ~info)))
     ([spec info]
      `(spec ~spec (merge ~info {:type nil})))))
