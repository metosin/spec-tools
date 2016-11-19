(ns spec-tools.convert
  #?(:cljs (:refer-clojure :exclude [type Inst Keyword UUID]))
  (:require [clojure.spec :as s]
    #?@(:cljs [[goog.date.UtcDateTime]
               [goog.date.Date]])))

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
      #?(:clj  (java.lang.Double/parseDouble x)
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
      #?(:clj  (java.util.UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->date-time [x]
  (if (string? x)
    (try
      #?(:clj  (.toDate (org.joda.time.DateTime/parse x))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))
