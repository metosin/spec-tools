(ns spec-tools.conform
  #?(:cljs (:refer-clojure :exclude [Inst Keyword UUID]))
  (:require [clojure.spec :as s]
    #?@(:cljs [[goog.date.UtcDateTime]
               [goog.date.Date]])
            [clojure.string :as str]
            [clojure.set :as set])
  #?(:clj
     (:import (java.util Date UUID)
              (java.time Instant))))

;;
;; Strings
;;

(defn string->long [_ x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (js/parseInt x 10))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->double [_ x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (js/parseFloat x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->keyword [_ x]
  (if (string? x)
    (keyword x)))

(defn string->boolean [_ x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else ::s/invalid)))

(defn string->uuid [_ x]
  (if (string? x)
    (try
      #?(:clj  (UUID/fromString x)
         :cljs (uuid x))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->date [_ x]
  (if (string? x)
    (try
      #?(:clj  (Date/from
                 (Instant/parse x))
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj  Exception
                :cljs js/Error) _
        ::s/invalid))))

(defn string->symbol [_ x]
  (if (string? x)
    (symbol x)))

(defn string->nil [_ x]
  (if-not (str/blank? x)
    ::s/invalid))

;;
;; Maps
;;

(defn strip-extra-keys [{:keys [keys spec]} x]
  (if (map? x)
    (s/conform spec (select-keys x keys))
    x))

(defn fail-on-extra-keys [{:keys [keys spec]} x]
  (if (and (map? x) (not (set/subset? (-> x (clojure.core/keys) (set)) keys)))
    ::s/invalid
    x))

;;
;; conforming
;;

(def json-type-conforming-opts
  (merge
    {:keyword string->keyword
     :uuid string->uuid
     :date string->date
     :symbol string->symbol}
    #?(:clj
       {:uri nil
        :bigdec nil
        :ratio nil})))

(def string-type-conforming-opts
  (merge
    json-type-conforming-opts
    {:long string->long
     :double string->double
     :boolean string->boolean
     :nil string->nil
     :string nil}))

(def strip-extra-keys-type-conforming-opts
  {:map strip-extra-keys})

(def fail-on-extra-keys-type-conforming-opts
  {:map fail-on-extra-keys})
