grammar Create;
@header { package fc.dsl.input; }

import Common;

create: entity data ;

  entity: (ID '.')* NAME ;

  data: '{' entry (',' entry)* '}' ;

    entry: ID ':' ( data | value ) ;

      value: TEXT | INT | FLOAT | BOOL | TIME | DATE | DATETIME | PARAM ;