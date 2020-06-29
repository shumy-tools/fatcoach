lexer grammar Common;

ALL: '*' ;
NAME: [A-Z][A-Za-z0-9_]* ;
ID: [@]?[a-z][A-Za-z0-9_]* ;

// value types
  INT: '-'? [0-9]+ ;
  FLOAT: INT ('.' [0-9]+)? ;
  TEXT: '"' ( '""' | ~["\r\n] )* '"';
  BOOL: 'true' | 'false' ;
  TIME: '#' [0-2][0-9]':'[0-9][0-9]':'[0-9][0-9] ;
  DATE: '#' [0-9][0-9][0-9][0-9]'-'[0-9][0-9]'-'[0-9][0-9] ;
  DATETIME: DATE'T'[0-2][0-9]':'[0-9][0-9]':'[0-9][0-9] ;
  PARAM: '?' ID;
WS: [ \t\r\n\f]+ -> skip ;