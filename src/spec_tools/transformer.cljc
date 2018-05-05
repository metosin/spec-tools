(ns spec-tools.transformer
  #?(:cljs (:refer-clojure :exclude [Inst Keyword UUID]))
  (:require [clojure.spec.alpha :as s]
    #?@(:cljs [[goog.date.UtcDateTime]
               [goog.date.Date]])
            [clojure.set :as set])
  #?(:clj
     (:import (java.util Date UUID)
              (com.fasterxml.jackson.databind.util StdDateFormat)
              (java.time Instant))))

;;
;; Strings
;;

(defn string->long [_ x]
  (if (string? x)
    (try
      #?(:clj  (Long/parseLong x)
         :cljs (let [x' (js/parseInt x 10)]
                 (if (js/isNaN x') x x')))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->double [_ x]
  (if (string? x)
    (try
      #?(:clj  (Double/parseDouble x)
         :cljs (let [x' (js/parseFloat x)]
                 (if (js/isNaN x') x x')))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->keyword [_ x]
  (if (string? x)
    (keyword x)
    x))

(defn keyword->string [_ x]
  (if (keyword? x)
    (let [name (name x)]
      (if-let [ns (namespace x)]
        (str ns "/" name) name))
    x))

(defn string->boolean [_ x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else x)
    x))

(defn string->uuid [_ x]
  (if (string? x)
    (try
      #?(:clj  (UUID/fromString x)
         ;; http://stackoverflow.com/questions/7905929/how-to-test-valid-uuid-guid
         :cljs (if (re-find #"^[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$" x)
                 (uuid x)
                 ;; TODO: what should this return?
                 x))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->date [_ x]
  (if (string? x)
    (try
      #?(:clj  (.parse (StdDateFormat.) x)
         :cljs (js/Date. (.getTime (goog.date.UtcDateTime.fromIsoString x))))
      (catch #?(:clj Exception, :cljs js/Error) _ x))
    x))

(defn string->symbol [_ x]
  (if (string? x)
    (symbol x)
    x))

(defn string->nil [_ x]
  (if (= "" x)
    nil
    x))

;;
;; Maps
;;

(defn strip-extra-keys [{:keys [keys]} x]
  (if (and keys (map? x))
    (select-keys x keys)
    x))

;; TODO: remove this as it couples transformation & validation?
(defn fail-on-extra-keys [{:keys [keys]} x]
  (if (and (map? x) (not (set/subset? (-> x (clojure.core/keys) (set)) keys)))
    ::s/invalid
    x))

;;
;; type decoders
;;

(def json-type-decoders
  (merge
    {:keyword string->keyword
     :uuid string->uuid
     :date string->date
     :symbol string->symbol}
    #?(:clj
       {:uri nil
        :bigdec nil
        :ratio nil})))

(def string-type-decoders
  (merge
    json-type-decoders
    {:long string->long
     :double string->double
     :boolean string->boolean
     :nil string->nil
     :string nil}))

(def strip-extra-keys-type-decoders
  {:map strip-extra-keys})

(def fail-on-extra-keys-type-decoders
  {:map fail-on-extra-keys})

;;
;; type encoders
;;

(def json-type-encoders
  (merge
    {:keyword keyword->string}))

(def string-type-encoders
  json-type-encoders)
