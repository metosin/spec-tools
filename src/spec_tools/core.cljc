(ns spec-tools.core
  #?(:cljs (:require-macros [spec-tools.core :refer [spec coll-spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.type :as type]
            [spec-tools.form :as form]
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

(defn ^:skip-wiki eq [value]
  #{value})

(defn ^:skip-wiki set-of [value]
  (s/coll-of
    value
    :into #{}))

(defn ^:skip-wiki enum [& values]
  (s/spec (set values)))

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
      ;; there is a dynamic conformer
      (if-let [conform (get *conforming* type)]
        (conform this x)
        ;; spec or regex
        (if (or (s/spec? spec) (s/regex? spec))
          (s/conform spec x)
          ;; invalid
          +invalid+))))
  (unform* [_ x]
    x)
  (explain* [this path via in x]
    (let [problems (cond

                     (s/spec? spec)
                     (let [conformed (s/conform* this x)
                           val (if (= conformed +invalid+) x (s/unform spec conformed))]
                       (s/explain* spec path via in val))

                     (s/regex? spec)
                     (let [conformed (s/conform* this x)
                           val (if (= conformed +invalid+) x (s/unform spec conformed))]
                       (s/explain* (s/specize* spec) path via in val))

                     :else
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
                 (->> (concat req-un opt-un)
                      (map (comp keyword name impl/polish))))))}))

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
;; Map Spec
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

#?(:clj (declare ^:private coll-spec-fn))

#?(:clj
   (defn- -vector [env n v]
     (if-not (= 1 (count v))
       (throw
         (ex-info
           "only single maps allowed in nested vectors"
           {:k n :v v}))
       `(spec (s/coll-of ~(coll-spec-fn env n (first v)) :into [])))))

#?(:clj
   (defn- -set [env n v]
     (if-not (= 1 (count v))
       (throw
         (ex-info
           "only single maps allowed in nested sets"
           {:k n :v v}))
       `(spec (s/coll-of ~(coll-spec-fn env n (first v)) :into #{})))))

#?(:clj
   (defn- -map [env n m]
     (let [resolve (if (impl/in-cljs? env) (partial impl/cljs-resolve env) resolve)
           resolved-opt (resolve `opt)
           resolved-req (resolve `req)]

       ;; predicate keys
       (if-let [key-spec (and (= 1 (count m))
                              (let [k (first (keys m))]
                                (and
                                  (not
                                    (or (keyword? k)
                                        (and (seq? k)
                                             (let [resolved (resolve (first k))]
                                               (#{resolved-opt resolved-req} resolved)))))
                                  k)))]
         `(spec (s/map-of ~key-spec ~(coll-spec-fn env n (first (vals m)))))
         ;; keyword keys
         (let [m (reduce-kv
                   (fn [acc k v]
                     (let [[req? kv] (if (seq? k) [(not= resolved-opt (resolve (first k))) (second k)] [true k])
                           k1 (if req? "req" "opt")
                           k2 (if-not (qualified-keyword? kv) "-un")
                           ak (keyword (str k1 k2))
                           [k' v'] (if (qualified-keyword? kv)
                                     [kv (if (not= kv v) v)]
                                     (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name kv)))]
                                       [k' (if (or (map? v) (vector? v) (set? v))
                                             (coll-spec-fn env k' v) v)]))]
                       (-> acc
                           (update ak (fnil conj []) k')
                           (cond-> v' (update ::defs (fnil conj []) [k' v'])))))
                   {}
                   m)
               defs (::defs m)
               margs (apply concat (dissoc m ::defs))]
           `(do
              ~@(for [[k v] defs]
                  `(s/def ~k ~v))
              (spec (s/keys ~@margs))))))))

#?(:clj
   (defn- coll-spec-fn [env n m]
     (if-let [f (cond
                  (map? m) -map
                  (vector? m) -vector
                  (set? m) -set)]
       (f env n m)
       `~m)))

#?(:clj
   (defmacro coll-spec [n m]
     (coll-spec-fn &env n m)))
