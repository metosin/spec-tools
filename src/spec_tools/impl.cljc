(ns spec-tools.impl
  (:refer-clojure :exclude [resolve])
  #?(:cljs (:require-macros [spec-tools.impl :refer [resolve]]))
  (:require
    #?(:cljs [cljs.analyzer.api])
    [clojure.spec.alpha :as s]
    [spec-tools.form :as form]
    [clojure.walk :as walk])
  (:import
    #?@(:clj
        [(clojure.lang Var)])))

#?(:clj
   (defn in-cljs? [env]
     (:ns env)))

;; ClojureScript 1.9.655 and later have a resolve macro - maybe this can be
;; eventually converted to use it.
#?(:clj
   (defmacro resolve
     [env sym]
     `(if (in-cljs? ~env)
        ((clojure.core/resolve 'cljs.analyzer.api/resolve) ~env ~sym)
        (clojure.core/resolve ~env ~sym))))

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

(defn- unfn [cljs? expr]
  (if (clojure.core/and (seq? expr)
                        (symbol? (first expr))
                        (= "fn*" (name (first expr))))
    (let [[[s] & form] (rest expr)]
      (conj (walk/postwalk-replace {s '%} form) '[%] (if cljs? 'cljs.core/fn 'clojure.core/fn)))
    expr))

#?(:clj
   (defn cljs-resolve [env symbol]
     (clojure.core/or (->> symbol (resolve env) cljs-sym) symbol)))

(defn polish [x]
  (cond
    (seq? x) (flatten (keep polish x))
    (symbol? x) nil
    :else x))

(defn polish-un [x]
  (some-> x polish name keyword))

(defn un-key [x]
  (some-> x name keyword))

(defn with-key->spec [{:keys [req req-un opt opt-un] :as data}]
  (let [key->spec (->> (concat opt req) (map (juxt identity identity)) (into {}))
        un-key->spec (->> (concat opt-un req-un) (map (juxt un-key identity)) (into {}))]
    (assoc data :key->spec (merge key->spec un-key->spec))))

(defn with-real-keys [{:keys [req-un opt-un] :as data}]
  (cond-> data
          req-un (update :req-un (partial mapv un-key))
          opt-un (update :opt-un (partial mapv un-key))))

(defn parse-keys [form]
  (let [m (some->> form (rest) (apply hash-map))]
    (cond-> m
            (:req m) (update :req #(->> % flatten (keep polish) (into [])))
            (:req-un m) (update :req-un #(->> % flatten (keep polish) (into [])))
            (:opt-un m) (update :opt-un #(->> % (keep polish) (into [])))
            true (-> with-key->spec with-real-keys))))

(defn extract-keys [form]
  (let [{:keys [req opt req-un opt-un]} (some->> form (rest) (apply hash-map))]
    (flatten (map polish (concat req opt req-un opt-un)))))

#?(:clj
   (defn resolve-form [env pred]
     (let [cljs? (in-cljs? env)
           res (if cljs? (partial cljs-resolve env) clojure.core/resolve)]
       (->> pred
            (walk/postwalk
              (fn [x]
                (if (symbol? x)
                  (or (some->> x res ->sym) x)
                  x)))
            (unfn cljs?)))))

(defn extract-pred-and-info [x]
  (if (map? x)
    [(:spec x) (dissoc x :spec)]
    [x {}]))

(defn strip-fn-if-needed [form]
  (let [head (first form)]
    ;; Deal with the form (clojure.core/fn [%] (foo ... %))
    ;; We should just use core.match...
    (if (and (= (count form) 3) (= head #?(:clj 'clojure.core/fn :cljs 'cljs.core/fn)))
      (nth form 2)
      form)))

(defn normalize-symbol [kw]
  (case (and (symbol? kw) (namespace kw))
    "spec-tools.spec" (symbol "clojure.core" (name kw))
    "cljs.core" (symbol "clojure.core" (name kw))
    "cljs.spec.alpha" (symbol "clojure.spec.alpha" (name kw))
    kw))

(defn extract-form [spec]
  (if (seq? spec) spec (s/form spec)))

(defn qualified-name [key]
  (if (keyword? key)
    (if-let [nn (namespace key)]
      (str nn "/" (name key))
      (name key))
    key))

(defn nilable-spec? [spec]
  (let [form (and spec (s/form spec))]
    (boolean
      (if (seq? form)
        (some-> form
                seq
                first
                #{'clojure.spec.alpha/nilable
                  'cljs.spec.alpha/nilable})))))

(defn unwrap
  "Unwrap [x] to x. Asserts that coll has exactly one element."
  [coll]
  {:pre [(= 1 (count coll))]}
  (first coll))

(defn deep-merge [& values]
  (cond
    (every? map? values)
    (apply merge-with deep-merge values)

    (every? coll? values)
    (reduce into values)

    :else
    (last values)))

(defn unlift-keys [data ns-name]
  (reduce
    (fn [acc [k v]]
      (if (= ns-name (namespace k))
        (assoc acc (keyword (name k)) v)
        acc))
    {} data))

;;
;; FIXME: using ^:skip-wiki functions from clojure.spec. might break.
;;

(defn register-spec! [k s]
  (s/def-impl k (s/form s) s))

;;
;; functional clojure.spec
;;

(defn coll-of-spec [pred type]
  (let [form (form/resolve-form pred)]
    (clojure.spec.alpha/every-impl
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
    (clojure.spec.alpha/every-impl
      `(s/tuple ~@forms)
      tuple
      {:into {}
       :conform-keys true
       ::s/kfn (fn [_ v] (nth v 0))
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
                        (clojure.core/or (p x) (reduced false)))
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

(defn or-spec [v]
  (let [ks (mapv first v)
        preds (mapv second v)
        forms (mapv form/resolve-form preds)]
    (s/or-spec-impl ks forms preds nil)))
