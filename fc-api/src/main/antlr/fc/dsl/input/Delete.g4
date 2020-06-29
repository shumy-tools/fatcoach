grammar Delete;
@header { package fc.dsl.input; }

import Common;

delete: entity '@id=' id=INT ;

  entity: (ID '.')* NAME ;