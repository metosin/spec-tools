(ns spec-tools.swagger.spec3
  (:require [clojure.spec.alpha :as s]
   [spec-tools.core :as st]
   [clojure.spec.gen.alpha :as sgen]
   [clojure.string :as string]
   [spec-tools.data-spec :as ds]))

(defn non-empty-string-ascii
  []
  (sgen/such-that #(not= "" %)
                  (sgen/string-ascii)))

(s/def ::string-not-empty (s/with-gen
                            (s/and string? #(not= "" %))
                            non-empty-string-ascii))
(s/def ::reference
  (ds/spec
   ::reference
   {(ds/req :?ref) ::string-not-empty}))

(s/def ::example
  (ds/spec {:keys-default ds/opt
            :name ::example
            :spec
            {:summary       ::string-not-empty
             :description   ::string-not-empty
             :value         any?
             :externalValue {keyword? ::string-not-empty}}}))

(s/def ::spec qualified-keyword?)
(s/def ::json-schema-or-reference
  (s/or
   :provided (ds/spec ::json-schema
                       {(ds/opt :?schema) ::string-not-empty
                        (ds/req :type)     (s/spec #{"string"
                                                     "number"
                                                     "integer"
                                                     "array"
                                                     "object"
                                                     "boolean"
                                                     "null"})})
   :reference ::reference
   :empty-map (s/and map? empty?)
   :bool boolean?))

(s/def ::schema (s/or :spec qualified-keyword? :json-schema ::json-schema-or-reference))

(def url (re-pattern #"^(https?|ftp)://(-\.)?([^\s/?\.#-]+\.?)+(/\S*)?$"))
(s/def ::url
   (s/with-gen (s/and string? #(re-matches url %))
     #(s/gen #{"https://openapi.com"
               "ftp://random.ly"
               "https://clojuredocs.org/clojure.core/rand-nth"})))

(def email (re-pattern #"^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\.[a-zA-Z]{2,63}"))
(s/def ::email (s/with-gen (s/and string? #(re-matches email %))
                #(s/gen #{"https@openapi.com"
                          "ftp@random.ly"
                          "https@clojuredocs.org"})))

(def path (re-pattern
           #"(?<=/)((\{[^\s/?{}.#-][^\s/{}]+)+}|([^\s/?{}.#-][^\s/{}]+)+)/?"))
(s/def ::path (s/with-gen (s/and string?
                                 #(= (count (re-seq path %))
                                     (as-> (string/split % #"/") loc
                                       ((fn [x] (if (empty? (first x)) (rest x) x)) loc)
                                       (count loc))))
                          #(s/gen #{"/openapi/com"
                                    "/{random}/ly"
                                    "/clojuredocs/{org}"})))

(s/def ::external-docs
  (ds/spec
    ::external-docs
    {(ds/opt :description) ::string-not-empty
     (ds/req :url) ::url}))

(s/def ::oauth-flow
  (ds/spec {:keys-default ds/opt
            :name ::oauth-flow
            :spec {:authorizationUrl ::url
                   :tokenUrl         ::url
                   :refreshUrl       ::url
                   :scopes           {keyword? ::string-not-empty}}}))

(s/def ::oauth-flows
  (ds/spec {:keys-default ds/opt
            :name ::oauth-flows
            :spec
            {:implicit (s/and ::oauth-flow (s/keys :req-un [::authorizationUrl ::scopes]))
             :password (s/and ::oauth-flow (s/keys :req-un [::tokenUrl ::scopes]))
             :clientCredentials (s/and ::oauth-flow (s/keys :req-un [::tokenUrl ::scopes]))
             :authorizationCode
             (s/and ::oauth-flow
              (s/keys :req-un [::authorizationUrl ::tokenUrl ::scopes]))}}))

(defmulti security-scheme-type :type)
(defmethod security-scheme-type "apiKey" [_]
 (ds/spec
    ::apiKey
    {(ds/req :name) ::string-not-empty
     (ds/req :in)  (s/spec #{"query" "header" "cookie"})}))

(defmethod security-scheme-type "http" [_]
 (ds/spec ::http {(ds/req :scheme) ::string-not-empty
                  (ds/opt :bearerFormat) ::string-not-empty}))

(defmethod security-scheme-type "oauth2" [_]
 (ds/spec ::oauth2 {(ds/req :flows) ::oauth-flows}))

(defmethod security-scheme-type "openIdConnect" [_]
 (ds/spec ::openIdConnect {(ds/req :openIdConnectUrl) ::string-not-empty}))

(def auth-sample {:type "http" :scheme "basic"})
(def api-key-sample {:type "apiKey" :name "api_key" :in "header"})
(def jwt-bearer-sample {:type "http" :scheme "bearer" :bearerFormat "JWT"})
(def implicit-oauth2-sample
  {:type  "oauth2"
   :flows {:implicit {:authorizationUrl "https://example.com/api/oauth/dialog"
                      :scopes           {
                                         :write:pets "modify pets in your account"
                                         :read:pets  "read your pets"}}}})

(s/def ::security-scheme
 (s/with-gen
  (s/and
      (ds/spec
       ::security-scheme-base
       {(ds/req :type)        (s/spec #{"apiKey", "http", "oauth2", "openIdConnect"})
         (ds/opt :description) ::string-not-empty})
      (s/multi-spec security-scheme-type :type))
  #(s/gen #{auth-sample api-key-sample jwt-bearer-sample implicit-oauth2-sample})))

(s/def ::security-scheme-or-reference
 (s/or
  :security-scheme ::security-scheme
  :reference ::reference))

(s/def ::security-requirement
  (ds/spec
    ::security-requirement
    {keyword? [::string-not-empty]}))

(s/def ::boolean-default-false
  (st/spec
   {:spec                boolean?
    :json-schema/default false}))

(s/def ::example-or-reference
 (s/or
  :example ::example
  :reference ::reference))

(defmulti parameter-style :style)
(defmethod parameter-style "form" [_]
 (ds/spec ::parameter-explode-header-form
  {(ds/opt :explode) (st/spec
                      {:spec                boolean?
                       :json-schema/default true})}))
(def parameter-explode-header-default
  {(ds/opt :explode) ::boolean-default-false})
(defmethod parameter-style :default [_]
 (ds/spec ::parameter-explode-header-default
          parameter-explode-header-default))


(defmulti parameter-in :in)

(s/def ::query-parameter
 (ds/spec {:keys-default ds/opt
           :name ::query-parameter
           :spec
           {:allowEmptyValue ::boolean-default-false
            :allowReserved ::boolean-default-false
            :style (st/spec {:spec (s/spec #{"form"
                                             "spaceDelimited"
                                             "pipeDelimited"
                                             "deepObject"})
                             :json-schema/default "form"})}}))

(defmethod parameter-in "query" [_] ::query-parameter)

(defmethod parameter-in "path" [_]
  (ds/spec ::path-parameter
   {(ds/req :required) true?
    (ds/opt :style) (st/spec
                     {:spec (s/spec #{"matrix" "label" "simple"})
                      :json-schema/default "simple"})}))

(def header-parameter {(ds/opt :style) (s/spec #{"simple"})})

(defmethod parameter-in "header" [_]
  (ds/spec ::header-parameter header-parameter))

(defmethod parameter-in "cookie" [_]
  (ds/spec ::cookie-parameter {:style (s/spec #{"form"})}))

(def param-sample1 {:name        "token",
                    :in          "header",
                    :description "token to be passed as a header",
                    :required    true,
                    :schema      {:type  "array"
                                  :items {:type   "integer"
                                          :format "int64"}}
                    :style       "simple"})


(def param-sample2 {:name        "username"
                    :in          "path"
                    :description "username to fetch"
                    :required    true
                    :schema      {:type "string"}})


(def param-sample3 {:name        "id"
                    :in          "query"
                    :description "ID of the object to fetch"
                    :required    false
                    :schema      {:type  "array"
                                  :items {:type "string"}}
                    :style       "form"
                    :explode     true})

(def param-sample4 {:in     "query"
                    :name   "freeForm"
                    :schema {:type                 "object"
                             :additionalProperties {:type "integer"}}
                    :style  "form"})

(def param-sample5
  {:in      "query"
   :name    "coordinates"
   :content {"application/json"
             {:schema {:type       "object"
                       :required   ["lat" "long"]
                       :properties {:lat  {:type "number"}
                                    :long {:type "number"}}}}}})

(def param-sample #{param-sample1 param-sample2 param-sample3 param-sample4})
(def header-sample (map
                    #(apply dissoc % [:in :name :content]) param-sample))

(def header {:description ::string-not-empty
              :required ::boolean-default-false
              :deprecated ::boolean-default-false
              :example any?
              :schema ::schema
              :examples {keyword? ::example-or-reference}})

(s/def ::header-base-no-content
  (s/and (ds/spec {:keys-default ds/opt
                   :name ::header-no-content
                   :spec  (merge header
                                 header-parameter
                                 parameter-explode-header-default)})
         not-empty))

(s/def ::header-no-content
  (s/with-gen
   (s/and
      ::header-base-no-content
      #(-> % (select-keys [:name :in]) count (= 0)))
   (fn [] (s/gen ::header-base-no-content))))


(s/def ::header-no-content-or-reference (s/or
                                         :header-no-content ::header-no-content
                                         :reference ::reference))

(s/def ::encoding
 (s/and
  (ds/spec
   ::encoding
    { (ds/opt :contentType) ::string-not-empty
      (ds/opt :headers) {keyword? ::header-no-content-or-reference}})
  ::query-parameter
  (s/multi-spec parameter-style :style)))

(s/def ::media-type
 (ds/spec {:keys-default ds/opt
             :name ::media-type
             :spec  {:example any?
                     :schema ::schema
                     :examples {keyword? ::example-or-reference}
                     :encoding {keyword?  ::encoding}}}))

(s/def ::content (s/every-kv ::string-not-empty ::media-type))

(s/def ::schema-xor-content
  (s/and map?
         #(-> % (select-keys [:schema :content]) count (< 2))))

(s/def ::with-content
  (s/and (s/keys :opt-un [::content]) ::schema-xor-content))

(s/def ::header
  (s/and ::header-no-content ::with-content))

(s/def ::header-or-reference (s/or
                              :header ::header
                              :reference ::reference))

(def parameter (merge
                header
                {(ds/req :name) ::string-not-empty
                 (ds/req :in)   (s/spec #{"query" "header" "path" "cookie"})}))

(s/def ::parameter
  (s/and
   (ds/spec {:keys-default ds/opt
             :name ::parameter
             :spec  parameter})
   (s/multi-spec parameter-in :in)
   ::with-content
   (s/multi-spec  parameter-style :style)))

(s/def ::server-variable (ds/spec
                          ::server-variable
                          {(ds/opt :enum) [::string-not-empty]
                           (ds/req :default) ::string-not-empty
                           (ds/opt :description) ::string-not-empty}))

(s/def ::server (ds/spec
                 ::server
                 {(ds/req :url) ::url
                  (ds/opt :description) ::string-not-empty
                  :variables {keyword? ::server-variable}}))

(s/def ::link
  (ds/spec
    {:keys-default ds/opt
     :name ::link
     :spec {:operationRef ::string-not-empty
            :operationId ::string-not-empty
            :parameters {keyword? any?}
            :requestBody any?
            :description ::string-not-empty
            :server ::server}}))

(s/def ::link-or-reference (s/or
                              :link ::link
                              :reference ::reference))

(s/def ::request-body
  (ds/spec
    {:keys-default ds/opt
     :name ::request-body
     :spec {:description ::string-not-empty
            (ds/req :content) ::content
            :required ::boolean-default-false}}))

(s/def ::request-body-or-reference (s/or
                                    :request-body ::request-body
                                    :reference ::reference))

(s/def ::response
  (ds/spec
    {:keys-default ds/opt
     :name ::response
     :spec {(ds/req :description) ::string-not-empty
            :headers {keyword? ::header-or-reference}
            :content ::content
            :links {keyword? ::link-or-reference}}}))

(s/def ::response-or-reference (s/or
                                :response ::response
                                :reference ::reference))

(defn distinct-params?
  "Checks that all entries in a collection of parameters have distinct locations"
  [params]
  (->> params
   (into #{} (map #(select-keys % [:name :in])))
   count
   (= (count params))))

(s/def ::parameter-or-reference (s/or
                                 :parameter ::parameter
                                 :reference ::reference))
(def ref-or-param-coll (vec (conj
                             param-sample
                             param-sample5
                             {:?ref "reference"})))
(defn ref-or-param-coll-sample [] (sgen/vector-distinct
                                   (sgen/elements ref-or-param-coll)
                                   {:min-elements 3 :max-elements 5}))

(s/def
  ::distinct-parameter-or-reference-list
  (s/with-gen
    (s/and
     (s/conformer seq)
     (s/cat
      :params (s/& (s/* ::parameter) distinct-params?)
      :refs (s/* ::reference)))
   ref-or-param-coll-sample))
(s/def ::response-code
  (s/with-gen
   (s/or
    :code (s/and ::string-not-empty #(re-matches #"^[1-5](\d|[xX]){2}$" %))
    :default #(= % :default))
   #(s/gen #{"1xx" "200" "201" "204" "304" "400" "403" "404" "500" "5xx" :default})))
(s/def ::responses (s/every-kv
                     ::response-code ::response-or-reference
                     :distinct true))

(def operation-without-callbacks
   {:tags [::string-not-empty]
    :summary ::string-not-empty
    :description ::string-not-empty
    :externalDocs ::external-docs
    :operationId ::string-not-empty
    :parameters ::distinct-parameter-or-reference-list
    :requestBody ::request-body-or-reference
    (ds/req :responses) ::responses
    :deprecated ::boolean-default-false
    :security [::security-requirement]
              :servers [::server]})

(s/def ::operation-without-callbacks
  (ds/spec
    {:keys-default ds/opt
     :name ::operation-without-callbacks
     :spec operation-without-callbacks}))

(def base-path-item
   {:?ref ::string-not-empty
    :summary ::string-not-empty
    :description ::string-not-empty
    :parameters ::distinct-parameter-or-reference-list
    :server [::server]})

(def operations-keywords
  [:get :put :post :delete :options :head :patch :trace])

(def callback-operations (into {}
                               (map (fn [x] [x ::operation-without-callbacks]))
                               operations-keywords))

(s/def ::path-item-in-callback
 (ds/spec {:keys-default ds/opt
           :name ::path-item-in-callback
           :spec (merge base-path-item callback-operations)}))

(def sample-callback
 {:trace
      {:requestBody
        {:description "Callback payload"
         :content
          {"application/json"
            {:schema
              {:?ref "#/components/schemas/SomePayload"}}}}
       :responses
        {"200"
          {:description "webhook successfully processed and no retries will be performed"}}}})

(s/def ::callback
  (s/every-kv ::string-not-empty ::path-item-in-callback))

(s/def ::callback-or-reference (s/or
                                :callback ::callback
                                :reference ::reference))

(def callbacks {(ds/opt :callbacks)  {::string-not-empty ::callback-or-reference}})

(s/def ::operation
  (ds/spec
    {:keys-default ds/opt
     :name ::operation
     :spec (merge operation-without-callbacks callbacks)}))

(def path-operations (into {}
                           (map (fn [x] [x ::operation]))
                           operations-keywords))
(s/def ::path-item
  (ds/spec {:keys-default ds/opt
            :name ::path-item
            :spec (merge base-path-item path-operations)}))
(s/def ::open-api
  (ds/spec
    {:keys-default ds/opt
     :name ::open-api
     :spec {(ds/req :openapi) #(re-matches #"(?is)(3\.0\.)(\d)+(-rc[0-2])?$" %)
            (ds/req :info) {(ds/req :title) ::string-not-empty
                              :description ::string-not-empty
                              :termsOfService ::string-not-empty
                              :contact { :name ::string-not-empty
                                                 :url ::url
                                                 :email ::email}
                              :license {(ds/req :name) ::string-not-empty
                                                 :url ::string-not-empty}
                             (ds/req :version) ::string-not-empty}
            :servers (st/spec {:spec (s/every ::server)
                               :json-schema/default [{:url "/"}]})
            (ds/req :paths) (s/every-kv ::path ::path-item)
            :components {:schemas {keyword? ::schema}
                         :responses {keyword? ::response-or-reference}
                         :parameters {keyword? ::parameter-or-reference}
                         :examples {keyword? ::example-or-reference}
                         :requestBodies {keyword? ::request-body-or-reference}
                         :headers {keyword? ::header-or-reference}
                         :securitySchemes {keyword? ::security-scheme-or-reference}
                         :links {keyword? ::link-or-reference}
                         :callbacks {keyword? ::callback-or-reference}}
            :security [::security-requirement]
            :tags [{:name ::string-not-empty
                             :description ::string-not-empty
                             :externalDocs ::external-docs}]
            :externalDocs ::external-docs}}))


