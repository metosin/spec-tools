(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  (:require #?(:cljs [cljs.analyzer.api :refer [resolve]])
    [spec-tools.form :as form]
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

(defn- unfn [cljs? expr]
  (if (clojure.core/and (seq? expr)
                        (symbol? (first expr))
                        (= "fn*" (name (first expr))))
    (let [[[s] & form] (rest expr)]
      (conj (walk/postwalk-replace {s '%} form) '[%] (if cljs? 'cljs.core/fn 'clojure.core/fn)))
    expr))

#?(:cljs
   (defn cljs-resolve [env symbol]
     (clojure.core/or (->> symbol (resolve env) cljs-sym) symbol))
   :clj (declare cljs-resolve))

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

(defn resolve-form [env pred]
  (let [cljs? (in-cljs? env)
        res (if cljs? (partial cljs-resolve env) clojure.core/resolve)]
    (->> pred
         (walk/postwalk
           (fn [x]
             (if (symbol? x)
               (or (some->> x res ->sym) x)
               x)))
         (unfn cljs?)
         #_(walk/postwalk clojure-core-symbol-or-any))))

(defn extract-pred-and-info [x]
  (if (map? x)
    [(:spec x) (dissoc x :spec)]
    [x {}]))

;;
;; FIXME: using ^:skip-wiki functions from clojure.spec. might break.
;;

(defn register-spec! [k s]
  (s/def-impl k (s/form s) s))
