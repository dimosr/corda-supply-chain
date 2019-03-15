package com.supplychain.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.supplychain.demo.contracts.CargoContract
import net.corda.core.contracts.Command
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.flows.*
import net.corda.core.transactions.TransactionBuilder

/**
 * Flow triggered when a cargo is delivered in the final destination.
 */
@StartableByRPC
@InitiatingFlow
class ExitCargoFlow(val cargoID: UniqueIdentifier): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val stateAndRef = retrieveCargoState(cargoID, serviceHub.vaultService)
        val transactionState = stateAndRef.state
        val cargoState = transactionState.data

        val transactionBuilder = TransactionBuilder(transactionState.notary)
                .addInputState(stateAndRef)
                .addCommand(Command(CargoContract.Commands.Exit(), ourIdentity.owningKey))

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)

        val otherDistributors = cargoState.participatingDistributors
        val sessionWithOtherDistributors = otherDistributors
                .filterNot { it == ourIdentity }
                .map { initiateFlow(it) }
        subFlow(FinalityFlow(signedTransaction, sessionWithOtherDistributors))
    }

}

@InitiatedBy(ExitCargoFlow::class)
class ExitCargoResponderFlow(private val otherSide: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        subFlow(ReceiveFinalityFlow(otherSide))
    }

}