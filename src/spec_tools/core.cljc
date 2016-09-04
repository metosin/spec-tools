(ns spec-tools.core
  (:refer-clojure :exlude [integer? int? double? keyword? boolean? uuid? inst?])
  (:require [clojure.spec :as s]
    #?@(:cljs [goog.date.UtcDateTime]))
  #?(:clj
     (:import [java.util Date UUID])))

(def ^:dynamic *conform-mode* nil)

(defn- double-like? [x]
  (#?(:clj  double?
      :cljs number?) x))

(defn string->int [x]
  (if (string? x)
    (try
      #?(:clj  (Integer/parseInt x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->long [x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->double [x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (js/parseFloat x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->keyword [x]
  (if (string? x)
    (keyword x)))

(defn string->boolean [x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else ::s/invalid)))

(defn string->uuid [x]
  (if (string? x)
    (try
      #?(:clj  (UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->inst [x]
  (if (string? x)
    (try
      #?(:clj  (.toDate (org.joda.time.DateTime/parse x))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(def +conformation+
  {:string [string? {integer? string->int
                     int? string->long
                     double-like? string->double
                     keyword? string->keyword
                     boolean? string->boolean
                     uuid? string->uuid
                     inst? string->inst}]
   :json [string? {keyword? string->keyword
                   uuid? string->uuid
                   inst? string->inst}]})

(defn dynamic-conformer [pred]
  (with-meta
    (s/conformer
      (fn [x]
        (if (pred x)
          x
          (if-let [[accept? conformers] (+conformation+ *conform-mode*)]
            (if (accept? x)
              (if-let [conformer (get conformers pred)]
                (conformer x)
                ::s/invalid)
              ::s/invalid)
            ::s/invalid))))
    {::pred pred}))

(defn conform
  ([spec value]
   (s/conform spec value))
  ([mode spec value]
   (binding [*conform-mode* mode]
     (s/conform spec value))))

;;
;; types
;;

(def integer? (dynamic-conformer clojure.core/integer?))
(def int? (dynamic-conformer clojure.core/int?))
(def double? (dynamic-conformer double-like?))
(def keyword? (dynamic-conformer clojure.core/keyword?))
(def boolean? (dynamic-conformer clojure.core/boolean?))
(def uuid? (dynamic-conformer clojure.core/uuid?))
(def inst? (dynamic-conformer clojure.core/inst?))
