;; ESTree Spec
;; https://github.com/estree/estree

(ns lambda-view.javascript
  (:require [lambda-view.utils :as utils]
            [reagent.core :as reagent])
  (:use [lambda-view.common :only [js-keyword
                                   white-space
                                   comma
                                   colon
                                   equal
                                   semicolon
                                   asterisk
                                   white-space-optional
                                   pair
                                   brackets
                                   parenthese
                                   braces
                                   box
                                   collapsed
                                   collapsable-box
                                   toggle-collapse
                                   toggle-layout-element
                                   operator]]
        [lambda-view.tag :only [mark-id!
                                id-of]]
        [lambda-view.state :only [init-collapse!
                                  get-collapse
                                  set-collapse!
                                  init-layout!
                                  get-layout
                                  set-layout!
                                  toggle-layout!]]))

(declare type-render)

(defn node-render-not-found [node]
  [:div (str "Node render not found of type: " (get node "type"))])

(defn node-is-nil []
  ;[:div "Node is nil"]
  nil)

(defn render-for-node [node]
  (if (nil? node) node-is-nil (let [type (get node "type")
                                    render (get type-render type)]
                                (if (nil? render) node-render-not-found render))))

(defn render-node [node]
  (mark-id! node)
  [(render-for-node node) node])

(defn render-node-coll [nodes]
  (map render-node nodes))

(defn render-exp-node [exp-node parent-exp-node]
  ; TODO Compare Priority...
  (println "render-exp-node" exp-node parent-exp-node)
  (if (or (nil? parent-exp-node)
          (= "Identifier" (get exp-node "type"))) (render-node exp-node)
                                                  (let [id (id-of exp-node)]
                                                    (init-collapse! id false)
                                                    (init-layout! id "horizontal")
                                                    (collapsable-box {:id    id
                                                                      :style :mini} (render-node exp-node)))))

(defn common-list [attr coll]
  (if (nil? coll) nil
                  (let [id (:id attr)
                        sep (cond
                              (= (:sep attr) :comma) (comma)
                              true (comma))
                        tail-idx (- (count coll) 1)]
                    (init-layout! id (if (> tail-idx 4) "vertical" "horizontal"))
                    (map-indexed (fn [idx e] [:div.box-element
                                              (render-node e)
                                              (if (not= idx tail-idx) (list (toggle-layout-element id sep)
                                                                            (white-space-optional)))])
                                 coll))))

(defn common-exp-list [attr coll parent]
  (if (nil? coll) nil
                  (let [id (:id attr)
                        sep (cond
                              (= (:sep attr) :comma) (comma)
                              true (comma))
                        tail-idx (- (count coll) 1)]
                    (init-layout! id (if (> tail-idx 4) "vertical" "horizontal"))
                    (map-indexed (fn [idx e] ^{:key (id-of e)} [:div.box-element
                                                                (render-exp-node e parent)
                                                                (if (not= idx tail-idx) (list ^{:key :a} (toggle-layout-element id sep)
                                                                                              ^{:key :b} (white-space-optional)))])
                                 coll))))

;; EmptyStatement
(defn empty-statement-render [_node]
  [:div {:class "empty statement"} ";"])

;; DebuggerStatement
(defn debugger-statement-render [_node]
  [:div {:class "debugger statement"} "debugger"])

;; ReturnStatement
(defn return-statement-render [node]
  (let [argument (get node "argument")]
    (if (nil? argument)
      [:div {:class "return statement"} (js-keyword "return")]
      [:div {:class "return statement"} (js-keyword "return") (white-space) (render-node argument)])))

;; BlockStatement
(defn block-statement-render [node]
  (let [id (id-of node)
        body (get node "body")]
    (init-collapse! id true)
    [:div.block.statement
     [collapsable-box {:id   id
                       :pair :brace} (render-node-coll body)]]))

;; BreakStatement
(defn break-statement-render [_node]
  [:div {:class "break statement"} "break"])

;; ContinueStatement
(defn continue-statement-render [_node]
  [:div {:class "continue statement"} "continue"])

;; LabeledStatement
(defn labeled-statement-render [node]
  (let [label (get node "label")
        body (get node "body")]
    [:div {:class "labeled statement"}
     [:div {:class "label"} (render-node label)]
     (colon)
     (white-space-optional)
     [:div {:class "body"} (render-node body)]]))

;; ImportDeclaration
(defn import-declaration-render [node]
  (let [id (id-of node)
        specifiers (get node "specifiers")
        source (get node "source")]
    (init-collapse! id false)
    (init-layout! id "horizontal")
    [:div {:class "import declaration"}
     (js-keyword "import")
     (if (> (count specifiers) 0) (list (white-space)
                                        ;; see reference https://www.ecma-international.org/ecma-262/9.0/index.html#prod-ImportClause
                                        ;; there are 5 possible scenarios
                                        ;; 1. [ImportDefaultSpecifier]                              (import a from "module")
                                        ;; 2. [ImportNamespaceSpecifier]                            (import * as a from "module")
                                        ;; 3. [ImportSpecifier...]                                  (import { a } from "moodule")
                                        ;; 4. [ImportDefaultSpecifier, ImportNamespaceSpecifier]    (import a, * as b from "module")
                                        ;; 5. [ImportDefaultSpecifier, ImportSpecifier...]          (import a, { b } from "module")
                                        (let [first-sp (first specifiers)
                                              first-sp-type (get first-sp "type")
                                              first-sp-only (= 1 (count specifiers))
                                              second-sp (second specifiers)
                                              second-sp-type (get second-sp "type")
                                              rest-sps (rest specifiers)
                                              render-list (fn [import-specifier-list] (collapsable-box {:id   id
                                                                                                        :pair :brace} (common-list {:id id} import-specifier-list)))]
                                          (cond
                                            ;; case 1
                                            (and first-sp-only
                                                 (= first-sp-type "ImportDefaultSpecifier")) (render-node first-sp)
                                            ;; case 2
                                            (and first-sp-only
                                                 (= first-sp-type "ImportNamespaceSpecifier")) (render-node first-sp)
                                            ;; case 3
                                            (and (not first-sp-only)
                                                 (= first-sp-type "ImportSpecifier")) (render-list specifiers)
                                            ;; case 4
                                            (and (= (count specifiers))
                                                 (= first-sp-type "ImportDefaultSpecifier")
                                                 (= second-sp-type "ImportNamespaceSpecifier")) (list (render-node first-sp)
                                                                                                      (comma)
                                                                                                      (white-space-optional)
                                                                                                      (render-node second-sp))
                                            ;; case 5
                                            (and (> (count specifiers) 1)
                                                 (= first-sp-type "ImportDefaultSpecifier")
                                                 (every? #(= (get %1 "type") "ImportSpecifier") rest-sps)) (list (render-node first-sp)
                                                                                                                 (comma)
                                                                                                                 (white-space-optional)
                                                                                                                 (render-list rest-sps))
                                            ;; UNKNOWN
                                            true (str "Unhandled case: " specifiers)))
                                        (white-space)
                                        (js-keyword "from")))
     (white-space)
     (render-node source)]))

;; ImportDefaultSpecifier
(defn import-default-specifier-render [node]
  [:div {:class "import-default-specifier"}
   (render-node (get node "local"))])

;; ImportSpecifier
(defn import-specifier-render [node]
  [:div {:class "import-specifier"}
   (let [imported (get node "imported")
         local (get node "local")]
     (if (= imported local) (render-node imported)
                            (list
                              (render-node imported)
                              (white-space)
                              (js-keyword "as")
                              (white-space)
                              (render-node local))))])

;; ImportNamespaceSpecifier
(defn import-namespace-specifier-render [node]
  [:div {:class "import-namespace-specifier"}
   "*" (white-space) (js-keyword "as") (white-space) (render-node (get node "local"))])

;; ExportDefaultDeclaration
(defn export-default-declaration-render [node]
  [:div {:class "export-default declaration"}
   (js-keyword "export") (white-space) (js-keyword "default") (white-space) (render-node (get node "declaration"))])

;; ExpressionStatement
(defn expression-statement-render [node]
  (let [expression (get node "expression")
        directive (get node "directive")]
    [:div {:class "expression statement"} (render-node expression)]))

;; ThrowStatement
(defn throw-statement-render [node]
  (let [argument (get node "argument")]
    [:div {:class "throw statement"}
     (js-keyword "throw") (white-space) (render-node argument)]))

;; WhileStatement
(defn while-statement-render [node]
  (let [id (id-of node)
        test (get node "test")
        test-id (str id ".test")
        body (get node "body")]
    (init-collapse! test-id false)
    [:div {:class "while statement"}
     (js-keyword "while")
     (white-space-optional)
     (collapsable-box {:id   test-id
                       :pair :parenthesis} (render-node test))
     (white-space-optional)
     ((render-for-node body) body)]))

;; DoWhileStatement
(defn do-while-statement-render [node]
  (let [id (id-of node)
        body (get node "body")
        test (get node "test")
        test-id (str id ".test")]
    (init-collapse! test-id false)
    [:div {:class "do-while statement"}
     (js-keyword "do")
     (white-space-optional)
     (render-node body)
     (white-space-optional)
     (js-keyword "while")
     (white-space-optional)
     (collapsable-box {:id   test-id
                       :pair :parenthesis} (render-node test))]))

;; IfStatement
(defn if-statement-render [node]
  (let [id (id-of node)
        test (get node "test")
        test-id (str id ".test")
        consequent (get node "consequent")
        alternate (get node "alternate")]
    (init-collapse! test-id false)
    [:div {:class "if statement"}
     (js-keyword "if") (white-space-optional) (collapsable-box {:id   test-id
                                                                :pair :parenthesis} (render-node test)) (white-space-optional)
     (render-node consequent)
     (if (nil? alternate)
       nil
       (list (white-space-optional) (js-keyword "else") (white-space-optional) (render-node alternate)))]))

;; TryStatement
(defn try-statement-render [node]
  (let [block (get node "block")
        handler (get node "handler")
        finalizer (get node "finalizer")]
    [:div {:class "try statement"}
     (js-keyword "try") (white-space-optional) (render-node block)
     (if-not (nil? handler) (list (white-space-optional) (render-node handler)))
     (if-not (nil? finalizer) (list (white-space-optional) (js-keyword "finally") (white-space-optional) (render-node finalizer)))]))

;; CatchClause
(defn catch-clause-render [node]
  (let [id (id-of node)
        param (get node "param")
        param-id (str id ".param")
        body (get node "body")]
    (init-collapse! param-id false)
    [:div {:class "catch-clause"}
     (js-keyword "catch")
     (white-space-optional)
     (if-not (nil? param) (list (collapsable-box {:id   param-id
                                                  :pair :parenthesis} (render-node param)) (white-space-optional)))
     (render-node body)]))

;; WithStatement
(defn with-statement-render [node]
  (let [id (id-of node)
        object (get node "object")
        object-id (str id ".objectt")
        body (get node "body")]
    (init-collapse! object-id false)
    [:div {:class "with statement"}
     (js-keyword "with")
     (white-space-optional)
     (collapsable-box {:id   object-id
                       :pair :parenthesis} (render-node object))
     (white-space-optional)
     (render-node body)]))

;; VariableDeclaration
(defn variable-declaration-render [node]
  (let [kind (get node "kind")
        declarations (get node "declarations")]
    [:div {:class "variable declaration"}
     (js-keyword kind)
     (white-space)
     (utils/join (render-node-coll declarations) (list (comma) (white-space-optional)))]))

;; VariableDeclarator
(defn variable-declarator-render [node]
  (let [id (get node "id")
        init (get node "init")]
    [:div {:class "variable-declarator"}
     (render-node id)
     (if-not (nil? init) (list (white-space-optional)
                               (equal)
                               (white-space-optional)
                               (render-node init)))]))

;; ForStatement
(defn for-statement-render [node]
  (let [id (id-of node)
        init (get node "init")
        test (get node "test")
        update (get node "update")
        body (get node "body")]
    (init-collapse! id false)
    [:div {:class "for statement"}
     (js-keyword "for")
     (white-space-optional)
     (collapsable-box {:id   id
                       :pair :parenthesis} (list (render-node init)
                                                 (semicolon) (white-space-optional)
                                                 (render-node test)
                                                 (semicolon) (white-space-optional)
                                                 (render-node update)))
     (white-space-optional)
     (render-node body)]))

;; ForOfStatement
(defn for-of-statement-render [node]
  (let [id (id-of node)
        left (get node "left")
        right (get node "right")
        body (get node "body")]
    (init-collapse! id false)
    [:div {:class "for-of statement"}
     (js-keyword "for")
     (white-space-optional)
     (collapsable-box {:id   id
                       :pair :parenthesis} (list (render-node left)
                                                 (white-space) (js-keyword "of") (white-space)
                                                 (render-node right)))
     (white-space-optional)
     (render-node body)]))

;; FunctionDeclaration
(defn function-declaration-render [node]
  (let [generator (get node "generator")
        ;; [NOTE]
        ;; expression flag is such an old thing which is not supported anymore
        ;; check here: https://github.com/estree/estree/blob/master/deprecated.md#functions
        ;;             https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Operators/Expression_Closures
        ;; expression (get node "expression")
        async (get node "async")
        id (get node "id")
        params (get node "params")
        params-id (str (id-of node) ".params")
        params-tail-idx (- (count params) 1)
        body (get node "body")]
    (init-collapse! params-id false)
    (init-layout! params-id (if (> params-tail-idx 4) "vertical" "horizontal"))
    [:div {:class "function declaration"}
     (if async (list (js-keyword "async")
                     (white-space)))
     (js-keyword "function")
     (white-space)
     (if generator (list (asterisk)
                         (white-space)))
     (if-not (nil? id) (list (render-node id)
                             (white-space-optional)))
     (collapsable-box {:id params-id} (common-list {:id params-id} params))
     (white-space-optional)
     (render-node body)]))

;; ClassDeclaration
(defn class-declaration-render [node]
  (let [id (get node "id")
        super-class (get node "superClass")
        body (get node "body")]
    [:div {:class "class declaration"}
     (js-keyword "class")
     (white-space)
     (render-node id)
     (if-not (nil? super-class) (list (white-space)
                                      (js-keyword "extends")
                                      (white-space)
                                      (render-node super-class)
                                      (white-space-optional)))
     (white-space-optional)
     (render-node body)]))

;; ClassBody
(defn class-body-render [node]
  (let [id (id-of node)
        body (get node "body")]
    (init-collapse! id true)
    [:div {:class "class-body"}
     (collapsable-box {:id   id
                       :pair :brace} (render-node-coll body))]))

;; MethodDefinition
(defn method-definition-render [node]
  (let [kind (get node "kind")
        static (get node "static")
        computed (get node "computed")
        key (get node "key")
        value (get node "value")]
    [:div {:class "method-definition"}
     (if static (list (js-keyword "static") (white-space)))
     (cond
       ;; Constructor or Method
       (or (= kind "method")
           (= kind "constructor")) (let [fn-exp-node value
                                         _id (get fn-exp-node "id") ; always nil
                                         generator (get fn-exp-node "generator")
                                         _expression (get fn-exp-node "expression") ;; useless
                                         async (get fn-exp-node "async")
                                         params (get fn-exp-node "params")
                                         params-id (str (id-of node) ".params")
                                         body (get fn-exp-node "body")]
                                     (init-collapse! params-id false)
                                     (list (if async (list (js-keyword "async") ;; optional?
                                                           (white-space)))
                                           (if generator (list (asterisk)
                                                               (white-space))) ;; optional?
                                           (render-node key)
                                           (white-space-optional)
                                           (collapsable-box {:id params-id} (common-list {:id params-id} params))
                                           (white-space-optional)
                                           (render-node body)))
       (or (= kind "get")
           (= kind "set")) (let [fn-exp-node value
                                 params (get fn-exp-node "params")
                                 params-id (str (id-of node) ".params")
                                 body (get fn-exp-node "body")]
                             (init-collapse! params-id false)
                             (list (js-keyword kind)
                                   (white-space)
                                   (render-node key)
                                   (white-space-optional)
                                   (collapsable-box {:id params-id} (common-list {:id params-id} params))
                                   (white-space-optional)
                                   (render-node body))))]))

;; SwitchStatement
(defn switch-statement-render [node]
  (let [id (id-of node)
        discriminant (get node "discriminant")
        cases (get node "cases")
        cases-id (str id ".cases")]
    (init-collapse! id false)
    (init-collapse! cases-id true)
    [:div {:class "switch statement"}
     (js-keyword "switch")
     (white-space-optional)
     (collapsable-box {:id id} (render-node discriminant))
     (white-space-optional)
     [collapsable-box {:id   cases-id
                       :pair :brace} (render-node-coll cases)]]))

;; SwitchCase
(defn switch-case-render [node]
  (let [consequent (get node "consequent")
        test (get node "test")]
    [:div {:class "switch-case"}
     [:div {:class "test"} (if (nil? test) (list (js-keyword "default")
                                                 (colon))
                                           (list (js-keyword "case")
                                                 (white-space)
                                                 (render-node test)
                                                 (colon)))]
     (white-space-optional)
     [:div {:class "consequent"} (render-node-coll consequent)]]))

;; Identifier
(defn identifier-render [node]
  [:div {:class "identifier"} (get node "name")])

;; Literal
(defn literal-render [node]
  [:div {:class "literal"} (get node "raw")])

;; Program
(defn program-render [node]
  [:div
   {:class "program"}
   (let [body (get node "body")]
     (if-not (nil? body) (render-node-coll body)))])

;; ArrayExpression
(defn array-expression-render [node]
  (let [id (id-of node)
        elements (get node "elements")]
    (init-collapse! id true)
    [:div.array.expression
     [collapsable-box {:id   id
                       :pair :bracket} (common-list {:id id} elements)]]))

;; AssignmentExpression
(defn assignment-expression-render [node]
  (let [op (get node "operator")
        left (get node "left")
        right (get node "right")]
    [:div.assignment.expression
     (render-exp-node left node)
     (white-space-optional)
     (operator op)
     (white-space-optional)
     (render-exp-node right node)]))

;; BinaryExpression
(defn binary-expression-render [node]
  (let [op (get node "operator")
        left (get node "left")
        right (get node "right")]
    [:div.binary.expression
     (render-exp-node left node)
     (white-space-optional)
     (operator op)
     (white-space-optional)
     (render-exp-node right node)]))

;; ThisExpression
(defn this-expression-render [_node]
  [:div.this.expression (js-keyword "this")])

;; SequenceExpression
(defn sequence-expression-render [node]
  (let [id (id-of node)
        expressions (get node "expressions")]
    (println "sequence-expression-render" id)
    [:div.sequence.expression (common-exp-list {:id id} expressions node)]))

;; Map node type to render function
(def type-render {"Program"                  program-render
                  "EmptyStatement"           empty-statement-render
                  "DebuggerStatement"        debugger-statement-render
                  "ReturnStatement"          return-statement-render
                  "BlockStatement"           block-statement-render
                  "BreakStatement"           break-statement-render
                  "ContinueStatement"        continue-statement-render
                  "LabeledStatement"         labeled-statement-render
                  "ExpressionStatement"      expression-statement-render
                  "ThrowStatement"           throw-statement-render
                  "WhileStatement"           while-statement-render
                  "DoWhileStatement"         do-while-statement-render
                  "IfStatement"              if-statement-render
                  "ImportDeclaration"        import-declaration-render
                  "ImportDefaultSpecifier"   import-default-specifier-render
                  "ImportNamespaceSpecifier" import-namespace-specifier-render
                  "ImportSpecifier"          import-specifier-render
                  "ExportDefaultDeclaration" export-default-declaration-render
                  "TryStatement"             try-statement-render
                  "WithStatement"            with-statement-render
                  "VariableDeclaration"      variable-declaration-render
                  "VariableDeclarator"       variable-declarator-render
                  "CatchClause"              catch-clause-render
                  "ForStatement"             for-statement-render
                  "ForOfStatement"           for-of-statement-render
                  "FunctionDeclaration"      function-declaration-render
                  "ClassDeclaration"         class-declaration-render
                  "ClassBody"                class-body-render
                  "MethodDefinition"         method-definition-render
                  "SwitchStatement"          switch-statement-render
                  "SwitchCase"               switch-case-render
                  "ArrayExpression"          array-expression-render
                  "Identifier"               identifier-render
                  "Literal"                  literal-render
                  ;; Expressions
                  "AssignmentExpression"     assignment-expression-render
                  "BinaryExpression"         binary-expression-render
                  "SequenceExpression"       sequence-expression-render
                  "ThisExpression"           this-expression-render})

(defn ast-render [ast]
  (render-node ast))