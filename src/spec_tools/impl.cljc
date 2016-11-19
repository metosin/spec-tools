(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  (:require [cljs.analyzer.api :refer [resolve]]
            [clojure.walk :as walk])
  (:import
    #?@(:clj
        [(clojure.lang Var)])))

(defn in-cljs? [env]
  (:ns env))

(defn- cljs-sym [x]
  (if (map? x)
    (:name x)
    x))

(defn- clj-sym [x]
  (if (var? x)
    (let [^Var v x]
      (symbol (str (.name (.ns v)))
              (str (.sym v))))
    x))

(defn ->sym [x]
  #?(:clj  (clj-sym x)
     :cljs (cljs-sym x)))

(defn- unfn [expr]
  (if (clojure.core/and (seq? expr)
                        (symbol? (first expr))
                        (= "fn*" (name (first expr))))
    (let [[[s] & form] (rest expr)]
      (conj (walk/postwalk-replace {s '%} form) '[%] 'fn))
    expr))

(defn cljs-resolve [env form]
  (cond
    (keyword? form) form
    (symbol? form) (clojure.core/or (->> form (resolve env) cljs-sym) form)
    (sequential? form) (walk/postwalk #(if (symbol? %) (cljs-resolve env %) %) (unfn form))
    :else form))
