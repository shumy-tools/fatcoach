grammar Update;
@header { package fc.dsl.input; }

import Common;

update: entity '@id=' id=INT data ;

  entity: (ID '.')* NAME ;

  data: '{' entry (',' entry)* '}' ;

    entry: ID ':' ( oper | value ) ;

      oper: (add='@add' | del='@del') INT ;

      value: TEXT | INT | FLOAT | BOOL | TIME | DATE | DATETIME | PARAM ;
