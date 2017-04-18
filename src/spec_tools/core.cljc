(ns spec-tools.core
  #?(:cljs (:require-macros [spec-tools.core :refer [spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.type :as type]
            [spec-tools.form :as form]
            [spec-tools.conform :as conform]
            [clojure.spec :as s]
    #?@(:clj  [
            [clojure.spec.gen :as gen]
            [clojure.edn]]
        :cljs [[goog.date.UtcDateTime]
               [cljs.reader]
               [goog.date.Date]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]]))
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
(def +problems+ #?(:clj :clojure.spec/problems, :cljs :cljs.spec/problems))

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
    ;; function predicate
    (if (and (fn? spec) (spec x))
      x
      (let [conforming *conforming*]
        ;; there is a dynamic conformer
        (if-let [conform (if conforming (conforming this))]
          (conform this x)
          ;; spec or regex
          (if (or (s/spec? spec) (s/regex? spec))
            (s/conform spec x)
            ;; invalid
            +invalid+)))))
  (unform* [_ x]
    (s/unform* (s/specize* spec) x))
  (explain* [this path via in x]
    (let [problems (if (or (s/spec? spec) (s/regex? spec))
                     (let [conformed (s/conform* this x)
                           val (if (= conformed +invalid+) x (s/unform spec conformed))]
                       (s/explain* (s/specize* spec) path via in val))
                     (when (= +invalid+ (if (and (fn? spec) (spec (s/conform* this x))) x +invalid+))
                       [{:path path
                         :pred (s/abbrev form)
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
  (gen* [this _ _ _]
    (if-let [gen (:gen this)]
      (gen)
      (gen/gen-for-pred spec)))
  (with-gen* [this gfn]
    (assoc this :gen gfn))
  (describe* [this]
    (let [info (extra-spec-map this)]
      `(spec-tools.core/spec ~form ~info)))
  IFn
  #?(:clj  (invoke [this x] (if (fn? spec) (spec x) (fail-on-invoke this)))
     :cljs (-invoke [this x] (if (fn? spec) (spec x) (fail-on-invoke this)))))

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

;; TODO: use http://dev.clojure.org/jira/browse/CLJ-2112
(defmulti collect-info (fn [dispath _] dispath) :default ::default)
(defmethod collect-info ::default [_ _] nil)

;; and's and or's are just flattened
(defmethod collect-info 'clojure.spec/keys [_ form]
  (let [{:keys [req opt req-un opt-un]} (some->> form (rest) (apply hash-map))]
    {:keys (set
             (flatten
               (concat
                 (map impl/polish (concat req opt))
                 (map impl/polish-un (concat req-un opt-un)))))}))

(defn extract-extra-info [form]
  (if (seq? form)
    (let [dispatch (impl/clojure-core-symbol-or-any (first form))]
      (collect-info dispatch form))))

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
  (let [form (or form
                 (let [form (s/form spec)]
                   (if-not (= form ::s/unknown) form))
                 (form/resolve-form spec)
                 ::s/unknown)
        info (extract-extra-info form)
        type (if-not (contains? m :type)
               (type/resolve-type form)
               type)]
    (map->Spec
      (merge m info {:form form, :type type}))))

(defn- extract-pred-and-info [x]
  (if (map? x)
    [(:spec x) (dissoc x :spec)]
    [x {}]))

#?(:clj
   (defmacro spec
     "Creates a Spec instance with one or two arguments:

     ;; using type inference
     (spec integer?)

     ;; with explicit type
     (spec integer? {:type :long})

     ;; map form
     (spec {:spec integer?, :type :long})

     calls create-spec, see it for details."
     ([pred-or-info]
      (let [[pred info] (extract-pred-and-info pred-or-info)]
        `(spec ~pred ~info)))
     ([pred info]
      (if (impl/in-cljs? &env)
        `(let [info# ~info
               form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred))]
           (assert (map? info#) (str "spec info should be a map, was: " info#))
           (create-spec
             (merge
               ~info
               {:form form#
                :spec ~pred})))
        `(let [info# ~info
               form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred resolve impl/->sym)) pred))]
           (assert (map? info#) (str "spec info should be a map, was: " info#))
           (create-spec
             (merge
               ~info
               {:form form#
                :spec ~pred})))))))

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

      calls create-spec, see it for details."
     ([pred-or-info]
      (let [[spec info] (extract-pred-and-info pred-or-info)]
        `(doc ~spec ~info)))
     ([spec info]
      `(spec ~spec (merge ~info {:type nil})))))

;;
;; Data Specs
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])
(defrecord Maybe [v])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))
(defn maybe [v] (->Maybe v))

(defn opt? [x]
  (instance? OptionalKey x))

(defn req? [x]
  (not (opt? x)))

(defn wrapped-key? [x]
  (or (opt? x) (instance? RequiredKey x)))

(defn unwrap-key [x]
  (if (wrapped-key? x) (:k x) x))

(defn maybe? [x]
  (instance? Maybe x))

(declare data-spec)

(defn- -map-spec [n data]
  ;; predicate keys
  (if-let [[k' v'] (and (= 1 (count data))
                        (let [[k v] (first data)]
                          (and
                            (not
                              (or (keyword? k)
                                  (wrapped-key? k)))
                            [k v])))]
    (create-spec {:spec (impl/map-of-spec (data-spec n k') (data-spec n v'))})
    ;; keyword keys
    (let [m (reduce-kv
              (fn [acc k v]
                (let [kv (unwrap-key k)
                      rk (keyword
                           (str (if (req? k) "req" "opt")
                                (if-not (qualified-keyword? kv) "-un")))
                      [v wrap] (if (maybe? v)
                                 [(:v v) (comp #(create-spec {:spec %}) impl/nilable-spec)]
                                 [v identity])
                      [k' n'] (if (qualified-keyword? kv)
                                [kv (if (not= kv v) kv)]
                                (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name (unwrap-key kv))))]
                                  [k' k']))
                      v' (if n' (wrap (data-spec n' v)))]
                  (-> acc
                      (update rk (fnil conj []) k')
                      (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
              {}
              data)
          defs (::defs m)
          margs (apply concat (dissoc m ::defs))]
      (doseq [[k s] defs]
        (impl/register-spec! k s))
      (create-spec {:spec (impl/keys-spec margs)}))))

(defn- -coll-spec [n v proto]
  (when-not (= 1 (count v))
    (throw
      (ex-info
        (str "only single maps allowed in nested " proto)
        {:k n :v v})))
  (let [spec (data-spec n (first v))
        form (s/form spec)]
    (create-spec {:spec (impl/coll-of-spec spec form proto)})))

(defn data-spec [name x]
  (cond
    (spec? x) x
    (s/regex? x) x
    (map? x) (-map-spec name x)
    (set? x) (-coll-spec name x #{})
    (vector? x) (-coll-spec name x [])
    :else (create-spec {:spec x})))
