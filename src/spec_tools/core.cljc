(ns spec-tools.core
  (:require [clojure.spec :as s]))

(def ^:dynamic *conform-mode* nil)

(defn string->int [x]
  (if (string? x)
    (try
      (Integer/parseInt x)
      (catch Exception _
        :clojure.spec/invalid))))

(defn string->long [x]
  (if (string? x)
    (try
      (Long/parseLong x)
      (catch Exception _
        :clojure.spec/invalid))))

(defn string->double [x]
  (if (string? x)
    (try
      (Double/parseDouble x)
      (catch Exception _
        :clojure.spec/invalid))))

(defn string->keyword [x]
  (if (string? x)
    (keyword x)))

(defn string->boolean [x]
  (if (string? x)
    (cond
      (= "true" x) true
      (= "false" x) false
      :else :clojure.spec/invalid)))

(def +string-conformers+
  {::int string->int
   ::long string->long
   ::double string->double
   ::keyword string->keyword
   ::boolean string->boolean})

(def +conform-modes+
  {::string [string? +string-conformers+]})

(defn dynamic-conformer [accept? type]
  (with-meta
    (s/conformer
      (fn [x]
        (if (accept? x)
          x
          (if-let [[accept? conformers] (+conform-modes+ *conform-mode*)]
            (if (accept? x)
              ((type conformers) x)
              :clojure.spec/invalid)
            :clojure.spec/invalid))))
    {::type type}))

(def aInt (dynamic-conformer integer? ::int))
(def aBool (dynamic-conformer boolean? ::boolean))
(def aLong (dynamic-conformer (partial instance? Long) ::long))
(def aKeyword (dynamic-conformer boolean? ::keyword))
