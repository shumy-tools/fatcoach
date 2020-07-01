grammar Create;
@header { package fc.dsl.input; }

import Common;

create: entity data EOF ;

  entity: (ID '.')* NAME ;

  data: '{' entry (',' entry)* '}' ;

    entry: ID ':' (data | list | value) ;

      list: '[' ((value (',' value)*) | (data (',' data)*)) ']' ;

      value: NULL | TEXT | LONG | DOUBLE | BOOL | TIME | DATE | DATETIME | PARAM ;
