(ns spec-tools.core
  (:refer-clojure :exclude [any? some? number? integer? int? pos-int? neg-int? nat-int?
                            float? double? boolean? string? ident? simple-ident? qualified-ident?
                            keyword? simple-keyword? qualified-keyword? symbol? simple-symbol?
                            qualified-symbol? uuid? uri? bigdec? inst? seqable? indexed?
                            map? vector? list? seq? char? set? nil? false? true? zero?
                            rational? coll? empty? associative? sequential? ratio? bytes?
                            #?@(:cljs [Inst Keyword UUID])])
  #?(:cljs (:require-macros [spec-tools.core :refer [spec coll-spec]]))
  (:require [spec-tools.impl :as impl]
            [spec-tools.types :as types]
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
    :kind clojure.core/set?))

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
               {:type :spec/problems
                :problems (+problems+ problems)
                :spec spec
                :value value}))))))))

;;
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :spec/form :pred))

(defn- fail-on-invoke [spec]
  (throw
    (ex-info
      (str
        "Can't invoke spec with a non-function predicate: " spec)
      {:spec spec})))

(defrecord Spec [pred]
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
      (if-let [conformer (get *conformers* (:spec/type this))]
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
                         :pred (s/abbrev (:spec/form this))
                         :val x
                         :via via
                         :in in}]))
          spec-reason (:spec/reason this)
          with-reason (fn [{:keys [reason] :as problem}]
                        (cond-> problem
                                (and spec-reason (not reason))
                                (assoc :reason spec-reason)))]
      (if problems
        (map with-reason problems))))
  (gen* [this _ _ _]
    (if-let [gen (:spec/gen this)]
      (gen)
      (gen/gen-for-pred pred)))
  (with-gen* [this gfn]
    (assoc this :spec/gen gfn))
  (describe* [this]
    (let [info (extra-spec-map this)]
      `(spec ~(:spec/form this) ~info)))
  IFn
  #?(:clj  (invoke [this x] (if (fn? pred) (pred x) (fail-on-invoke this)))
     :cljs (-invoke [this x] (if (fn? pred) (pred x) (fail-on-invoke this)))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (merge
                      (select-keys t [:spec/form])
                      (if (:spec/type t) (select-keys t [:spec/type]))
                      (extra-spec-map t))))))

(defn spec? [x]
  (if (instance? Spec x) x))

;; TODO: use http://dev.clojure.org/jira/browse/CLJ-2112
(defn- extract-extra-info [form]
  (if (and
        (clojure.core/seq? form)
        (= 'clojure.spec/keys (impl/clojure-core-symbol-or-any (first form))))
    (if-let [m (some->> form
                        (rest)
                        (apply hash-map))]
      {:spec/keys (set
                    (concat
                      (:req m)
                      (:opt m)
                      (map (comp keyword name) (:req-un m))
                      (map (comp keyword name) (:opt-un m))))})))

(defn create-spec [m]
  (let [form (or (:spec/form m) (s/form (:pred m)))
        info (extract-extra-info form)
        type (if-not (contains? m :spec/type)
               (types/resolve-type form)
               (:spec/type m))]
    (map->Spec (merge m info {:spec/form form, :spec/type type}))))

(defn- extract-pred-and-info [x]
  (if (clojure.core/map? x)
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
               form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred))]
           (assert (clojure.core/map? info#) (str "spec info should be a map, was: " info#))
           (create-spec
             (merge
               ~info
               {:spec/form form#
                :pred ~pred})))
        `(let [info# ~info
               form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred resolve impl/->sym)) pred))]
           (assert (clojure.core/map? info#) (str "spec info should be a map, was: " info#))
           (create-spec
             (merge
               ~info
               {:spec/form form#
                :pred ~pred})))))))

#?(:clj
   (defmacro doc
     ([pred-or-info]
      (let [[pred info] (extract-pred-and-info pred-or-info)]
        `(doc ~pred ~info)))
     ([pred info]
      `(spec ~pred (merge ~info {:spec/type nil})))))

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
                                    (or (clojure.core/keyword? k)
                                        (and (clojure.core/seq? k)
                                             (let [resolved (resolve (first k))]
                                               (#{resolved-opt resolved-req} resolved)))))
                                  k)))]
         `(spec (s/map-of ~key-spec ~(coll-spec-fn env n (first (vals m)))))
         ;; keyword keys
         (let [m (reduce-kv
                   (fn [acc k v]
                     (let [[req? kv] (if (clojure.core/seq? k) [(not= resolved-opt (resolve (first k))) (second k)] [true k])
                           k1 (if req? "req" "opt")
                           k2 (if-not (clojure.core/qualified-keyword? kv) "-un")
                           ak (keyword (str k1 k2))
                           [k' v'] (if (clojure.core/qualified-keyword? kv)
                                     [kv (if (not= kv v) v)]
                                     (let [k' (keyword (str (str (namespace n) "$" (name n)) "/" (name kv)))]
                                       [k' (if (or (clojure.core/map? v) (clojure.core/vector? v) (clojure.core/set? v))
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
                  (clojure.core/map? m) -map
                  (clojure.core/vector? m) -vector
                  (clojure.core/set? m) -set)]
       (f env n m)
       `~m)))

#?(:clj
   (defmacro coll-spec [n m]
     (coll-spec-fn &env n m)))

;;
;; clojure.core predicates as Specs
;;

(def any? (spec clojure.core/any?))
(def some? (spec clojure.core/some?))
(def number? (spec clojure.core/number?))
(def integer? (spec clojure.core/integer?))
(def int? (spec clojure.core/int?))
(def pos-int? (spec clojure.core/pos-int?))
(def neg-int? (spec clojure.core/neg-int?))
(def nat-int? (spec clojure.core/nat-int?))
(def float? (spec clojure.core/float?))
(def double? (spec clojure.core/double?))
(def boolean? (spec clojure.core/boolean?))
(def string? (spec clojure.core/string?))
(def ident? (spec clojure.core/ident?))
(def simple-ident? (spec clojure.core/simple-ident?))
(def qualified-ident? (spec clojure.core/qualified-ident?))
(def keyword? (spec clojure.core/keyword?))
(def simple-keyword? (spec clojure.core/simple-keyword?))
(def qualified-keyword? (spec clojure.core/qualified-keyword?))
(def symbol? (spec clojure.core/symbol?))
(def simple-symbol? (spec clojure.core/simple-symbol?))
(def qualified-symbol? (spec clojure.core/qualified-symbol?))
(def uuid? (spec clojure.core/uuid?))
#?(:clj (def uri? (spec clojure.core/uri?)))
#?(:clj (def bigdec? (spec clojure.core/bigdec?)))
(def inst? (spec clojure.core/inst?))
(def seqable? (spec clojure.core/seqable?))
(def indexed? (spec clojure.core/indexed?))
(def map? (spec clojure.core/map?))
(def vector? (spec clojure.core/vector?))
(def list? (spec clojure.core/list?))
(def seq? (spec clojure.core/seq?))
(def char? (spec clojure.core/char?))
(def set? (spec clojure.core/set?))
(def nil? (spec clojure.core/nil?))
(def false? (spec clojure.core/false?))
(def true? (spec clojure.core/true?))
(def zero? (spec clojure.core/zero?))
#?(:clj (def rational? (spec clojure.core/rational?)))
(def coll? (spec clojure.core/coll?))
(def empty? (spec clojure.core/empty?))
(def associative? (spec clojure.core/associative?))
(def sequential? (spec clojure.core/sequential?))
#?(:clj (def ratio? (spec clojure.core/ratio?)))
#?(:clj (def bytes? (spec clojure.core/bytes?)))
