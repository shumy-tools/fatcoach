grammar Query;
@header { package fc.dsl.query; }

import Common;

query: entity qline EOF ;

  entity: (ID '.')* NAME ;

qline: filter? (limit page?)? select ;

  filter: '|' expr '|' ;

    expr: '(' expr ')'
      | left=expr oper='and' right=expr
      | left=expr oper='or' right=expr
      | predicate
    ;

    predicate: path comp param ;

      path: ID ('.' ID)* ;

      comp: ('==' | '!=' | '>' | '<' | '>=' | '<=' | 'in') ;

      param: value | list ;

        value: TEXT | INT | FLOAT | BOOL | TIME | DATE | DATETIME | PARAM ;

        list: '[' value (',' value)* ']';

  limit: 'limit' intOrParam ;

  page: 'page' intOrParam ;

    intOrParam: (INT | PARAM) ;

  select: '{' fields (',' relation)* '}' ;

    fields: ALL | (field (',' field)*) ;

      field: ('(' order INT ')')? ID ;

        order: ('asc' | 'dsc') ;

    relation: ID qline ;