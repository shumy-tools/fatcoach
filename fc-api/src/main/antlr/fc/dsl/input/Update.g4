grammar Update;
@header { package fc.dsl.input; }

import Common;

update: entity '@id' '=' id=longOrParam data ;

  entity: (ID '.')* NAME ;

  longOrParam: (LONG | PARAM) ;

  data: '{' entry (',' entry)* '}' ;

    entry: ID ':' ( oper | value ) ;

      oper: (add='@add' | del='@del') longOrParam ;

      list: '[' oper (',' oper)* ']' ;

      value: NULL | TEXT | LONG | DOUBLE | BOOL | TIME | DATE | DATETIME | PARAM ;
