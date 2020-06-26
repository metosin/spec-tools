(ns spec-tools.openapi.spec
  (:require [clojure.spec.alpha :as s]
            [spec-tools.data-spec :as ds]))

(s/def ::contact
  (ds/spec
   ::contact
   {(ds/opt :name)  string?
    (ds/opt :url)   string?
    (ds/opt :email) string?}))

(s/def ::license
  (ds/spec
   ::license
   {(ds/req :name) string?
    (ds/opt :url)  string?}))

(s/def ::info
  (ds/spec
   ::info
   {(ds/req :title)          string?
    (ds/opt :description)    string?
    (ds/opt :termsOfService) string?
    (ds/opt :contact)        ::contact
    (ds/opt :license)        ::license
    (ds/req :version)        string?}))

(s/def ::server-variable
  (ds/spec
   ::server-variable
   {(ds/opt :enum)        [string?]
    (ds/req :default)     string?
    (ds/opt :description) string?}))

(s/def ::server
  (ds/spec
   ::server
   {(ds/req :url)         string?
    (ds/opt :description) string?
    (ds/opt :variables)   {string? ::server-variable}}))

(s/def ::external-documentation
  (ds/spec
   ::external-documentation
   {(ds/opt :description) string?
    (ds/req :url)         string?}))

;; FIXME: Not sure about this
(s/def ::schema (s/map-of
                 (s/or :kw keyword? :str string?)
                 (s/or :kw keyword? :str string?)))

(s/def ::example
  (ds/spec
   ::example
   {(ds/opt :summary)       string?
    (ds/opt :description)   string?
    (ds/opt :value)         any?
    (ds/opt :externalValue) string?}))

(s/def ::header
  (ds/spec
   ::header
   {(ds/opt :description)     string?
    (ds/req :required)        (s/and boolean? true?)
    (ds/opt :deprecated)      boolean?
    (ds/opt :allowEmptyValue) boolean?
    (ds/opt :style)           (s/spec #{"simple"})
    (ds/opt :explode)         (s/and boolean? false?)
    (ds/opt :schema)          ::schema
    (ds/opt :example)         any?
    (ds/opt :examples)        {string? ::example}}))

(s/def ::encoding
  (ds/spec
   ::encoding
   {(ds/opt :contentType)   string?
    (ds/opt :headers)       {string? ::header}
    (ds/opt :style)         (s/spec #{"form"
                                      "spaceDelimited"
                                      "pipeDelimited"
                                      "deepObject"})
    (ds/opt :explode)       boolean?
    (ds/opt :allowReserved) boolean?}))

(s/def ::media-object
  (ds/spec
   ::media-object
   {(ds/opt :schema)   ::schema
    (ds/opt :example)  any?
    (ds/opt :examples) {string? ::example}
    (ds/opt :encoding) {string? ::encoding}}))

(s/def ::parameter-path
  (ds/spec
   ::parameter
   {(ds/req :name)            string?
    (ds/req :in)              (s/spec #{"path"})
    (ds/opt :description)     string?
    (ds/req :required)        (s/and boolean? true?)
    (ds/opt :deprecated)      boolean?
    (ds/opt :allowEmptyValue) boolean?
    (ds/opt :style)           (s/spec #{"matrix"
                                        "label"
                                        "form"
                                        "simple"
                                        "spaceDelimited"
                                        "pipeDelimited"
                                        "deepObject"})
    (ds/opt :explode)         boolean?
    (ds/opt :allowReserved)   boolean?
    (ds/opt :schema)          ::schema
    (ds/opt :example)         any?
    (ds/opt :examples)        {string? ::example}
    (ds/opt :content)         {string? ::media-object}}))

(s/def ::parameter-other
  (ds/spec
   ::parameter-other
   {(ds/req :name)            string?
    (ds/req :in)              (s/spec #{"query" "header" "cookie"})
    (ds/opt :description)     string?
    (ds/opt :required)        boolean?
    (ds/opt :deprecated)      boolean?
    (ds/opt :allowEmptyValue) boolean?
    (ds/opt :style)           (s/spec #{"matrix"
                                        "label"
                                        "form"
                                        "simple"
                                        "spaceDelimited"
                                        "pipeDelimited"
                                        "deepObject"})
    (ds/opt :explode)         boolean?
    (ds/opt :allowReserved)   boolean?
    (ds/opt :schema)          ::schema
    (ds/opt :example)         any?
    (ds/opt :examples)        {string? ::example}
    (ds/opt :content)         {string? ::media-object}}))

(s/def ::parameter
  (s/or :path-parameter  ::parameter-path
        :other-parameter ::parameter-other))

(s/def ::request-body
  (ds/spec
   ::request-body
   {(ds/opt :description) string?
    (ds/req :content)     {string? ::media-object}
    (ds/opt :required)    boolean?}))

(s/def ::link
  (ds/spec
   ::link
   {(ds/opt :operationRef) string?
    (ds/opt :operationId)  string?
    (ds/opt :parameters)   {string? any?}
    (ds/opt :requestBody)  any?
    (ds/opt :description)  string?
    (ds/opt :server)       ::server}))

(s/def ::response-code (s/or :code (s/int-in 100 600)
                             :default #{:default}))

(s/def ::response
  (ds/spec
   ::response
   {(ds/req :description) string?
    (ds/opt :headers)     {string? ::header}
    (ds/opt :content)     {string? ::media-object}
    (ds/opt :links)       {string? ::link}}))

(s/def ::operation
  (ds/spec
   ::operation
   {(ds/opt :tags)         [string?]
    (ds/opt :summary)      string?
    (ds/opt :description)  string?
    (ds/opt :externalDocs) ::external-documentation
    (ds/opt :operationId)  string?
    (ds/opt :parameters)   [::parameter]
    (ds/opt :requestBody)  ::request-body
    (ds/req :responses)    ::response
    ;; (ds/opt :callbacks)    {string? ::callback}
    (ds/opt :deprecated)   boolean?
    (ds/opt :security)     {string? [string?]}
    (ds/opt :servers)      [::server]}))

(s/def ::path
  (ds/spec
   ::path
   {(ds/opt :summary)     string?
    (ds/opt :description) string?
    (ds/opt :get)         ::operation
    (ds/opt :put)         ::operation
    (ds/opt :post)        ::operation
    (ds/opt :delete)      ::operation
    (ds/opt :options)     ::operation
    (ds/opt :head)        ::operation
    (ds/opt :patch)       ::operation
    (ds/opt :servers)     [::server]
    (ds/opt :parameters)  any?}))

(s/def ::callback
  (ds/spec
   ::callback
   {string? ::path}))

(s/def ::tag
  (ds/spec
   ::tag
   {(ds/req :name)         string?
    (ds/opt :description)  string?
    (ds/opt :externalDocs) ::external-documentation}))

(s/def ::security-scheme-api-key
  (ds/spec
   ::security-scheme-api-key
   {(ds/req :type)        (s/spec #{"apiKey"})
    (ds/opt :description) string?
    (ds/req :name)        string?
    (ds/req :in)          (s/spec #{"query" "header" "cookie"})}))

(s/def ::security-scheme-http
  (ds/spec
   ::security-scheme-http
   {(ds/req :type)         (s/spec #{"http"})
    (ds/opt :description)  string?
    (ds/req :scheme)       string?
    (ds/req :bearerFormat) string?}))

(s/def ::security-scheme-oauth2
  (ds/spec
   ::security-scheme-oauth2
   {(ds/req :type)        (s/spec #{"oauth2"})
    (ds/opt :description) string?
    (ds/req :flows)
    {(ds/opt :implicit)          {(ds/req :authorizationUrl) string?
                                  (ds/opt :refreshUrl)       string?
                                  (ds/req :scopes)           {string? string?}}
     (ds/opt :password)          {(ds/req :tokenUrl)   string?
                                  (ds/opt :refreshUrl) string?
                                  (ds/req :scopes)     {string? string?}}
     (ds/opt :clientCredentials) {(ds/req :tokenUrl)   string?
                                  (ds/opt :refreshUrl) string?
                                  (ds/req :scopes)     {string? string?}}
     (ds/opt :authorizationCode) {(ds/req :authorizationUrl) string?
                                  (ds/req :tokenUrl)         string?
                                  (ds/opt :refreshUrl)       string?
                                  (ds/req :scopes)           {string? string?}}}}))

(s/def ::security-scheme
  (s/or :api-key ::security-scheme-api-key
        :http    ::security-scheme-http
        :oauth2  ::security-scheme-oauth2))

(s/def ::components
  (ds/spec
   ::components
   {(ds/opt :schemas)         {string? ::schema}
    (ds/opt :responses)       {string? ::response}
    (ds/opt :parameters)      {string? ::parameter}
    (ds/opt :examples)        {string? ::example}
    (ds/opt :requestBodies)   {string? ::request-body}
    (ds/opt :headers)         {string? ::header}
    (ds/opt :securitySchemes) {string? ::security-scheme}
    (ds/opt :links)           {string? ::link}
    (ds/opt :callbacks)       {string? ::callback}}))

(s/def ::openapi
  (ds/spec
   ::openapi
   {(ds/req :openapi)      (s/and string? #(re-matches #"^3\.\d\.\d$" %))
    (ds/req :info)         ::info
    (ds/opt :servers)      [::server]
    (ds/req :paths)        {string? ::path}
    (ds/opt :components)   ::components
    (ds/opt :security)     {string? [string?]}
    (ds/opt :tags)         [::tag]
    (ds/opt :externalDocs) ::external-documentation}))
