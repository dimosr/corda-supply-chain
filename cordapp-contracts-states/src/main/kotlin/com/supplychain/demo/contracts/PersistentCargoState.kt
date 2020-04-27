package com.supplychain.demo.contracts

import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import javax.persistence.Column
import javax.persistence.ElementCollection
import javax.persistence.Entity
import javax.persistence.Index
import javax.persistence.Table

object CargoStateSchema

object CargoStateSchemaV1: MappedSchema(schemaFamily = CargoStateSchema.javaClass, version = 1, mappedTypes = listOf(PersistentCargoState::class.java)) {

    @Entity
    @Table(name = "cargo_states", indexes = arrayOf(Index(name = "offset_index", columnList = "timestamp, transaction_id, output_index")))
    class PersistentCargoState(
            @Column(name = "id", nullable = false)
            var cargoID: String,

            @Column(name = "participating_distributors", nullable = false)
            @ElementCollection
            var participatingDistributors: List<Party>,

            @Column(name = "holding_distributor", nullable = false)
            var holdingDistributor: Party?,

            @Column
            var timestamp: Long
    ): PersistentState() {

        // no-arg constructor required by hibernate
        constructor(): this("", emptyList(), null, 0)
    }
}