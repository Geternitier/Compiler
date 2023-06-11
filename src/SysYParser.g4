parser grammar SysYParser;

options {
    tokenVocab = SysYLexer;
}

program
   : compUnit
   ;

compUnit
   : (funcDef | decl | stmt)+ EOF
   ;

decl
   : (constDecl | varDecl)
   ;

constDecl
   : CONST bType constDef (COMMA constDef)* SEMICOLON
   ;

bType
   : INT
   ;

constDef
   : IDENT (L_BRACKT constExp R_BRACKT)* ASSIGN constInitVal
   ;

constInitVal
   : constExp
   | L_BRACE (constInitVal (COMMA constInitVal)*)? R_BRACE
   ;

varDecl
   : bType varDef (COMMA varDef)* SEMICOLON
   ;

varDef
   : IDENT (L_BRACKT constExp R_BRACKT)* (ASSIGN initVal)?
   ;

initVal
   : exp
   | L_BRACE (initVal (COMMA initVal)*)? R_BRACE
   ;

funcDef
   : funcType IDENT L_PAREN (funcFParams)? R_PAREN block
   ;

funcType
   : VOID
   | INT
   ;

funcFParams
   : funcFParam (COMMA funcFParam)*
   ;

funcFParam
   : bType IDENT (L_BRACKT R_BRACKT (L_BRACKT exp R_BRACKT)*)?
   ;

block
   : L_BRACE (blockItem)* R_BRACE
   ;

blockItem
   : decl
   | stmt
   ;

stmt
   : lVal ASSIGN exp SEMICOLON                      # assignStmt
   | (exp)? SEMICOLON                               # questionStmt
   | block                                          # blockStmt
   | IF L_PAREN cond R_PAREN stmt (ELSE stmt)?      # ifStmt
   | WHILE L_PAREN cond R_PAREN stmt                # whileStmt
   | BREAK SEMICOLON                                # breakStmt
   | CONTINUE SEMICOLON                             # continueStmt
   | RETURN (exp)? SEMICOLON                        # returnStmt
   ;

exp
   : L_PAREN exp R_PAREN                            # parenExp
   | lVal                                           # lValExp
   | number                                         # numberExp
   | IDENT L_PAREN funcRParams? R_PAREN             # funcExp
   | unaryOp exp                                    # unaryExp
   | exp (MUL | DIV | MOD) exp                      # mulDivModExp
   | exp (PLUS | MINUS) exp                         # plusMinusExp
   ;

cond
   : exp                                            # expCond
   | cond (LT | GT | LE | GE) cond                  # compareCond
   | cond (EQ | NEQ) cond                           # equalCond
   | cond AND cond                                  # andCond
   | cond OR cond                                   # orCond
   ;

lVal
   : IDENT (L_BRACKT exp R_BRACKT)*
   ;

number
   : INTEGER_CONST
   ;

unaryOp
   : PLUS
   | MINUS
   | NOT
   ;

funcRParams
   : param (COMMA param)*
   ;

param
   : exp
   ;

constExp
   : exp
   ;
