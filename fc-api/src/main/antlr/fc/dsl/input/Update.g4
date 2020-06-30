grammar Update;
@header { package fc.dsl.input; }

import Common;

update: entity '@id' '=' id=longOrParam data ;

  entity: (ID '.')* NAME ;

  longOrParam: (LONG | PARAM) ;

  data: '{' entry (',' entry)* '}' ;

    entry: ID ':' ( data | oper | list | value ) ;

      oper: (add='@add' | del='@del') value ;

      list: '[' ((value (',' value)*) | (oper (',' oper)*) | (data (',' data)*)) ']' ;

      value: NULL | TEXT | LONG | DOUBLE | BOOL | TIME | DATE | DATETIME | PARAM ;
