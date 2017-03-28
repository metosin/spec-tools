(ns spec-tools.core
  #?(:cljs (:require-macros [spec-tools.core :refer [spec coll-spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.types :as types]
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

(defn ^:skip-wiki registry
  ([]
   (s/registry))
  ([re]
   (->> (s/registry)
        (filter #(-> % first str (subs 1) (->> (re-matches re))))
        (into {}))))

(defn ^:skip-wiki eq [value]
  #{value})

(defn ^:skip-wiki set-of [value]
  (s/coll-of
    value
    :kind set?))

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

(def ^:dynamic ^:private *conformers* nil)

(defn explain
  ([spec value]
   (explain spec value nil))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/explain spec value))))

(defn explain-data
  ([spec value]
   (explain-data spec value nil))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/explain-data spec value))))

(defn conform
  ([spec value]
   (conform spec value nil))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

(defn conform!
  ([spec value]
   (conform! spec value nil))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (let [conformed (s/conform spec value)]
       (if-not (= conformed +invalid+)
         conformed
         (let [problems (s/explain-data spec value)]
           (throw
             (ex-info
               "Spec conform error"
               {:type ::problems
                :problems (+problems+ problems)
                :spec spec
                :value value}))))))))

;;
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :form :pred))

(defn- fail-on-invoke [spec]
  (throw
    (ex-info
      (str
        "Can't invoke spec with a non-function predicate: " spec)
      {:spec spec})))

(defrecord Spec [pred form type]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [this x]
    ;; function predicate
    (if (and (fn? pred) (pred x))
      x
      ;; there is a dynamic conformer
      (if-let [conformer (get *conformers* type)]
        (conformer this x)
        ;; spec predicate
        (if (s/spec? pred)
          (s/conform pred x)
          ;; invalid
          +invalid+))))
  (unform* [_ x]
    x)
  (explain* [this path via in x]
    (let [problems (if (s/spec? pred)
                     (s/explain* pred path via in (s/conform* this x))
                     (when (= +invalid+ (if (and (fn? pred) (pred (s/conform* this x))) x +invalid+))
                       [{:path path
                         :pred (s/abbrev form)
                         :val x
                         :via via
                         :in in}]))
          spec-reason (:reason this)
          with-reason (fn [{:keys [reason] :as problem}]
                        (cond-> problem
                                (and spec-reason (not reason))
                                (assoc :reason spec-reason)))]
      (if problems
        (map with-reason problems))))
  (gen* [this _ _ _]
    (if-let [gen (:gen this)]
      (gen)
      (gen/gen-for-pred pred)))
  (with-gen* [this gfn]
    (assoc this :gen gfn))
  (describe* [this]
    (let [info (extra-spec-map this)]
      `(spec ~form ~info)))
  IFn
  #?(:clj  (invoke [this x] (if (fn? pred) (pred x) (fail-on-invoke this)))
     :cljs (-invoke [this x] (if (fn? pred) (pred x) (fail-on-invoke this)))))

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
(defn- extract-extra-info [form]
  (if (and
        (seq? form)
        (= 'clojure.spec/keys (impl/clojure-core-symbol-or-any (first form))))
    (if-let [m (some->> form
                        (rest)
                        (apply hash-map))]
      {:keys (set
               (concat
                 (:req m)
                 (:opt m)
                 (map (comp keyword name) (:req-un m))
                 (map (comp keyword name) (:opt-un m))))})))

(defn create-spec [m]
  (let [form (or (:form m) (s/form (:pred m)))
        info (extract-extra-info form)
        type (if-not (contains? m :type)
               (types/resolve-type form)
               (:type m))]
    (map->Spec (merge m info {:form form, :type type}))))

(defn- extract-pred-and-info [x]
  (if (map? x)
    [(:pred x) (dissoc x :pred)]
    [x {}]))

#?(:clj
   (defmacro spec
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
                :pred ~pred})))
        `(let [info# ~info
               form# (if (symbol? '~pred)
                       '~(or (and (symbol? pred) (some->> pred resolve impl/->sym)) pred))]
           (assert (map? info#) (str "spec info should be a map, was: " info#))
           (create-spec
             (merge
               ~info
               {:form form#
                :pred ~pred})))))))

#?(:clj
   (defmacro doc
     ([pred-or-info]
      (let [[pred info] (extract-pred-and-info pred-or-info)]
        `(doc ~pred ~info)))
     ([pred info]
      `(spec ~pred (merge ~info {:type nil})))))

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
