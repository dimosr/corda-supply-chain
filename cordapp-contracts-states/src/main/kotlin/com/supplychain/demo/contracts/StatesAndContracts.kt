package com.supplychain.demo.contracts

import net.corda.core.contracts.*
import net.corda.core.identity.AbstractParty
import net.corda.core.identity.Party
import net.corda.core.schemas.MappedSchema
import net.corda.core.schemas.PersistentState
import net.corda.core.schemas.QueryableState
import net.corda.core.transactions.LedgerTransaction
import java.lang.IllegalArgumentException


class CargoContract : Contract {
    companion object {
        const val ID = "com.supplychain.demo.contracts.CargoContract"
    }

    override fun verify(tx: LedgerTransaction) {
        tx.commands.requireSingleCommand<Commands>()

        val commandWithParties = tx.commands.first()

        when(commandWithParties.value) {
            Commands.Enter() -> {
                val signers = commandWithParties.signers
                val outputStates = tx.outputStates
                requireThat { "Enter contains only one output cargo" using (outputStates.size == 1) }
                requireThat { "Shipment contains more than one distributor" using (outputStates.first().participants.size > 1) }
                requireThat { "Enter contains no inputs" using (tx.inputStates.isEmpty()) }

                val outputState = outputStates.first() as CargoState
                requireThat { "the key of the holder distributor is in the signers" using (outputState.currentDistributor.owningKey in signers) }
            }
            Commands.Transfer() -> {
                val signers = commandWithParties.signers
                val outputStates = tx.outputStates
                val inputStates = tx.inputStates
                requireThat { "Transfer contains one input and one output state" using (outputStates.size == 1 && inputStates.size == 1) }

                val outputState = outputStates.first() as CargoState
                val inputState = inputStates.first() as CargoState
                requireThat { "Scheduled trip has not changed" using (inputState.participatingDistributors == outputState.participatingDistributors) }
                requireThat { "Current distributor included in the scheduled trip" using (outputState.currentDistributor in outputState.participatingDistributors) }
                requireThat { "Output and input state corresponds to the same cargo " using (inputState.cargoID == outputState.cargoID)}

                requireThat { "Both sender and receiver have signed the transfer" using (outputState.currentDistributor.owningKey in signers && inputState.currentDistributor.owningKey in signers) }

                //TODO / Nice-to-have: mechanisms to validate the sender has indeed dispatched the cargo & the receiver has indeed received it.
            }
            Commands.Exit() -> {
                val signers = commandWithParties.signers
                val inputStates = tx.inputStates
                requireThat { "Exit contains only one input cargo" using (inputStates.size == 1) }
                requireThat { "Exit contains no output cargo" using (tx.outputStates.isEmpty()) }

                val inputState = inputStates.first() as CargoState
                requireThat { "the key of the holder distributor is in the signers" using (inputState.currentDistributor.owningKey in signers) }
            }
            else -> throw IllegalArgumentException("Invalid command included in the transaction: ${commandWithParties.value}")
        }
    }

    // Used to indicate the transaction's intent.
    interface Commands : CommandData {
        /**
         * Used to enter a cargo in the chain, when an order has been placed for it.
         */
        class Enter : TypeOnlyCommandData(), Commands

        /**
         * Used to transfer a cargo from one distributor to the next one.
         */
        class Transfer: TypeOnlyCommandData(), Commands

        /**
         * Used to exit a cargo from the chain, when it has been delivered to the end customer.
         */
        class Exit: TypeOnlyCommandData(), Commands
    }
}

@BelongsToContract(CargoContract::class)
data class CargoState(val participatingDistributors: List<Party>, val cargoID: UniqueIdentifier, val currentDistributor: Party) : LinearState, QueryableState {
    override fun supportedSchemas(): Iterable<MappedSchema> = listOf(CargoStateSchemaV1)

    override fun generateMappedObject(schema: MappedSchema): PersistentState {
        return when(schema) {
            is CargoStateSchemaV1 -> {
                CargoStateSchemaV1.PersistentCargoState(cargoID.toString(), participatingDistributors, currentDistributor)
            }
            else -> throw IllegalArgumentException("Unsupported schema: $schema")
        }
    }

    override val linearId: UniqueIdentifier = cargoID
    override val participants: List<AbstractParty> = participatingDistributors

}
