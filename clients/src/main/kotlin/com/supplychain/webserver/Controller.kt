package com.supplychain.webserver

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.supplychain.demo.contracts.CargoState
import com.supplychain.demo.flows.CargoArrivalReceiverFlow
import com.supplychain.demo.flows.EnterCargoFlow
import com.supplychain.demo.flows.ExitCargoFlow
import net.corda.client.jackson.JacksonSupport
import net.corda.core.contracts.UniqueIdentifier
import net.corda.core.identity.CordaX500Name
import net.corda.core.messaging.startFlow
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*

/**
 * Define your API endpoints here.
 */
@RestController
@RequestMapping("/") // The paths for HTTP requests are relative to this base path.
class Controller(rpc: NodeRPCConnection) {

    data class CreateScheduleRequest(val distributorsName: List<String>)
    data class CreateScheduleResponse(val cargoID: String)

    companion object {
        private val logger = LoggerFactory.getLogger(RestController::class.java)
    }

    private val proxy = rpc.proxy

    @GetMapping(value = "/node", produces = arrayOf("application/json"))
    private fun nodeInfo(): String {
        val objectMapper = JacksonSupport.createDefaultMapper(proxy)

        val nodeInfo = proxy.nodeInfo()
        return objectMapper.writeValueAsString(nodeInfo)
    }

    /**
     * Note:
     * This API is used to identify all the parties we can transact with.
     * For simplicity and demonstration purposes, it makes use of the network map to discover them.
     * However, this approach might not be the right one in realistic scenarios, because the map might be very large or contain irrelevant parties.
     */
    @GetMapping(value = "/parties", produces = arrayOf("application/json"))
    private fun otherParties(): String {
        val objectMapper = JacksonSupport.createDefaultMapper(proxy)
        val nodeInfo = proxy.nodeInfo()

        val partiesName = proxy.networkMapSnapshot()
                .map { it.legalIdentities.first() }
                .filter { it.name.toString().contains("Distributor") && it.name != nodeInfo.legalIdentities.first().name }
        return objectMapper.writeValueAsString(partiesName)
    }

    @PostMapping(value = "/schedule/create", consumes = arrayOf("application/json"), produces = arrayOf("application/json"))
    private fun createSchedule(@RequestBody createScheduleRequest: CreateScheduleRequest): String {
        val selectedNotary = proxy.notaryIdentities().first()
        val objectMapper = ObjectMapper().registerModule(KotlinModule())

        val scheduleDistributors = createScheduleRequest.distributorsName.mapNotNull { proxy.wellKnownPartyFromX500Name(CordaX500Name.parse(it)) }
        val flowHandle = proxy.startFlow(::EnterCargoFlow, scheduleDistributors, selectedNotary)
        val cargoID = flowHandle.returnValue.toCompletableFuture().get()

        val createScheduleResponse = CreateScheduleResponse(cargoID.id.toString())
        return objectMapper.writeValueAsString(createScheduleResponse)
    }

    @GetMapping(value = "/cargo", produces = arrayOf("application/json"))
    private fun allCargo(): String {
        val objectMapper = JacksonSupport.createDefaultMapper(proxy)

        val allCargoStates = proxy.vaultQuery(CargoState::class.java)
                .states.map { it.state.data }
        return objectMapper.writeValueAsString(allCargoStates)
    }

    @PostMapping(value = "/cargo/{cargoID}/arrived")
    @ResponseStatus(value = HttpStatus.OK)
    private fun triggerCargoArrival(@PathVariable(value="cargoID") cargoID: String) {
        proxy.startFlow(::CargoArrivalReceiverFlow, UniqueIdentifier.fromString(cargoID))
    }

    @PostMapping(value = "/cargo/{cargoID}/deliver")
    @ResponseStatus(value = HttpStatus.OK)
    private fun triggerCargoDelivery(@PathVariable(value="cargoID") cargoID: String) {
        proxy.startFlow(::ExitCargoFlow, UniqueIdentifier.fromString(cargoID))
    }
}