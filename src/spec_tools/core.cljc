(ns spec-tools.core
  (:refer-clojure :exclude [type string? integer? int? double? keyword? boolean? uuid? inst?])
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

(declare ->Type)

(defrecord Type [form pred gfn info]
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
  (with-gen* [_ gfn] (->Type form pred gfn info))
  (describe* [_] `(spec-tools.core/type ~form))
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
                      (if-let [info (:info t)]
                        {:info info}))))))

(defmacro type [pred]
  `(->Type '~(or (->> pred #?(:clj resolve, :cljs (resolve &env)) ->sym) pred) ~pred nil nil))

(defn with-info [^Type t info]
  (map->Type (assoc t :info info)))

(defn info [^Type t]
  (:info t))

;;
;; concrete types
;;

(def string? (type clojure.core/integer?))
(def integer? (type clojure.core/integer?))
(def int? (type clojure.core/int?))
(def double? (type double-like?))
(def keyword? (type clojure.core/keyword?))
(def boolean? (type clojure.core/boolean?))
(def uuid? (type clojure.core/uuid?))
(def inst? (type clojure.core/inst?))
