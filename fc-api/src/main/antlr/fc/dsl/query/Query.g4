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

        list: '[' value (',' value)* ']';

        value: NULL | TEXT | LONG | DOUBLE | BOOL | TIME | DATE | DATETIME | PARAM ;

  limit: 'limit' longOrParam ;

  page: 'page' longOrParam ;

    longOrParam: (LONG | PARAM) ;

  select: '{' fields (',' relation)* '}' ;

    fields: ALL | (field (',' field)*) ;

      field: ('(' order LONG ')')? ID ;

        order: ('asc' | 'dsc') ;

    relation: ID qline ;