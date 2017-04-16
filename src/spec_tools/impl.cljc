(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  (:require [cljs.analyzer.api :refer [resolve]]
            [clojure.spec :as s]
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

(defn clojure-core-symbol-or-any [x]
  (or
    (if (symbol? x)
      (if-let [ns (get {"cljs.core" "clojure.core"
                        "cljs.spec" "clojure.spec"} (namespace x))]
        (symbol ns (name x))))
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

(defn polish [x]
  (cond
    (seq? x) (flatten (keep polish x))
    (symbol? x) nil
    :else x))

(defn polish-un [x]
  (-> x polish name keyword))

(defn extract-keys [form]
  (let [{:keys [req opt req-un opt-un]} (some->> form (rest) (apply hash-map))]
    (flatten (map polish (concat req opt req-un opt-un)))))

(defn register-spec! [k s]
  (s/def-impl k (s/form s) s))
