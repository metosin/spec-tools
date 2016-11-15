(ns spec-tools.core
  (:refer-clojure :exclude [type any? some?
                            number? integer? int? pos-int? neg-int? nat-int? float? double?
                            boolean? string? ident? simple-ident? qualified-ident?
                            keyword? simple-keyword? qualified-keyword? symbol? simple-symbol? qualified-symbol?
                            uuid? uri? bigdec? inst? seqable? indexed? map? vector? list? seq? char? set?
                            nil? false? true? zero? rational? coll? empty? associative? sequential? ratio? bytes?])
  #?(:cljs (:require-macros [spec-tools.core :refer [type]]))
  (:require
    [clojure.spec :as s]
    #?(:clj
    [clojure.spec.gen :as gen])
    #?@(:cljs [[goog.date.UtcDateTime]
               [cljs.analyzer.api :refer [resolve]]
               [clojure.test.check.generators]
               [cljs.spec.impl.gen :as gen]]))
  (:import
    #?@(:clj
        [(java.util Date UUID)
         (clojure.lang AFn IFn Var)
         (java.io Writer)])))

(defn- ->sym [x]
  #?(:clj  (if (var? x)
             (let [^Var v x]
               (symbol (str (.name (.ns v)))
                       (str (.sym v))))
             x)
     :cljs (if (map? x)
             (:name x)
             x)))

(defn- double-like? [x]
  (#?(:clj  clojure.core/double?
      :cljs number?) x))

(defn -string->int [x]
  (if (clojure.core/string? x)
    (try
      #?(:clj  (Integer/parseInt x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->long [x]
  (if (clojure.core/string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->double [x]
  (if (clojure.core/string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (js/parseFloat x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->keyword [x]
  (if (clojure.core/string? x)
    (keyword x)))

(defn -string->boolean [x]
  (if (clojure.core/string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else ::s/invalid)))

(defn -string->uuid [x]
  (if (clojure.core/string? x)
    (try
      #?(:clj  (UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn -string->inst [x]
  (if (clojure.core/string? x)
    (try
      #?(:clj  (.toDate (org.joda.time.DateTime/parse x))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

;;
;; Public API
;;

(def string-conformers
  {clojure.core/integer? -string->int
   clojure.core/int? -string->long
   double-like? -string->double
   clojure.core/keyword? -string->keyword
   clojure.core/boolean? -string->boolean
   clojure.core/uuid? -string->uuid
   clojure.core/inst? -string->inst})

(def json-conformers
  {clojure.core/keyword? -string->keyword
   clojure.core/uuid? -string->uuid
   clojure.core/inst? -string->inst})

(def ^:dynamic ^:private *conformers* nil)

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformers* conformers]
     (s/conform spec value))))

;;
;; Types
;;

(defprotocol TypeLike
  (type-like [this]))

(defn- extra-type-map [t]
  (dissoc t :form :pred :gfn))

(defrecord Type [form pred gfn]
  #?@(:clj
      [s/Specize
       (specize* [s] s)
       (specize* [s _] s)])

  s/Spec
  (conform* [this x]
    (if (pred x)
      x
      (if (clojure.core/string? x)
        (if-let [conformer (get *conformers* (type-like this))]
          (conformer x)
          '::s/invalid)
        ::s/invalid)))
  (unform* [_ x] x)
  (explain* [_ path via in x]
    (when (= ::s/invalid (if (pred x) x ::s/invalid))
      [{:path path :pred (s/abbrev form) :val x :via via :in in}]))
  (gen* [_ _ _ _] (if gfn
                    (gfn)
                    (gen/gen-for-pred pred)))
  (with-gen* [this gfn] (assoc this :gfn gfn))
  (describe* [this]
    (let [info (extra-type-map this)]
      (if (seq info)
        `(spec-tools.core/type ~form ~info)
        `(spec-tools.core/type ~form))))
  IFn
  #?(:clj  (invoke [_ x] (pred x))
     :cljs (-invoke [_ x] (pred x)))

  TypeLike
  (type-like [_] pred))

#?(:clj
   (defmethod print-method Type
     [^Type t ^Writer w]
     (.write w (str "#Type"
                    (merge
                      {:pred (:form t)}
                      (extra-type-map t))))))

(defmacro type
  ([pred]
   `(map->Type {:form '~(or (->> pred #?(:clj resolve, :cljs (resolve &env)) ->sym) pred), :pred ~pred}))
  ([pred info]
   `(map->Type (merge ~info {:form '~(or (->> pred #?(:clj resolve, :cljs (resolve &env)) ->sym) pred), :pred ~pred}))))

(defn with-info [^Type t info]
  (assoc t :info info))

(defn info [^Type t]
  (:info t))

;;
;; concrete types
;;

(def any? (type clojure.core/any?))
(def some? (type clojure.core/some?))
(def number? (type clojure.core/number?))
(def integer? (type clojure.core/integer?))
(def int? (type clojure.core/int?))
(def pos-int? (type clojure.core/pos-int?))
(def neg-int? (type clojure.core/neg-int?))
(def nat-int? (type clojure.core/nat-int?))
(def float? (type clojure.core/float?))
(def double? (type double-like?))
(def boolean? (type clojure.core/boolean?))
(def string? (type clojure.core/string?))
(def ident? (type clojure.core/ident?))
(def simple-ident? (type clojure.core/simple-ident?))
(def qualified-ident? (type clojure.core/qualified-ident?))
(def keyword? (type clojure.core/keyword?))
(def simple-keyword? (type clojure.core/simple-keyword?))
(def qualified-keyword? (type clojure.core/qualified-keyword?))
(def symbol? (type clojure.core/symbol?))
(def simple-symbol? (type clojure.core/simple-symbol?))
(def qualified-symbol? (type clojure.core/qualified-symbol?))
(def uuid? (type clojure.core/uuid?))
(def uri? (type clojure.core/uri?))
(def bigdec? (type clojure.core/bigdec?))
(def inst? (type clojure.core/inst?))
(def seqable? (type clojure.core/seqable?))
(def indexed? (type clojure.core/indexed?))
(def map? (type clojure.core/map?))
(def vector? (type clojure.core/vector?))
(def list? (type clojure.core/list?))
(def seq? (type clojure.core/seq?))
(def char? (type clojure.core/char?))
(def set? (type clojure.core/set?))
(def nil? (type clojure.core/nil?))
(def false? (type clojure.core/false?))
(def true? (type clojure.core/true?))
(def zero? (type clojure.core/zero?))
(def rational? (type clojure.core/rational?))
(def coll? (type clojure.core/coll?))
(def empty? (type clojure.core/empty?))
(def associative? (type clojure.core/associative?))
(def sequential? (type clojure.core/sequential?))
(def ratio? (type clojure.core/ratio?))
(def bytes? (type clojure.core/bytes?))

(comment
  #{any?
    some?
    number?
    integer?
    int?
    pos-int?
    neg-int?
    nat-int?
    float?
    double?
    boolean?
    string?
    ident?
    simple-ident?
    qualified-ident?
    keyword?
    simple-keyword?
    qualified-keyword?
    symbol?
    simple-symbol?
    qualified-symbol?
    uuid?
    uri?
    bigdec?
    inst?
    seqable?
    indexed?
    map?
    vector?
    list?
    seq?
    char?
    set?
    nil?
    false?
    true?
    zero?
    rational?
    coll?
    empty?
    associative?
    sequential?
    ratio?
    bytes?})
