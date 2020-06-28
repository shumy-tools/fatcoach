package fc.api.query

import fc.api.SEntity
import fc.api.spi.IAdaptor

class QueryBuilder(val sEntity: SEntity, private val adaptor: IAdaptor)