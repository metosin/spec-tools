(ns spec-tools.core
  (:refer-clojure :exclude [string? integer? int? double? keyword? boolean? uuid? inst?])
  (:require
    [clojure.spec :as s]
    [clojure.spec.gen :as gen]
    #?@(:cljs [goog.date.UtcDateTime]))
  #?(:clj
     (:import [java.util Date UUID])))

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

(def string-conformations
  {clojure.core/integer? -string->int
   clojure.core/int? -string->long
   double-like? -string->double
   clojure.core/keyword? -string->keyword
   clojure.core/boolean? -string->boolean
   clojure.core/uuid? -string->uuid
   clojure.core/inst? -string->inst})

(def json-conformations
  {clojure.core/keyword? -string->keyword
   clojure.core/uuid? -string->uuid
   clojure.core/inst? -string->inst})

(def ^:dynamic ^:private *conformations* nil)

(defn dynamically-conformed [pred]
  (s/with-gen
  (s/conformer
    (fn [x]
      (if (pred x)
        x
        (if (clojure.core/string? x)
          (if-let [conformer (get *conformations* pred)]
            (conformer x)
            '::s/invalid)
          ::s/invalid)))
      identity)
    #(s/gen pred)))

(defn conform
  ([spec value]
   (s/conform spec value))
  ([spec value conformers]
   (binding [*conformations* conformers]
     (s/conform spec value))))

;;
;; types
;;

(def string? clojure.core/string?)
(def integer? (dynamically-conformed clojure.core/integer?))
(def int? (dynamically-conformed clojure.core/int?))
(def double? (dynamically-conformed double-like?))
(def keyword? (dynamically-conformed clojure.core/keyword?))
(def boolean? (dynamically-conformed clojure.core/boolean?))
(def uuid? (dynamically-conformed clojure.core/uuid?))
(def inst? (dynamically-conformed clojure.core/inst?))
