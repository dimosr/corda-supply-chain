package com.supplychain.demo.flows

import net.corda.core.schemas.MappedSchema
import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Table

object FlowLockSchema

class FlowLockSchemaV1: MappedSchema(schemaFamily = FlowLockSchema.javaClass, version = 1, mappedTypes = listOf(FlowLock::class.java)) {
    @Entity
    @Table(name = "flow_lock")
    class FlowLock(
            @Id
            @Column(name = "lock_id")
            var lockId: String
    )
}