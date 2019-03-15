package com.supplychain.demo

import com.supplychain.demo.contracts.CargoContract
import com.supplychain.demo.contracts.CargoState
import net.corda.core.contracts.TransactionVerificationException
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.testing.core.TestIdentity
import net.corda.testing.node.MockServices
import net.corda.testing.node.ledger
import org.junit.Test
import java.lang.IllegalStateException

class ContractTests {
    private val ledgerServices = MockServices()

    private val distributorA = TestIdentity(CordaX500Name("Distributor-A", "New York", "US"))
    private val distributorB = TestIdentity(CordaX500Name("Distributor-B", "Zurich", "CH"))

    @Test(expected = IllegalStateException::class)
    fun `enter command without any cargo fails`() {
        ledgerServices.ledger {
            transaction {
                command(distributorA.publicKey, CargoContract.Commands.Enter())
                verifies()
            }
        }
    }

    @Test(expected = TransactionVerificationException::class)
    fun `enter command with input cargo fails`() {
        ledgerServices.ledger {
            transaction {
                input(CargoContract.ID, CargoState(listOf(distributorA.party, distributorB.party), UniqueIdentifier(), distributorA.party))
                command(distributorA.publicKey, CargoContract.Commands.Enter())
                verifies()
            }
        }
    }

    @Test(expected = TransactionVerificationException::class)
    fun `enter command not signed by first distributor fails`() {
        ledgerServices.ledger {
            transaction {
                input(CargoContract.ID, CargoState(listOf(distributorA.party, distributorB.party), UniqueIdentifier(), distributorA.party))
                command(distributorB.publicKey, CargoContract.Commands.Enter())
                verifies()
            }
        }
    }

    @Test
    fun `valid enter command is verified successfully`() {
        ledgerServices.ledger {
            transaction {
                output(CargoContract.ID, CargoState(listOf(distributorA.party, distributorB.party), UniqueIdentifier(), distributorA.party))
                command(distributorA.publicKey, CargoContract.Commands.Enter())
                verifies()
            }
        }
    }
}