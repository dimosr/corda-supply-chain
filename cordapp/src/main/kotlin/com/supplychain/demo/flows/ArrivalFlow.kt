package com.supplychain.demo.flows

import co.paralleluniverse.fibers.Suspendable
import com.supplychain.demo.contracts.CargoContract
import com.supplychain.demo.contracts.CargoState
import com.supplychain.demo.contracts.CargoStateSchemaV1
import net.corda.core.contracts.Command
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.contracts.requireThat
import net.corda.core.flows.*
import net.corda.core.node.services.Vault
import net.corda.core.node.services.VaultService
import net.corda.core.node.services.queryBy
import net.corda.core.node.services.vault.QueryCriteria
import net.corda.core.node.services.vault.builder
import net.corda.core.serialization.CordaSerializable
import net.corda.core.transactions.SignedTransaction
import net.corda.core.transactions.TransactionBuilder
import net.corda.core.utilities.unwrap

@CordaSerializable
enum class TransferRole {
    /* One of the distributors involved in the transfer */
    SIGNER,

    /* One of the distributors not involved in the transfer, but included in the scheduled trip */
    PARTICIPANT
}



/**
 * Flow triggered when a cargo arrives at a distributor.
 *
 * Triggers a [CargoContract.Commands.Transfer] between the currentDistributor and the next distributor in the scheduled trip.
 */
@StartableByRPC
@InitiatingFlow
class CargoArrivalReceiverFlow(private val cargoID: UniqueIdentifier): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val stateAndRef = retrieveCargoState(cargoID, serviceHub.vaultService)
        val transactionState = stateAndRef.state
        val cargoState = transactionState.data

        val transactionBuilder = TransactionBuilder(transactionState.notary)
                .addInputState(stateAndRef)
                .addOutputState(cargoState.copy(currentDistributor = ourIdentity))
                .addCommand(Command(CargoContract.Commands.Transfer(), listOf(ourIdentity.owningKey, cargoState.currentDistributor.owningKey)))

        val signedTransaction = serviceHub.signInitialTransaction(transactionBuilder, ourIdentity.owningKey)

        val otherDistributors = cargoState.participatingDistributors
        val sessionWithOtherDistributors = otherDistributors
                .filterNot { it == ourIdentity }
                .map { initiateFlow(it) }

        val sessionWithPreviousDistributor = sessionWithOtherDistributors.find { it.counterparty == cargoState.currentDistributor}!!
        sessionWithOtherDistributors.minus(sessionWithPreviousDistributor)
                .forEach { it.send(TransferRole.PARTICIPANT) }
        sessionWithPreviousDistributor.send(TransferRole.SIGNER)


        val txSignedByOtherSide = signedTransaction + subFlow(CollectSignatureFlow(signedTransaction, sessionWithPreviousDistributor, listOf(sessionWithPreviousDistributor.counterparty.owningKey)))

        subFlow(FinalityFlow(txSignedByOtherSide, sessionWithOtherDistributors))
    }

}

@InitiatedBy(CargoArrivalReceiverFlow::class)
class CargoArrivalResponderFlow(private val otherSide: FlowSession): FlowLogic<Unit>() {

    @Suspendable
    override fun call() {
        val signTransactionFlow = object: SignTransactionFlow(otherSide) {
            override fun checkTransaction(stx: SignedTransaction) {
                val command = stx.tx.commands.single()
                val inputCargoState = serviceHub.loadState(stx.inputs.single()).data as CargoState
                val outputCargoState = stx.coreTransaction.outputStates.single() as CargoState
                val currentDistributorIndex = inputCargoState.participatingDistributors.indexOf(inputCargoState.currentDistributor)
                val expectedNextDistributor = inputCargoState.participatingDistributors[currentDistributorIndex + 1]

                requireThat { "it's a transfer command" using (command.value is CargoContract.Commands.Transfer) }
                requireThat { "input and output have the same cargo ID" using (inputCargoState.cargoID == outputCargoState.cargoID) }
                requireThat { "new holding distributor is the next one" using (outputCargoState.currentDistributor == expectedNextDistributor) }
            }
        }
        val role = otherSide.receive(TransferRole::class.java).unwrap { it }

        when (role) {
            TransferRole.SIGNER -> {
                val txId = subFlow(signTransactionFlow).id
                subFlow(ReceiveFinalityFlow(otherSide, txId))
            }
            TransferRole.PARTICIPANT -> {
                subFlow(ReceiveFinalityFlow(otherSide))
            }
        }

    }

}

/**
 * Retrieves state with the provided ID from the local vault.
 */
fun retrieveCargoState(cargoID: UniqueIdentifier, vaultService: VaultService): StateAndRef<CargoState> {
    val generalCriteria = QueryCriteria.VaultQueryCriteria(Vault.StateStatus.ALL)
    val results = builder {
        val cargoIdCheck = CargoStateSchemaV1.PersistentCargoState::cargoID.equal(cargoID.toString())
        val criteria = generalCriteria.and(QueryCriteria.VaultCustomQueryCriteria(cargoIdCheck))
        vaultService.queryBy<CargoState>(criteria)
    }

    return results.states.single()
}