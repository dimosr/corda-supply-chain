package com.supplychain.demo

import com.supplychain.demo.contracts.CargoState
import com.supplychain.demo.flows.CargoArrivalReceiverFlow
import com.supplychain.demo.flows.EnterCargoFlow
import com.supplychain.demo.flows.ExitCargoFlow
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.TestIdentity
import net.corda.testing.driver.*
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.Future

class DriverBasedTest {
    private val distributorA = TestIdentity(CordaX500Name("Distributor-A", "London", "GB"))
    private val distributorB = TestIdentity(CordaX500Name("Distributor-B", "New York", "US"))
    private val distributorC = TestIdentity(CordaX500Name("Distributor-C", "Zurich", "CH"))

    private val CARGO_ID = UniqueIdentifier()

    private val LEDGER_CONVERGENCE_PERIOD_MILLIS = 1000L

    @Test
    fun `a cargo can be transferred successfully from origin to destination`() = withDriver {
        val (distributorAHandle, distributorBHandle, distributorCHandle) = startNodes(distributorA, distributorB, distributorC)
        val allDistributorHandles = listOf(distributorAHandle, distributorBHandle, distributorCHandle)

        val participatingDistributors = listOf(distributorAHandle.resolveParty(distributorA.name), distributorAHandle.resolveParty(distributorB.name), distributorCHandle.resolveParty(distributorC.name))

        distributorAHandle.rpc.startFlow(::EnterCargoFlow, participatingDistributors, CARGO_ID, notaryHandles.first().identity).returnValue.get()
        verifyCargoInDistributor(distributorA.name, allDistributorHandles)

        distributorBHandle.rpc.startFlow(::CargoArrivalReceiverFlow, CARGO_ID).returnValue.get()
        verifyCargoInDistributor(distributorB.name, allDistributorHandles)

        distributorCHandle.rpc.startFlow(::CargoArrivalReceiverFlow, CARGO_ID).returnValue.get()
        Thread.sleep(LEDGER_CONVERGENCE_PERIOD_MILLIS)
        verifyCargoInDistributor(distributorC.name, allDistributorHandles)

        distributorCHandle.rpc.startFlow(::ExitCargoFlow, CARGO_ID).returnValue.get()
        Thread.sleep(LEDGER_CONVERGENCE_PERIOD_MILLIS)
        verifyCargoDelivered(allDistributorHandles)
    }

    // Runs a test inside the Driver DSL, which provides useful functions for starting nodes, etc.
    private fun withDriver(test: DriverDSL.() -> Unit) = driver(
        DriverParameters(isDebug = true, startNodesInProcess = true, notarySpecs = listOf(NotarySpec(name = DUMMY_NOTARY_NAME, validating = false)))
    ) { test() }

    // Makes an RPC call to retrieve another node's name from the network map.
    private fun NodeHandle.resolveParty(name: CordaX500Name) = rpc.wellKnownPartyFromX500Name(name)!!

    // Resolves a list of futures to a list of the promised values.
    private fun <T> List<Future<T>>.waitForAll(): List<T> = map { it.getOrThrow() }

    // Starts multiple nodes simultaneously, then waits for them all to be ready.
    private fun DriverDSL.startNodes(vararg identities: TestIdentity): List<NodeHandle> {
        val cordappContracts = TestCordapp.findCordapp("com.supplychain.demo.contracts")
        return identities
                .map { startNode(providedName = it.name, defaultParameters = NodeParameters().withAdditionalCordapps(setOf(cordappContracts))) }
                .waitForAll()
    }

    private fun verifyCargoInDistributor(distributorName: CordaX500Name, partiesHandles: List<NodeHandle>) {
        partiesHandles.forEach {
            val cargoState = it.rpc.vaultQuery(CargoState::class.java).states.map { it.state.data }
            assertThat(cargoState).hasSize(1)
            assertThat(cargoState.first().currentDistributor.name).isEqualTo(distributorName)
        }
    }

    private fun verifyCargoDelivered(partiesHandles: List<NodeHandle>) {
        partiesHandles.forEach {
            val cargoStates = it.rpc.vaultQuery(CargoState::class.java).states.map { it.state.data }
            assertThat(cargoStates).isEmpty()
        }
    }

}