grammar Update;
@header { package fc.dsl.input; }

import Common;

update: entity '@id' '==' id=longOrParam data EOF ;

  entity: (ID '.')* NAME ;

  longOrParam: (LONG | PARAM) ;

  data: '{' (entry (',' entry)*)? '}' ;

    entry: ID ':' (oper | data | list | value) ;

      oper: (add='@add' | del='@del') (list | value) ;

      list: '[' (item (',' item)*)? ']' ;

      item: value | data ;

      value: NULL | TEXT | LONG | DOUBLE | BOOL | TIME | DATE | DATETIME | PARAM ;
