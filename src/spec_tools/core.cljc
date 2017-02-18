(ns spec-tools.core
  (:refer-clojure :exclude [any? some? number? integer? int? pos-int? neg-int? nat-int?
                            float? double? boolean? string? ident? simple-ident? qualified-ident?
                            keyword? simple-keyword? qualified-keyword? symbol? simple-symbol?
                            qualified-symbol? uuid? uri? bigdec? inst? seqable? indexed?
                            map? vector? list? seq? char? set? nil? false? true? zero?
                            rational? coll? empty? associative? sequential? ratio? bytes?
                            #?@(:cljs [Inst Keyword UUID])])
  #?(:cljs (:require-macros [spec-tools.core :refer [spec coll-spec]]))
  (:require
    [spec-tools.impl :as impl]
    [spec-tools.types :as types]
    [spec-tools.convert :as convert]
    [clojure.spec :as s]
    #?@(:clj  [[clojure.spec.gen :as gen]
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

(def invalid '::s/invalid)

;;
;; Dynamic conforming
;;

(def ^:dynamic ^:private *conformers* nil)

(def string-conformers
  {:long convert/string->long
   :double convert/string->double
   :keyword convert/string->keyword
   :boolean convert/string->boolean
   :uuid convert/string->uuid
   :date convert/string->date})

(def json-conformers
  {:keyword convert/string->keyword
   :uuid convert/string->uuid
   :date convert/string->date})

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

;;
;; Spec Record
;;

(defn- extra-spec-map [t]
  (dissoc t :form :pred :gfn))

(defrecord Spec [hint form pred gfn]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [_ x]
    ;; function predicate
    (if (and (fn? pred) (pred x))
      x
      ;; there is a dynamic conformer
      (if-let [conformer (get *conformers* hint)]
        (conformer pred x)
        ;; spec predicate
        (if (s/spec? pred)
          (s/conform pred x)
          ;; invalid
          '::s/invalid))))
  (unform* [_ x]
    x)
  (explain* [_ path via in x]
    (when (= ::s/invalid (if (pred x) x ::s/invalid))
      [{:path path :pred (s/abbrev form) :val x :via via :in in}]))
  (gen* [_ _ _ _]
    (if gfn
      (gfn)
      (gen/gen-for-pred pred)))
  (with-gen* [this gfn]
    (assoc this :gfn gfn))
  (describe* [this]
    (let [info (extra-spec-map this)]
      `(spec ~form ~info)))
  IFn
  #?(:clj  (invoke [_ x] (pred x))
     :cljs (-invoke [_ x] (pred x))))

#?(:clj
   (defmethod print-method Spec
     [^Spec t ^Writer w]
     (.write w (str "#Spec"
                    (merge
                      {:pred (:form t)}
                      (if (:hint t) (select-keys t [:hint]))
                      (extra-spec-map t))))))

#?(:clj
   (defmacro spec
     ([pred]
      (if (impl/in-cljs? &env)
        `(let [form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred)
                       (s/form ~pred))]
           (map->Spec
             {:hint (types/resolve-type form#)
              :pred ~pred
              :form form#}))
        `(let [form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred resolve impl/->sym)) pred)
                       (s/form ~pred))]
           (map->Spec
             {:hint (types/resolve-type form#)
              :pred ~pred
              :form form#}))))
     ([pred info]
      (if (impl/in-cljs? &env)
        `(let [info# ~info
               form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred (impl/cljs-resolve &env) impl/->sym)) pred)
                       (s/form ~pred))]
           (assert (clojure.core/map? info#) (str "spec info should be a map, was: " info#))
           (map->Spec
             (merge
               ~info
               (if-not (contains? info# :hint)
                 {:hint (types/resolve-type form#)})
               {:form form#
                :pred ~pred})))
        `(let [info# ~info
               form# (if (clojure.core/symbol? '~pred)
                       '~(or (and (clojure.core/symbol? pred) (some->> pred resolve impl/->sym)) pred)
                       (s/form ~pred))]
           (assert (clojure.core/map? info#) (str "spec info should be a map, was: " info#))
           (map->Spec
             (merge
               ~info
               (if-not (contains? info# :hint)
                 {:hint (types/resolve-type form#)})
               {:form form#
                :pred ~pred})))))))

#?(:clj
   (defmacro doc [pred info]
     `(spec ~pred (merge ~info {:hint nil}))))

#?(:clj
   (defmacro typed-spec
     ([hint pred]
      `(spec ~pred {:hint ~hint}))
     ([hint pred info]
      `(spec ~pred (merge ~info {:hint ~hint})))))

;;
;; Map Spec
;;

(defrecord OptionalKey [k])
(defrecord RequiredKey [k])

(defn opt [k] (->OptionalKey k))
(defn req [k] (->RequiredKey k))

#?(:clj (declare ^:private coll-spec-fn))

(defn- -vector [env n v]
  (if-not (= 1 (count v))
    (throw
      (ex-info
        "only single maps allowed in nested vectors"
        {:k n :v v}))
    `(s/coll-of ~(coll-spec-fn env n (first v)) :into [])))

(defn- -set [env n v]
  (if-not (= 1 (count v))
    (throw
      (ex-info
        "only single maps allowed in nested sets"
        {:k n :v v}))
    `(s/coll-of ~(coll-spec-fn env n (first v)) :into #{})))

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
         `(s/map-of ~key-spec ~(coll-spec-fn env n (first (vals m))))
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
              (s/keys ~@margs)))))))

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
(def uri? (spec clojure.core/uri?))                         ;; not in cljs
(def bigdec? (spec clojure.core/bigdec?))                   ;; not in cljs
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
(def rational? (spec clojure.core/rational?))               ;; not in cljs
(def coll? (spec clojure.core/coll?))
(def empty? (spec clojure.core/empty?))
(def associative? (spec clojure.core/associative?))
(def sequential? (spec clojure.core/sequential?))
(def ratio? (spec clojure.core/ratio?))                     ;; not in cljs
(def bytes? (spec clojure.core/bytes?))                     ;; not in cljs
