(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  (:require [cljs.analyzer.api :refer [resolve]]
            [clojure.walk :as walk]))

(defn in-cljs? [env]
  (:ns env))

(defn- ->sym
  "Returns a symbol from a symbol or var"
  [x]
  (if (map? x)
    (:name x)
    x))

(defn- unfn [expr]
  (if (clojure.core/and (seq? expr)
                        (symbol? (first expr))
                        (= "fn*" (name (first expr))))
    (let [[[s] & form] (rest expr)]
      (conj (walk/postwalk-replace {s '%} form) '[%] 'fn))
    expr))

(defn res [env form]
  (cond
    (keyword? form) form
    (symbol? form) (clojure.core/or (->> form (resolve env) ->sym) form)
    (sequential? form) (walk/postwalk #(if (symbol? %) (res env %) %) (unfn form))
    :else form))
