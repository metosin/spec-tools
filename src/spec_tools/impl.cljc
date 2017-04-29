(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  (:require [cljs.analyzer.api :refer [resolve]]
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

;;
;; FIXME: using ^:skip-wiki functions from clojure.spec. might break.
;;

(defn register-spec! [k s]
  (s/def-impl k (s/form s) s))

(defn coll-of-spec [pred type]
  (let [form (form/resolve-form pred)]
    (clojure.spec/every-impl
      form
      pred
      {:into type
       ::s/conform-all true
       ::s/describe `(s/coll-of ~form :into ~type),
       ::s/cpred coll?,
       ::s/kind-form (quote nil)}
      nil)))

(defn map-of-spec [kpred vpred]
  (let [forms (map form/resolve-form [kpred vpred])
        tuple (s/tuple-impl forms [kpred vpred])]
    (clojure.spec/every-impl
      `(s/tuple ~@forms)
      tuple
      {:into {}
       :conform-keys true
       ::s/conform-all true
       ::s/describe `(s/map-of ~@forms :conform-keys true),
       ::s/cpred coll?,
       ::s/kind-form (quote nil)}
      nil)))

(defn keys-spec [{:keys [req opt req-un opt-un]}]
  (let [req-specs (flatten (map polish (concat req req-un)))
        opt-specs (flatten (map polish (concat opt opt-un)))
        req-keys (flatten (concat (map polish req) (map polish-un req-un)))
        opt-keys (flatten (concat (map polish opt) (map polish-un opt-un)))
        pred-exprs (concat
                     [#(map? %)]
                     (map (fn [x] #(contains? % x)) req-keys))
        pred-forms (concat
                     [`(fn [~'%] (map? ~'%))]
                     (map (fn [k] `(fn [~'%] (contains? ~'% ~k))) req-keys))
        keys-pred (fn [x]
                    (reduce
                      (fn [_ p]
                        (or (p x) (reduced false)))
                      true
                      pred-exprs))]

    (s/map-spec-impl
      {:req-un req-un
       :opt-un opt-un
       :pred-exprs pred-exprs
       :keys-pred keys-pred
       :opt-keys opt-keys
       :req-specs req-specs
       :req req
       :req-keys req-keys
       :opt-specs opt-specs
       :pred-forms pred-forms
       :opt opt})))

(defn nilable-spec [pred]
  (let [form (form/resolve-form pred)]
    (s/nilable-impl form pred nil)))
