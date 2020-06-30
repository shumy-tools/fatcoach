grammar Delete;
@header { package fc.dsl.input; }

import Common;

delete: entity '@id' '=' id=longOrParam ;

  longOrParam: (LONG | PARAM) ;

  entity: (ID '.')* NAME ;