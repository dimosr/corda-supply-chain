package com.supplychain.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.supplychain.demo.contracts.CargoContract
import com.supplychain.demo.contracts.CargoState
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.identity.Party
import net.corda.core.transactions.TransactionBuilder
import java.lang.IllegalArgumentException

/**
 * Flow triggered when a cargo is scheduled for transport.
 */
@StartableByRPC
@InitiatingFlow
class EnterCargoFlow(val tripDistributors: List<Party>, val cargoID: UniqueIdentifier, val notary: Party) : FlowLogic<UniqueIdentifier>() {

    constructor(tripDistributors: List<Party>, notary: Party): this(tripDistributors, UniqueIdentifier(), notary)

    @Suspendable
    override fun call(): UniqueIdentifier {
        assert(tripDistributors.contains(ourIdentity)) {IllegalArgumentException("Our identity should be included in the shipment distributors.")}
        val initialDistributor = ourIdentity

        val cargoState = CargoState(tripDistributors, cargoID, initialDistributor)
        val transactionBuilder = TransactionBuilder(notary)
                .addOutputState(cargoState, CargoContract.ID)
                .addCommand(Command(CargoContract.Commands.Enter(), ourIdentity.owningKey))


        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)

        val sessionWithOtherDistributors = tripDistributors
                .filterNot { it.name == ourIdentity.name }
                .map { initiateFlow(it) }

        subFlow(FinalityFlow(signedTransaction, sessionWithOtherDistributors))

        return cargoID
    }

}

@InitiatedBy(EnterCargoFlow::class)
class EnterCargoResponderFlow(private val otherSide: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSide))
    }

}