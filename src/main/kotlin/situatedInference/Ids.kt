package situatedInference

import kotlin.properties.Delegates

internal var explainId by Delegates.notNull<Long>()
internal var situateId by Delegates.notNull<Long>()
internal var sharedId by Delegates.notNull<Long>()
internal var situateInsideId by Delegates.notNull<Long>()
internal var situatedContextPrefixId by Delegates.notNull<Long>()
internal var sharedKnowledgeContextId by Delegates.notNull<Long>()
internal var situatedContextId by Delegates.notNull<Long>()
internal var situateSchemaId by Delegates.notNull<Long>()
internal var appendToContextsId by Delegates.notNull<Long>()
internal var hasSituatedContextId by Delegates.notNull<Long>()
internal var prefixToSituateId by Delegates.notNull<Long>()
